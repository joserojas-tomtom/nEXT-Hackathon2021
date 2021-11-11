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
package com.motomized.navigation.common.permission

import android.Manifest
import android.content.Context
import com.markodevcic.peko.Peko.isRequestInProgress
import com.markodevcic.peko.Peko.requestPermissionsAsync
import com.markodevcic.peko.Peko.resumeRequest
import com.markodevcic.peko.PermissionResult
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

object PermissionChecker {

    private val mainDispatcher = Dispatchers.Main
    private val coroutineScope =
        CoroutineScope(mainDispatcher) + CoroutineName("PermissionChecker")

    fun check(context: Context, callback: () -> Unit): Job {
        return coroutineScope.launch {
            val result = requestPermissionsAsync(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET
            )

            if (result is PermissionResult.Granted) {
                callback.invoke()
            }
        }
    }

    fun resumeCheck(callback: () -> Unit): Job {
        return coroutineScope.launch {
            // get the existing request and await the result
            val result = resumeRequest()
            // check granted permissions
            if (result is PermissionResult.Granted) {
                callback.invoke()
            }
        }
    }

    fun isPermissionCheckInProgress(): Boolean = isRequestInProgress()
}
