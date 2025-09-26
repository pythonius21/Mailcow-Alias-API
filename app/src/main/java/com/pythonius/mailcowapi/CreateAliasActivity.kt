package com.pythonius.mailcowapi

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.random.Random
import android.content.ClipData
import android.content.ClipboardManager
import org.json.JSONArray
import org.json.JSONException

class CreateAliasActivity  : AppCompatActivity() {

    private lateinit var addressInput: EditText
    private lateinit var gotoInput: EditText
    private lateinit var privcomment: EditText
    private lateinit var pubcomment: EditText
    private lateinit var createButton: Button
    private lateinit var reloadAliasButton: ImageButton

    private lateinit var baseUrl: String
    private lateinit var apiKey: String
    private lateinit var aliasDomain: String
    private val prefixWords = mutableListOf<String>()
    private val availableAliasDomains = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_alias)

        addressInput = findViewById(R.id.addressInput)
        gotoInput = findViewById(R.id.gotoInput)
        privcomment = findViewById(R.id.privcomment)
        pubcomment = findViewById(R.id.pubcomment)
        createButton = findViewById(R.id.createAliasButton)
        reloadAliasButton = findViewById(R.id.reloadAliasButton)

        val prefs = getSharedPreferences("mailcow_prefs", Context.MODE_PRIVATE)
        baseUrl = prefs.getString("server_url", "") ?: ""
        apiKey = prefs.getString("api_key", "") ?: ""
        aliasDomain = prefs.getString("default_domain", "example.com") ?: "example.com"

        loadWordsFromAssets()

        fetchAliasDomainsFromMailcow { success ->
            if (success && availableAliasDomains.isNotEmpty()) {
                aliasDomain = availableAliasDomains[0]
                val preferredDomain = prefs.getString("default_domain", "") ?: ""
                if (preferredDomain.isNotEmpty() && availableAliasDomains.contains(preferredDomain)) {
                    aliasDomain = preferredDomain
                }
                Toast.makeText(this, "Default-Alias-Domain set to '${aliasDomain}'.", Toast.LENGTH_SHORT).show()
            } else if (success && availableAliasDomains.isEmpty()) {
                Toast.makeText(this, "No Alias-Domain found in API: '$aliasDomain'", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Using Fallback-Domain: '$aliasDomain'", Toast.LENGTH_LONG).show()
            }
            if (addressInput.text.isEmpty()) {
                generateRandomAlias()
            }
        }

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
            if (!address.contains("@")) {
                Toast.makeText(this, "The Alias has to include '@'.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            createAlias(address, goto)
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
            Toast.makeText(this, "Could not load word list", Toast.LENGTH_SHORT).show()
        } finally {
            reader?.close()
            try {
                reader?.close()
            } catch (e: IOException) {
            }
        }
    }

    private fun generateRandomAlias() {
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

    private fun fetchAliasDomainsFromMailcow(callback: (success: Boolean) -> Unit) {
    if (baseUrl.isEmpty() || apiKey.isEmpty()) {
        runOnUiThread {
            Toast.makeText(this, "Base URL or API Key not set.", Toast.LENGTH_SHORT)
                .show()
            callback(false)
        }
        return
    }

    val apiUrl = "https://$baseUrl/api/v1/get/alias/all"
    val request = Request.Builder()
        .url(apiUrl)
        .get()
        .addHeader("X-Api-Key", apiKey)
        .addHeader("accept", "application/json")
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            runOnUiThread {
                Toast.makeText(
                    this@CreateAliasActivity,
                    "Error on fetching Alias-Domains: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                callback(false)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                try {
                    val tempDomains = mutableSetOf<String>()
                    val jsonArray = JSONArray(responseBody)

                    for (i in 0 until jsonArray.length()) {
                        val aliasObject = jsonArray.getJSONObject(i)
                        if (aliasObject.has("address")) {
                            val fullAddress = aliasObject.getString("address")
                            if (fullAddress.contains("@")) {
                                val domain = fullAddress.substringAfterLast("@", "")
                                if (domain.isNotEmpty()) {
                                    tempDomains.add(domain)
                                }
                            }
                        }
                    }
                    runOnUiThread {
                        availableAliasDomains.clear()
                        availableAliasDomains.addAll(tempDomains.sorted())
                        callback(true)
                    }
                } catch (e: JSONException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@CreateAliasActivity,
                            "Error on parsing Alias-Domains: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        callback(false)
                    }
                }
            } else {
                runOnUiThread {
                    val errorBody = responseBody ?: "No Error Body"
                    Toast.makeText(
                        this@CreateAliasActivity,
                        "API Error (${response.code}): $errorBody",
                        Toast.LENGTH_LONG
                    ).show()
                    callback(false)
                }
            }
        }
    })
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