package com.pythonius.mailcowapi

import android.content.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var loadButton: Button
    private var noInternetBanner: TextView? = null
    private lateinit var connectivityReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
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

        val prefs = getSharedPreferences("mailcow_prefs", Context.MODE_PRIVATE)
        urlInput.setText(prefs.getString("server_url", ""))
        apiKeyInput.setText(prefs.getString("api_key", ""))

        loadButton.setOnClickListener {
            val baseUrl = urlInput.text.toString().trim()
            val apiKey = apiKeyInput.text.toString().trim()

            if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                Toast.makeText(this, "Enter Server-URL and API-Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("server_url", baseUrl)
                .putString("api_key", apiKey)
                .apply()

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
                        if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                            val intent = Intent(this@MainActivity, AliasListActivity::class.java)
                            intent.putExtra("alias_json", responseBody)
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@MainActivity, "Failed to load aliases", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
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
            topMargin = getStatusBarHeight()  // <-- Abstand zur Statusbar
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