package com.pythonius.mailcowapi

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.random.Random
import android.content.ClipData
import android.content.ClipboardManager

class CreateAliasActivity  : AppCompatActivity() {

    private lateinit var addressInput: EditText
    private lateinit var gotoInput: EditText
    private lateinit var privcomment: EditText
    private lateinit var pubcomment: EditText
    private lateinit var createButton: Button
    private lateinit var reloadAliasButton: ImageButton // Add this line

    private lateinit var baseUrl: String
    private lateinit var apiKey: String
    private lateinit var aliasDomain: String // Added
    private val prefixWords = mutableListOf<String>() // Now mutable, loaded from assets

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
        reloadAliasButton = findViewById(R.id.reloadAliasButton) // Add this line

        // Lese baseUrl und API Key aus SharedPreferences
        val prefs = getSharedPreferences("mailcow_prefs", Context.MODE_PRIVATE)
        baseUrl = prefs.getString("server_url", "") ?: ""
        apiKey = prefs.getString("api_key", "") ?: ""
        // Inside onCreate, after getting baseUrl and apiKey
        aliasDomain = prefs.getString("default_domain", "fermium.cc") ?: "fermium.cc" // Example: get from prefs or use a default

        loadWordsFromAssets() // Call this to load your words

        // Inside onCreate, after initializing views
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
            // Handle error, e.g., log it or use a default small list
            Toast.makeText(this, "Could not load word list", Toast.LENGTH_SHORT).show()
        } finally {
            reader?.close()
        }
    }

    private fun generateRandomAlias() {
        if (prefixWords.isEmpty()) {
            // Fallback if the list is empty or couldn't be loaded
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