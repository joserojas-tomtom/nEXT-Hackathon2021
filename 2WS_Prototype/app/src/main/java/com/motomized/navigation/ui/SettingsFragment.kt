/*
 * © 2021 TomTom N.V. All rights reserved.
 *
 * This software is the proprietary copyright of TomTom N.V. and its subsidiaries and may be used
 * for internal evaluation purposes or commercial use strictly subject to separate licensee
 * agreement between you and TomTom. If you are the licensee, you are only permitted to use
 * this software in accordance with the terms of your license agreement. If you are not the
 * licensee then you are not authorized to use this software in any manner and should
 * immediately return it to TomTom N.V.
 */

package com.motomized.navigation.ui

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import com.motomized.navigation.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(resources.getColor(R.color.transparent_mask))
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}
