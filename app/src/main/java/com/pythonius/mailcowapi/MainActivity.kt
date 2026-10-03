package com.pythonius.mailcowapi

import android.content.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var loadButton: Button
    private var noInternetBanner: TextView? = null
    private lateinit var connectivityReceiver: BroadcastReceiver
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupNoInternetBanner()
        checkAndShowBanner()

        connectivityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                checkAndShowBanner()
            }
        }

        registerReceiver(connectivityReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        urlInput = findViewById(R.id.serverUrlInput)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        loadButton = findViewById(R.id.loadAliasesButton)

        val prefs = getEncryptedPrefs()
        urlInput.setText(prefs.getString("server_url", ""))
        apiKeyInput.setText(prefs.getString("api_key", ""))

        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@MainActivity, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    loadAliases()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })

        loadButton.setOnClickListener {
            val baseUrl = urlInput.text.toString().trim()
            val apiKey = apiKeyInput.text.toString().trim()

            if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                Toast.makeText(this, "Enter Server-URL and API-Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            getEncryptedPrefs().edit()
                .putString("server_url", baseUrl)
                .putString("api_key", apiKey)
                .apply()

            showBiometricPrompt()
        }
    }

    private fun isBiometricEnabled(): Boolean {
        val prefs = getSharedPreferences("mailcow_security", Context.MODE_PRIVATE)
        return prefs.getBoolean("biometric_enabled", true)
    }

    private fun showBiometricPrompt() {
        if (!isBiometricEnabled()) {
            loadAliases()
            return
        }
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Biometric Authentication")
                    .setSubtitle("Verify your identity to access Mailcow")
                    .setNegativeButtonText("Cancel")
                    .build()
                biometricPrompt.authenticate(promptInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Toast.makeText(this, "No biometric hardware available", Toast.LENGTH_SHORT).show()
                loadAliases()
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Toast.makeText(this, "Biometric hardware unavailable", Toast.LENGTH_SHORT).show()
                loadAliases()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Toast.makeText(this, "No biometric credentials enrolled", Toast.LENGTH_SHORT).show()
                loadAliases()
            }
            else -> {
                loadAliases()
            }
        }
    }

    private fun loadAliases() {
        val prefs = getEncryptedPrefs()
        val baseUrl = prefs.getString("server_url", "") ?: ""
        val apiKey = prefs.getString("api_key", "") ?: ""

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "Please configure server URL and API key first", Toast.LENGTH_SHORT).show()
            return
        }

        val apiUrl = "https://$baseUrl/api/v1/get/alias/all"
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("X-Api-Key", apiKey)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        val intent = Intent(this@MainActivity, AliasListActivity::class.java)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to connect to server (HTTP ${response.code})", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("mailcow_theme", Context.MODE_PRIVATE)
        val theme = prefs.getString("theme", "dark")
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun getEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            this,
            "mailcow_prefs_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(connectivityReceiver)
    }

    private fun setupNoInternetBanner() {
        val banner = TextView(this).apply {
            text = "NO INTERNET CONNECTION"
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.neon_magenta))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.background_dark))
            setPadding(16, 16, 16, 16)
            gravity = android.view.Gravity.CENTER
            visibility = android.view.View.GONE
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.1f
        }

        noInternetBanner = banner

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP
            topMargin = getStatusBarHeight()
        }

        addContentView(banner, layoutParams)
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun checkAndShowBanner() {
        if (isNetworkAvailable()) {
            hideNoInternetBanner()
        } else {
            showNoInternetBanner()
        }
    }

    private fun showNoInternetBanner() {
        noInternetBanner?.visibility = android.view.View.VISIBLE
    }

    private fun hideNoInternetBanner() {
        noInternetBanner?.visibility = android.view.View.GONE
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo = cm.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }
}