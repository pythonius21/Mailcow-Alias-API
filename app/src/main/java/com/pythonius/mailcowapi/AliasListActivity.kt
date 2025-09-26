package com.pythonius.mailcowapi

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.pythonius.mailcowapi.R.layout.dialog_alias_details
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class AliasListActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var baseUrl: String
    private lateinit var apiKey: String
    private lateinit var aliasContainer: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alias_list)

        sharedPreferences = getSharedPreferences("mailcow_prefs", Context.MODE_PRIVATE)
        baseUrl = sharedPreferences.getString("server_url", "") ?: ""
        apiKey = sharedPreferences.getString("api_key", "") ?: ""

        aliasContainer = findViewById(R.id.aliasContainer)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "Please make sure the server URL and API key are filled", Toast.LENGTH_LONG).show()
            return
        }

        swipeRefresh.setOnRefreshListener {
            loadAliases()
        }

        loadAliases()

        findViewById<FloatingActionButton>(R.id.fabAddAlias).setOnClickListener {
            startActivity(Intent(this, CreateAliasActivity::class.java))
        }
    }

    private fun loadAliases() {
        swipeRefresh.isRefreshing = true
        val apiUrl = "https://$baseUrl/api/v1/get/alias/all"
        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("X-Api-Key", apiKey)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(this@AliasListActivity, "Failed to load aliases: ${e.message}", Toast.LENGTH_SHORT).show()
                    displayAliases("[]")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    if (response.isSuccessful && !body.isNullOrEmpty()) {
                        try {
                            val originalArray = JSONArray(body)
                            val aliasObjectList = mutableListOf<JSONObject>()
                            for (i in 0 until originalArray.length()) {
                                aliasObjectList.add(originalArray.getJSONObject(i))
                            }
                            aliasObjectList.sortByDescending { it.optLong("id", 0L) }

                            val sortedJsonArray = JSONArray(aliasObjectList)
                            displayAliases(sortedJsonArray.toString())

                        } catch (e: Exception) {
                            Toast.makeText(this@AliasListActivity, "Error processing alias data: ${e.message}", Toast.LENGTH_LONG).show()
                            displayAliases("[]")
                        }
                    } else {
                        Toast.makeText(this@AliasListActivity, "Error loading aliases: ${response.message} (${response.code})", Toast.LENGTH_SHORT).show()
                        displayAliases("[]")
                    }
                }
            }
        })
    }

    private fun displayAliases(jsonString: String) {
        aliasContainer.removeAllViews()

        try {
            val aliasArray = JSONArray(jsonString)

            if (aliasArray.length() == 0) {
                val emptyTextView = TextView(this).apply {
                    text = "No aliases found.\nPull down to refresh or tap '+' to add."
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(this@AliasListActivity, R.color.empty_list_text_color))
                    setTextAppearance(android.R.style.TextAppearance_Medium)

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    val marginInDp = 32
                    val marginInPx = (marginInDp * resources.displayMetrics.density).toInt()
                    params.setMargins(marginInPx, marginInPx, marginInPx, marginInPx)
                    layoutParams = params
                }
                aliasContainer.addView(emptyTextView)
                return
            }

            for (i in 0 until aliasArray.length()) {
                val aliasObj = aliasArray.getJSONObject(i)
                val address = aliasObj.optString("address", "[No Address]")
                val goto = aliasObj.optString("goto", "[No Goto]")
                val domain = aliasObj.optString("domain", "")
                val created = aliasObj.optString("created", "")
                val active = aliasObj.optString("active", "0").toIntOrNull() ?: 0
                val privateComment = aliasObj.optString("private_comment", "")
                val publicComment = aliasObj.optString("public_comment", "")

                val aliasView = TextView(this).apply {
                    val displayText = StringBuilder()
                    displayText.append(address)
                    if (goto.isNotEmpty() && goto != address) {
                        displayText.append("\n  \u279C ${goto.take(30)}${if (goto.length > 30) "..." else ""}")
                    }
                    if (active == 0) {
                        displayText.append("\n(Inactive)")
                    }
                    text = displayText.toString()

                    setTextIsSelectable(true)
                    isFocusable = true
                    isFocusableInTouchMode = true

                    setBackgroundResource(R.drawable.rounded_alias_background)
                    setTextColor(ContextCompat.getColor(this@AliasListActivity, R.color.alias_item_text_light))

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    val marginInDp = 8
                    val marginVerticalInPx = (marginInDp * resources.displayMetrics.density).toInt()
                    val marginHorizontalInPx = (16 * resources.displayMetrics.density).toInt()
                    params.setMargins(marginHorizontalInPx, marginVerticalInPx, marginHorizontalInPx, marginVerticalInPx)
                    layoutParams = params

                    val paddingInDp = 16
                    val paddingInPx = (paddingInDp * resources.displayMetrics.density).toInt()
                    setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)

                    isClickable = true
                    isFocusable = true

                    setOnClickListener {
                        showAliasDetailsDialog(address, goto, domain, created, active, privateComment, publicComment)
                    }
                }
                aliasContainer.addView(aliasView)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error parsing data: ${e.message}", Toast.LENGTH_LONG).show()
            aliasContainer.removeAllViews()
            val errorTextView = TextView(this).apply { text = "Error displaying aliases."; gravity = Gravity.CENTER }
            aliasContainer.addView(errorTextView)
        }
    }

    private fun showAliasDetailsDialog(address: String, goto: String, domain: String, created: String, active: Int, privateComment: String, publicComment: String) {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(dialog_alias_details, null)

        val tvActive: TextView = dialogView.findViewById(R.id.dialog_alias_active)
        val tvAddress: TextView = dialogView.findViewById(R.id.dialog_alias_address)
        val tvGoto: TextView = dialogView.findViewById(R.id.dialog_alias_goto)
        val tvDomain: TextView = dialogView.findViewById(R.id.dialog_alias_domain)
        val tvCreated: TextView = dialogView.findViewById(R.id.dialog_alias_created)

        val layoutPrivateComment: LinearLayout = dialogView.findViewById(R.id.layout_private_comment)
        val tvPrivateComment: TextView = dialogView.findViewById(R.id.dialog_alias_private_comment)

        val layoutPublicComment: LinearLayout = dialogView.findViewById(R.id.layout_public_comment)
        val tvPublicComment: TextView = dialogView.findViewById(R.id.dialog_alias_public_comment)

        tvActive.text = if (active == 1) "Yes" else "No"
        tvAddress.text = address
        tvGoto.text = goto
        tvDomain.text = domain
        tvCreated.text = created

        if (privateComment.isNotEmpty()) {
            tvPrivateComment.text = privateComment
            layoutPrivateComment.visibility = View.VISIBLE
        } else {
            layoutPrivateComment.visibility = View.GONE
        }

        if (publicComment.isNotEmpty()) {
            tvPublicComment.text = publicComment
            layoutPublicComment.visibility = View.VISIBLE
        } else {
            layoutPublicComment.visibility = View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("Alias details")
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .setNegativeButton("Edit") { _, _ ->
                showEditDialog(address, goto, privateComment, publicComment, active)
            }
            .setNeutralButton("Delete") { _, _ ->
                showDeleteConfirmationDialog(address)
            }
            .show()
    }

    private fun showEditDialog(address: String, currentGoto: String, currentPrivateComment: String, currentPublicComment: String, currentActive: Int) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val addressLabel = TextView(this).apply{
            text = "Editing: $address"
            setTextAppearance(android.R.style.TextAppearance_Medium)
            setPadding(0,0,0, (16 * resources.displayMetrics.density).toInt())
        }

        val gotoInput = EditText(this).apply {
            hint = "Goto"
            setText(currentGoto)
        }
        val privateCommentInput = EditText(this).apply {
            hint = "Private Comment"
            setText(currentPrivateComment)
        }
        val publicCommentInput = EditText(this).apply {
            hint = "Public Comment"
            setText(currentPublicComment)
        }
        val activeCheckbox = CheckBox(this).apply {
            text = "Active"
            isChecked = (currentActive == 1)
        }

        layout.addView(addressLabel)
        layout.addView(gotoInput)
        layout.addView(privateCommentInput)
        layout.addView(publicCommentInput)
        layout.addView(activeCheckbox)

        AlertDialog.Builder(this)
            .setTitle("Edit Alias")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newGoto = gotoInput.text.toString().trim()
                val newPrivateComment = privateCommentInput.text.toString().trim()
                val newPublicComment = publicCommentInput.text.toString().trim()
                val newActive = if (activeCheckbox.isChecked) "1" else "0"

                if (newGoto.isEmpty()) {
                    Toast.makeText(this, "Goto must not be empty!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                editAlias(address, newGoto, newPrivateComment, newPublicComment, newActive)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editAlias(address: String, newGoto: String, newPrivateComment: String, newPublicComment: String, newActive: String) {
        swipeRefresh.isRefreshing = true
        val apiUrl = "https://$baseUrl/api/v1/edit/alias"

        val json = JSONObject().apply {
            put("attr", JSONObject().apply {
                put("active", newActive)
                put("address", address)
                put("goto", newGoto)
                put("private_comment", newPrivateComment)
                put("public_comment", newPublicComment)
            })
            put("items", JSONArray().put(address))
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(apiUrl)
            .post(body)
            .addHeader("X-Api-Key", apiKey)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(this@AliasListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    if (response.isSuccessful) {
                        Toast.makeText(this@AliasListActivity, "Alias updated successfully", Toast.LENGTH_SHORT).show()
                        loadAliases()
                    } else {
                        Toast.makeText(this@AliasListActivity, "Error updating alias (${response.code}): $responseBody", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun showDeleteConfirmationDialog(address: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Alias")
            .setMessage("Are you sure you want to delete the alias \"$address\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteAlias(address)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAlias(address: String) {
        swipeRefresh.isRefreshing = true
        val apiUrl = "https://$baseUrl/api/v1/delete/alias"

        val jsonPayloadArray = JSONArray().put(address)

        val body = jsonPayloadArray.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(apiUrl)
            .post(body)
            .addHeader("X-Api-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(this@AliasListActivity, "Error deleting alias: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    if (response.isSuccessful) {
                        Toast.makeText(this@AliasListActivity, "Alias deleted successfully", Toast.LENGTH_SHORT).show()
                        loadAliases()
                    } else {
                        Toast.makeText(this@AliasListActivity, "Error deleting alias (${response.code}): $responseBody", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}
