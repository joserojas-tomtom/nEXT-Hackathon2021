package com.motomized.navigation.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class SettingsProvider(private val context:Context) {

    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun simulateDrive() = preferences.getBoolean("simulate_drive", false)
}