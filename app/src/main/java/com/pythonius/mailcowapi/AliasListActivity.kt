package com.pythonius.mailcowapi

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.pythonius.mailcowapi.R.layout.dialog_alias_details
import com.pythonius.mailcowapi.R.layout.dialog_edit_alias
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
    private lateinit var listContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar

    private var allAliasesJson: String = "[]"
    private var isSelectionMode = false
    private val selectedAddresses = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }

        setContentView(R.layout.activity_alias_list)
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        sharedPreferences = EncryptedSharedPreferences.create(
            this,
            "mailcow_prefs_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        baseUrl = sharedPreferences.getString("server_url", "") ?: ""
        apiKey = sharedPreferences.getString("api_key", "") ?: ""

        aliasContainer = findViewById(R.id.aliasContainer)
        listContainer = findViewById(R.id.listContainer)
        searchInput = findViewById(R.id.searchInput)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionTextView = navigationView.findViewById<TextView>(R.id.nav_app_version)
            versionTextView.text = pInfo.versionName
        } catch (e: Exception) {
            // ignore
        }

        ViewCompat.setOnApplyWindowInsetsListener(swipeRefresh) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            val lp = view.layoutParams as ViewGroup.MarginLayoutParams
            lp.topMargin = topInset
            view.layoutParams = lp
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(navigationView) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(view.paddingLeft, topInset, view.paddingRight, view.paddingBottom)
            insets
        }

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "Please make sure the server URL and API key are filled", Toast.LENGTH_LONG).show()
            // Consider finishing or guiding to settings
            return
        }

        swipeRefresh.setOnRefreshListener {
            loadAliases()
        }

        loadAliases()

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filterAliases(s?.toString() ?: "")
            }
        })

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
                    displayAliases("[]") // Pass empty JSON array string to show "No aliases"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    if (response.isSuccessful && !body.isNullOrEmpty()) {
                        try {
                            // 1. Parse the JSON string into a list of JSONObjects
                            val originalArray = JSONArray(body)
                            val aliasObjectList = mutableListOf<JSONObject>()
                            for (i in 0 until originalArray.length()) {
                                aliasObjectList.add(originalArray.getJSONObject(i))
                            }
                            aliasObjectList.sortByDescending { it.optLong("id", 0L) }

                            // 3. Convert the sorted list back to a JSON array string
                            val sortedJsonArray = JSONArray(aliasObjectList)
                            allAliasesJson = sortedJsonArray.toString()
                            filterAliases(searchInput.text.toString())

                        } catch (e: Exception) {
                            // Catch parsing/sorting errors
                            Toast.makeText(this@AliasListActivity, "Error processing alias data: ${e.message}", Toast.LENGTH_LONG).show()
                            displayAliases("[]") // Show empty state on error
                        }
                    } else {
                        Toast.makeText(this@AliasListActivity, "Error loading aliases: ${response.message} (${response.code})", Toast.LENGTH_SHORT).show()
                        displayAliases("[]") // Pass empty JSON array string
                    }
                }
            }
        })
    }

    private fun filterAliases(query: String) {
        if (allAliasesJson == "[]") {
            displayAliases(allAliasesJson)
            return
        }
        try {
            val aliasArray = JSONArray(allAliasesJson)
            if (query.isEmpty()) {
                displayAliases(allAliasesJson)
                return
            }
            val filteredList = mutableListOf<JSONObject>()
            val lowerQuery = query.lowercase()
            for (i in 0 until aliasArray.length()) {
                val obj = aliasArray.getJSONObject(i)
                val privateComment = obj.optString("private_comment", "")
                val publicComment = obj.optString("public_comment", "")
                if (privateComment.lowercase().contains(lowerQuery) || publicComment.lowercase().contains(lowerQuery)) {
                    filteredList.add(obj)
                }
            }
            val filteredArray = JSONArray(filteredList)
            displayAliases(filteredArray.toString())
        } catch (e: Exception) {
            displayAliases(allAliasesJson)
        }
    }

    private fun displayAliases(jsonString: String) {
        listContainer.removeAllViews()

        try {
            val aliasArray = JSONArray(jsonString)

            if (aliasArray.length() == 0) {
                // More styled "No aliases found" message
                val emptyTextView = TextView(this).apply {
                    text = "No aliases found.\nPull down to refresh or tap '+' to add."
                    gravity = Gravity.CENTER // Center the text within the TextView
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
                listContainer.addView(emptyTextView)
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

                // Use TextView instead of Button for alias items
                val aliasView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.rounded_alias_background)

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    val marginInDp = 4
                    val marginVerticalInPx = (marginInDp * resources.displayMetrics.density).toInt()
                    val marginHorizontalInPx = (16 * resources.displayMetrics.density).toInt()
                    params.setMargins(marginHorizontalInPx, marginVerticalInPx, marginHorizontalInPx, marginVerticalInPx)
                    layoutParams = params

                    val paddingInDp = 16
                    val paddingInPx = (paddingInDp * resources.displayMetrics.density).toInt()
                    setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)

                    val addressText = TextView(this@AliasListActivity).apply {
                        text = address
                        setTextColor(ContextCompat.getColor(this@AliasListActivity, R.color.neon_cyan))
                        textSize = 16f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    addView(addressText)

                    if (goto.isNotEmpty() && goto != address) {
                        val gotoText = TextView(this@AliasListActivity).apply {
                            text = "\u279C ${goto.take(40)}${if (goto.length > 40) "..." else ""}"
                            setTextColor(ContextCompat.getColor(this@AliasListActivity, R.color.text_secondary))
                            textSize = 14f
                            setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
                        }
                        addView(gotoText)
                    }

                    if (active == 0) {
                        val inactiveText = TextView(this@AliasListActivity).apply {
                            text = "INACTIVE"
                            setTextColor(ContextCompat.getColor(this@AliasListActivity, R.color.text_muted))
                            textSize = 11f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            letterSpacing = 0.1f
                            setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
                        }
                        addView(inactiveText)
                    }

                    isClickable = true
                    isFocusable = true

                    setOnClickListener {
                        if (isSelectionMode) {
                            toggleSelection(address, this)
                        } else {
                            showAliasDetailsDialog(address, goto, domain, created, active, privateComment, publicComment)
                        }
                    }

                    setOnLongClickListener {
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            toggleSelection(address, this)
                            toolbar.menu.clear()
                            toolbar.inflateMenu(R.menu.menu_selection)
                            toolbar.setNavigationIcon(R.drawable.ic_close)
                            toolbar.setNavigationOnClickListener {
                                clearSelection()
                            }
                        }
                        true
                    }
                }
                listContainer.addView(aliasView)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error parsing data: ${e.message}", Toast.LENGTH_LONG).show()
            // Optionally display the "No aliases found" message here too
            listContainer.removeAllViews()
            val errorTextView = TextView(this).apply { text = "Error displaying aliases."; gravity = Gravity.CENTER }
            listContainer.addView(errorTextView)
        }
    }

    private fun toggleSelection(address: String, view: View) {
        if (selectedAddresses.contains(address)) {
            selectedAddresses.remove(address)
            view.alpha = 1.0f
        } else {
            selectedAddresses.add(address)
            view.alpha = 0.5f
        }
        if (selectedAddresses.isEmpty()) {
            clearSelection()
        }
    }

    private fun clearSelection() {
        isSelectionMode = false
        selectedAddresses.clear()
        toolbar.menu.clear()
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        filterAliases(searchInput.text.toString())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                showBatchDeleteConfirmation()
                true
            }
            R.id.action_toggle_active -> {
                showBatchToggleActiveConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showBatchDeleteConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Aliases")
            .setMessage("Are you sure you want to delete ${selectedAddresses.size} alias(es)?")
            .setPositiveButton("Delete") { _, _ ->
                batchDelete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBatchToggleActiveConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Toggle Active Status")
            .setMessage("Toggle active status for ${selectedAddresses.size} alias(es)?")
            .setPositiveButton("Toggle") { _, _ ->
                batchToggleActive()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun batchDelete() {
        swipeRefresh.isRefreshing = true
        val jsonPayloadArray = JSONArray()
        selectedAddresses.forEach { jsonPayloadArray.put(it) }
        val body = jsonPayloadArray.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://$baseUrl/api/v1/delete/alias")
            .post(body)
            .addHeader("X-Api-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(this@AliasListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    if (response.isSuccessful) {
                        Toast.makeText(this@AliasListActivity, "Aliases deleted", Toast.LENGTH_SHORT).show()
                        clearSelection()
                        loadAliases()
                    } else {
                        Toast.makeText(this@AliasListActivity, "Error deleting aliases", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun batchToggleActive() {
        swipeRefresh.isRefreshing = true
        val json = JSONObject().apply {
            put("attr", JSONObject().apply {
                put("active", "1")
            })
            put("items", JSONArray().apply {
                selectedAddresses.forEach { put(it) }
            })
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://$baseUrl/api/v1/edit/alias")
            .post(body)
            .addHeader("X-Api-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(this@AliasListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    if (response.isSuccessful) {
                        Toast.makeText(this@AliasListActivity, "Aliases updated", Toast.LENGTH_SHORT).show()
                        clearSelection()
                        loadAliases()
                    } else {
                        Toast.makeText(this@AliasListActivity, "Error updating aliases", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun showAliasDetailsDialog(address: String, goto: String, domain: String, created: String, active: Int, privateComment: String, publicComment: String) {
        val dialogView = LayoutInflater.from(this).inflate(dialog_alias_details, null)

        dialogView.findViewById<TextView>(R.id.dialog_alias_active).text = if (active == 1) "Yes" else "No"
        dialogView.findViewById<TextView>(R.id.dialog_alias_address).text = address
        dialogView.findViewById<TextView>(R.id.dialog_alias_goto).text = goto
        dialogView.findViewById<TextView>(R.id.dialog_alias_domain).text = domain
        dialogView.findViewById<TextView>(R.id.dialog_alias_created).text = created

        val layoutPrivateComment = dialogView.findViewById<LinearLayout>(R.id.layout_private_comment)
        val layoutPublicComment = dialogView.findViewById<LinearLayout>(R.id.layout_public_comment)

        if (privateComment.isNotEmpty()) {
            dialogView.findViewById<TextView>(R.id.dialog_alias_private_comment).text = privateComment
            layoutPrivateComment.visibility = View.VISIBLE
        } else {
            layoutPrivateComment.visibility = View.GONE
        }

        if (publicComment.isNotEmpty()) {
            dialogView.findViewById<TextView>(R.id.dialog_alias_public_comment).text = publicComment
            layoutPublicComment.visibility = View.VISIBLE
        } else {
            layoutPublicComment.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<Button>(R.id.dialog_btn_ok).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.dialog_btn_edit).setOnClickListener {
            dialog.dismiss()
            showEditDialog(address, goto, privateComment, publicComment, active)
        }
        dialogView.findViewById<Button>(R.id.dialog_btn_delete).setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmationDialog(address)
        }

        dialog.show()
    }

    private fun showEditDialog(address: String, currentGoto: String, currentPrivateComment: String, currentPublicComment: String, currentActive: Int) {
        val dialogView = LayoutInflater.from(this).inflate(dialog_edit_alias, null)

        dialogView.findViewById<TextView>(R.id.edit_alias_title).text = "EDIT ALIAS"
        dialogView.findViewById<TextView>(R.id.edit_alias_address).text = "Editing: $address"

        val gotoInput = dialogView.findViewById<EditText>(R.id.edit_goto_input)
        gotoInput.setText(currentGoto)

        val privateCommentInput = dialogView.findViewById<EditText>(R.id.edit_private_comment_input)
        privateCommentInput.setText(currentPrivateComment)

        val publicCommentInput = dialogView.findViewById<EditText>(R.id.edit_public_comment_input)
        publicCommentInput.setText(currentPublicComment)

        val activeCheckbox = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.edit_active_checkbox)
        activeCheckbox.isChecked = (currentActive == 1)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<Button>(R.id.edit_btn_save).setOnClickListener {
            val newGoto = gotoInput.text.toString().trim()
            val newPrivateComment = privateCommentInput.text.toString().trim()
            val newPublicComment = publicCommentInput.text.toString().trim()
            val newActive = if (activeCheckbox.isChecked) "1" else "0"

            if (newGoto.isEmpty()) {
                Toast.makeText(this, "Goto must not be empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            editAlias(address, newGoto, newPrivateComment, newPublicComment, newActive)
        }

        dialogView.findViewById<Button>(R.id.edit_btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // editAlias remains the same as your original
    private fun editAlias(address: String, newGoto: String, newPrivateComment: String, newPublicComment: String, newActive: String) {
        swipeRefresh.isRefreshing = true // Show loading indicator
        val apiUrl = "https://$baseUrl/api/v1/edit/alias"

        val json = JSONObject().apply {
            put("attr", JSONObject().apply {
                put("active", newActive)
                put("address", address) // The original address for identification
                put("goto", newGoto)
                put("private_comment", newPrivateComment)
                put("public_comment", newPublicComment)
            })
            put("items", JSONArray().put(address)) // Target item by its address
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
                val responseBody = response.body?.string() // Read body for potential error messages
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

    // showDeleteConfirmationDialog remains the same as your original
    private fun showDeleteConfirmationDialog(address: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Alias")
            .setMessage("Are you sure you want to delete the alias \"$address\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteAlias(address)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // deleteAlias: Ensure payload is correct for Mailcow API (often an array of items)
    private fun deleteAlias(address: String) {
        swipeRefresh.isRefreshing = true // Show loading indicator
        val apiUrl = "https://$baseUrl/api/v1/delete/alias"

        // Mailcow's delete API usually expects a JSON array of items (addresses) to delete.
        val jsonPayloadArray = JSONArray().put(address)

        val body = jsonPayloadArray.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(apiUrl)
            .post(body) // Or .delete(body) if API supports DELETE with body, but POST is common
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
                val responseBody = response.body?.string() // Read body for potential error messages
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
