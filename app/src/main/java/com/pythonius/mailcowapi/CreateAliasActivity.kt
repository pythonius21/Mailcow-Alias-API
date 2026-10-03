package com.pythonius.mailcowapi

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.random.Random
import android.content.ClipData
import android.content.ClipboardManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CreateAliasActivity  : AppCompatActivity() {

    private lateinit var addressInput: EditText
    private lateinit var gotoInput: EditText
    private lateinit var privcomment: EditText
    private lateinit var pubcomment: EditText
    private lateinit var createButton: Button
    private lateinit var reloadAliasButton: ImageButton
    private lateinit var domainSpinner: AutoCompleteTextView

    private lateinit var baseUrl: String
    private lateinit var apiKey: String
    private lateinit var aliasDomain: String
    private val prefixWords = mutableListOf<String>()
    private val domainList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }

        setContentView(R.layout.activity_create_alias)

        addressInput = findViewById(R.id.addressInput)
        gotoInput = findViewById(R.id.gotoInput)
        privcomment = findViewById(R.id.privcomment)
        pubcomment = findViewById(R.id.pubcomment)
        createButton = findViewById(R.id.createAliasButton)
        reloadAliasButton = findViewById(R.id.reloadAliasButton)
        domainSpinner = findViewById(R.id.domainSpinner)

        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            this,
            "mailcow_prefs_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        baseUrl = prefs.getString("server_url", "") ?: ""
        apiKey = prefs.getString("api_key", "") ?: ""

        loadWordsFromAssets()
        loadDomains()

        reloadAliasButton.setOnClickListener {
            generateRandomAlias()
        }

        createButton.setOnClickListener {
            val address = addressInput.text.toString().trim()
            val goto = gotoInput.text.toString().trim()

            if (address.isEmpty() || goto.isEmpty()) {
                Toast.makeText(this, "Please fill out all values!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            createAlias("$address@$aliasDomain", goto)
        }
    }

    private fun loadWordsFromAssets() {
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(InputStreamReader(assets.open("words.txt")))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.trim()?.let { if (it.isNotEmpty()) prefixWords.add(it) }
            }
        } catch (e: IOException) {
            // Handle error, e.g., log it or use a default small list
            Toast.makeText(this, "Could not load word list", Toast.LENGTH_SHORT).show()
        } finally {
            reader?.close()
        }
    }

    private fun loadDomains() {
        val apiUrl = "https://$baseUrl/api/v1/get/alias-domain/all"
        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("X-Api-Key", apiKey)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@CreateAliasActivity, "Failed to load alias domains: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && !body.isNullOrEmpty()) {
                        try {
                            val domainArray = JSONArray(body)
                            domainList.clear()
                            for (i in 0 until domainArray.length()) {
                                val obj = domainArray.getJSONObject(i)
                                if (obj.optInt("active", 0) == 1) {
                                    val domainName = obj.optString("alias_domain", obj.optString("domain", obj.optString("domain_name", "")))
                                    if (domainName.isNotEmpty()) {
                                        domainList.add(domainName)
                                    }
                                }
                            }
                            if (domainList.isNotEmpty()) {
                                val adapter = ArrayAdapter(this@CreateAliasActivity, android.R.layout.simple_dropdown_item_1line, domainList)
                                domainSpinner.setAdapter(adapter)
                                val storedDomain = getSharedPreferences("mailcow_prefs_secure", Context.MODE_PRIVATE).getString("last_domain", null)
                                val defaultDomain = storedDomain ?: domainList[0]
                                aliasDomain = defaultDomain
                                domainSpinner.setText(defaultDomain, false)
                                domainSpinner.setOnItemClickListener { _, _, position, _ ->
                                    aliasDomain = domainList[position]
                                    getSharedPreferences("mailcow_prefs_secure", Context.MODE_PRIVATE).edit().putString("last_domain", aliasDomain).apply()
                                }
                            } else {
                                Toast.makeText(this@CreateAliasActivity, "No active alias domains found", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@CreateAliasActivity, "Failed to parse alias domains", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@CreateAliasActivity, "Failed to load alias domains (${response.code})", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun generateRandomAlias() {
        if (domainList.isEmpty()) {
            Toast.makeText(this, "No domains available", Toast.LENGTH_SHORT).show()
            return
        }
        if (prefixWords.isEmpty()) {
            val randomPrefix = "random${Random.nextInt(1000, 9999)}"
            addressInput.setText("$randomPrefix@$aliasDomain")
            Toast.makeText(this, "Word list empty, using default.", Toast.LENGTH_SHORT).show()
            return
        }

        val randomWord = prefixWords.random()
        val randomNumber = Random.nextInt(100, 9999)
        val newAliasPrefix = "$randomWord$randomNumber"
        val newAlias = "$newAliasPrefix@$aliasDomain"
        addressInput.setText(newAlias)
    }

    private fun createAlias(addressToCopy: String, goto: String) {
        val apiUrl = "https://$baseUrl/api/v1/add/alias"

        val json = JSONObject().apply {
            put("active", "1")
            put("address", addressToCopy)
            put("goto", goto)
            put("private_comment", privcomment.text.toString())
            put("public_comment", pubcomment.text.toString())
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, json.toString())

        val request = Request.Builder()
            .url(apiUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Api-Key", apiKey)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@CreateAliasActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Copied Alias", addressToCopy)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@CreateAliasActivity, "Alias '$addressToCopy' created and copied to clipboard!", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        Toast.makeText(this@CreateAliasActivity, "Error (${response.code}): $responseBody", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}