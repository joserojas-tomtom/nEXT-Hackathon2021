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

import com.tomtom.sdk.maps.display.gesture.OnMapPanningListener

/**
 * Helper class that may be extended and where the methods may be
 * implemented. This way it is not necessary to implement all methods
 * of [OnMapPanningListener].
 */
abstract class SimpleMapPanningListener : OnMapPanningListener {

    override fun onMapPanningEnded() = Unit

    override fun onMapPanningOngoing() = Unit

    override fun onMapPanningStarted() = Unit
}
