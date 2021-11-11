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
package com.motomized.navigation

import android.app.Application
import timber.log.Timber

@Suppress("unused")
class GoNavApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
