package com.pythonius.mailcowapi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: Toolbar = findViewById(R.id.settingsToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val versionText: TextView = findViewById(R.id.settingsAppVersion)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "Version ${pInfo.versionName}"
        } catch (e: Exception) {
            versionText.text = "Version 1.0.7"
        }

        val prefs = getSharedPreferences("mailcow_theme", Context.MODE_PRIVATE)
        val currentTheme = prefs.getString("theme", "dark")

        val radioGroup: RadioGroup = findViewById(R.id.themeRadioGroup)
        when (currentTheme) {
            "light" -> radioGroup.check(R.id.radioLight)
            "system" -> radioGroup.check(R.id.radioSystem)
            else -> radioGroup.check(R.id.radioDark)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radioLight -> "light"
                R.id.radioSystem -> "system"
                else -> "dark"
            }
            prefs.edit().putString("theme", theme).apply()
            applyTheme(theme)
        }

        val githubLink = findViewById<TextView>(R.id.githubLink)
        githubLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pythonius21/Mailcow-Alias-API"))
            startActivity(intent)
        }

        val biometricSwitch = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.biometricSwitch)
        val secPrefs = getSharedPreferences("mailcow_security", Context.MODE_PRIVATE)
        biometricSwitch.isChecked = secPrefs.getBoolean("biometric_enabled", true)
        biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            secPrefs.edit().putBoolean("biometric_enabled", isChecked).apply()
        }
    }

    private fun applyTheme(theme: String) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}