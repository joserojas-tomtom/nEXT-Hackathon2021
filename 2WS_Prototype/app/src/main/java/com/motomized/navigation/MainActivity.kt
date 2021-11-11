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

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.markodevcic.peko.ActivityRotatingException
import com.tomtom.sdk.common.location.GeoCoordinate
import com.tomtom.sdk.common.measures.Units
import com.tomtom.sdk.common.route.Route
import com.tomtom.sdk.maps.display.TomTomMap
import com.tomtom.sdk.maps.display.camera.CameraTrackingMode
import com.tomtom.sdk.maps.display.camera.OnCameraChangeListener
import com.tomtom.sdk.maps.display.common.screen.Padding
import com.tomtom.sdk.maps.display.gesture.OnMapLongClickListener
import com.tomtom.sdk.maps.display.location.AccuracyIndicatorType
import com.tomtom.sdk.maps.display.location.LocationMarkerOptions
import com.tomtom.sdk.maps.display.location.LocationMarkerType
import com.tomtom.sdk.maps.display.route.RouteOptions
import com.tomtom.sdk.maps.display.ui.MapFragment
import com.tomtom.sdk.maps.display.ui.OnMapReadyCallback
import com.tomtom.sdk.maps.display.ui.OnUiComponentClickListener
import com.tomtom.sdk.navigation.NavigationException
import com.motomized.navigation.common.permission.PermissionChecker
import com.motomized.navigation.ui.SettingsFragment
import com.motomized.navigation.ui.SimpleMapPanningListener
import com.motomized.navigation.R
import com.tomtom.sdk.navigation.ui.NavigationFragment
import com.tomtom.sdk.navigation.ui.NavigationUiOptions
import java.util.Locale
import kotlinx.coroutines.Job
import timber.log.Timber
import com.tomtom.sdk.maps.display.ui.compass.VisibilityPolicy as CompassVisibilityPolicy
import com.tomtom.sdk.maps.display.ui.logo.VisibilityPolicy as LogoVisibilityPolicy

class MainActivity : AppCompatActivity(), OnMapReadyCallback, OnMapLongClickListener {

    private lateinit var viewModel: MainViewModel
    private lateinit var tomTomMap: TomTomMap
    private lateinit var progressDialog: AlertDialog
    private lateinit var settingsButton: FloatingActionButton

    private var permissionJob: Job? = null
    private var capturedCameraTrackingMode: CameraTrackingMode? = null

    private val onCameraChangeListener by lazy {
        OnCameraChangeListener {
            if (isInOverview()) {
                // we just exited the tilted map view mode used while navigating/free driving
                tomTomMap.setPadding(DEFAULT_MAP_PADDING)
                adjustCompassVisibilityInOverview()
            }

            tomTomMap.cameraTrackingMode().let {
                if (capturedCameraTrackingMode != it) {
                    toggleSpeedView()
                    capturedCameraTrackingMode = it
                }
            }
        }
    }

    private val onMapPanningListener by lazy {
        object : SimpleMapPanningListener() {
            override fun onMapPanningStarted() {
                tomTomMap.changeCameraTrackingMode(CameraTrackingMode.NONE)
            }
        }
    }

    private val currentLocationButtonClickListener by lazy {
        OnUiComponentClickListener {
            if (viewModel.state().value is MainViewState.Navigating ||
                viewModel.state().value is MainViewState.FreeDriving
            ) {
                tomTomMap.changeCameraTrackingMode(CameraTrackingMode.FOLLOW_WITH_HEADING)
                tomTomMap.compassButton.visibilityPolicy = CompassVisibilityPolicy.INVISIBLE
                adjustMapPadding()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setUpView()
        requestPermissions()
    }

    private fun requestPermissions() {
        permissionJob = requestPermissions {
            val mapFragment =
                supportFragmentManager.findFragmentById(R.id.map_fragment) as MapFragment
            mapFragment.getMapAsync(this)
            viewModel.onPermissionsGranted()
        }
    }

    private fun setUpView() {
        progressDialog = createProgressDialog()
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        settingsButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .add(R.id.settings_container, SettingsFragment.newInstance())
                .addToBackStack(FRAGMENT_TAG_SETTINGS)
                .commit()
        }
    }

    private fun renderState(state: MainViewState) {
        when (state) {
            is MainViewState.FreeDriving -> handleFreeDrivingState()
            is MainViewState.Loading -> handleLoadingState()
            is MainViewState.Navigating -> handleNavigationState(state)
        }
    }

    private fun handleFreeDrivingState() {
        Timber.d("handleFreeDrivingState()")
        removeAndStopNavigationFragment()
        progressDialog.dismiss()
        tomTomMap.removeRoutes()
        tomTomMap.setPadding(freeDrivingMapPadding())
        tomTomMap.logoView.visibilityPolicy = LogoVisibilityPolicy.VISIBLE
        tomTomMap.compassButton.visibilityPolicy = CompassVisibilityPolicy.INVISIBLE
        tomTomMap.changeCameraTrackingMode(CameraTrackingMode.FOLLOW_WITH_HEADING)
        tomTomMap.setLocationEngine(viewModel.mapMatchedLocationEngine())
        tomTomMap.addOnCameraChangeListener(onCameraChangeListener)
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        settingsButton.show()
    }

    private fun handleLoadingState() {
        Timber.d("handleLoadingState()")
        progressDialog.show()
        settingsButton.hide()
        tomTomMap.removeOnCameraChangeListener(onCameraChangeListener)
    }

    private fun handleNavigationState(state: MainViewState.Navigating) {
        Timber.d("handleNavigationState()")
        progressDialog.dismiss()
        tomTomMap.addOnCameraChangeListener(onCameraChangeListener)
        tomTomMap.setPadding(navigationPadding())
        tomTomMap.removeRoutes()
        tomTomMap.logoView.visibilityPolicy = LogoVisibilityPolicy.INVISIBLE
        tomTomMap.compassButton.visibilityPolicy = CompassVisibilityPolicy.INVISIBLE
        tomTomMap.addRoute(RouteOptions(state.route.legs.flatMap { it.points }))
        tomTomMap.changeCameraTrackingMode(CameraTrackingMode.FOLLOW_WITH_HEADING)
        tomTomMap.setLocationEngine(viewModel.mapMatchedLocationEngine())
        startOrUpdateNavigation(state.route)
        adjustCurrentLocationButtonMarginForNavigation()
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        settingsButton.hide()
    }

    private fun adjustMapPadding() {
        when (viewModel.state().value) {
            is MainViewState.Navigating -> tomTomMap.setPadding(navigationPadding())
            is MainViewState.FreeDriving -> tomTomMap.setPadding(freeDrivingMapPadding())
            else -> Unit
        }
    }

    private fun freeDrivingMapPadding(): Padding {
        val paddingLeft = resources.getDimensionPixelOffset(R.dimen.free_driving_map_padding_left)
        val paddingTop = resources.getDimensionPixelOffset(R.dimen.free_driving_map_padding_top)
        val paddingRight = resources.getDimensionPixelOffset(R.dimen.free_driving_map_padding_right)
        val paddingBottom =
            resources.getDimensionPixelOffset(R.dimen.free_driving_map_padding_bottom)
        return Padding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    private fun navigationPadding(): Padding {
        val paddingLeft = resources.getDimensionPixelOffset(R.dimen.navigation_map_padding_left)
        val paddingTop = resources.getDimensionPixelOffset(R.dimen.navigation_map_padding_top)
        val paddingRight = resources.getDimensionPixelOffset(R.dimen.navigation_map_padding_right)
        val paddingBottom = resources.getDimensionPixelOffset(R.dimen.navigation_map_padding_bottom)
        return Padding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    override fun onMapReady(map: TomTomMap) {
        if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        tomTomMap = map
        tomTomMap.addOnMapPanningListener(onMapPanningListener)
        tomTomMap.addOnMapLongClickListener(this)
        tomTomMap.currentLocationButton.addOnCurrentLocationButtonClickListener(
            currentLocationButtonClickListener
        )
        val options = LocationMarkerOptions(
            type = LocationMarkerType.CHEVRON,
            accuracyIndicatorType = AccuracyIndicatorType.NONE
        )
        tomTomMap.enableLocationMarker(options)
        viewModel.state().observe(this) { renderState(it) }
        viewModel.messages().observe(this) { showToast(it) }
        viewModel.distanceToInstruction().observe(this) {
            adjustCurrentLocationButtonMarginForNavigation()
        }
    }

    override fun onMapLongClicked(coordinate: GeoCoordinate): Boolean {
        removeAndStopNavigationFragment()
        tomTomMap.removeRoutes()
        viewModel.planRoute(coordinate)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        clearPermissionJob()
        if (this::tomTomMap.isInitialized) {
            tomTomMap.removeOnMapLongClickListener(this)
            tomTomMap.removeOnMapPanningListener(onMapPanningListener)
            tomTomMap.currentLocationButton.removeOnCurrentLocationButtonClickListener(
                currentLocationButtonClickListener
            )
            tomTomMap.removeOnCameraChangeListener(onCameraChangeListener)
        }
        navigationFragment()?.removeNavigationListener(navigationListener)
        progressDialog.dismiss()
    }

    private fun clearPermissionJob() {
        if (isChangingConfigurations) {
            permissionJob?.cancel(ActivityRotatingException())
        } else {
            permissionJob?.cancel()
        }
    }

    private fun startOrUpdateNavigation(route: Route) {
        val fragment = navigationFragment()
        if (fragment == null) {
            startNavigationFragment(route)
        } else {
            // After deviation the `Start driving` instruction is not necessary
            val updatedRoute = route.removeFirstInstruction()
            fragment.updateRoute(updatedRoute)
            fragment.addNavigationListener(navigationListener)
        }
    }

    private fun startNavigationFragment(route: Route) {
        val navigationUiOptions = NavigationUiOptions.Builder()
            .units(Units.AUTO)
            .soundEnabled(true)
            .voiceLanguage(Locale.US)
            .build()
        val navFragment = NavigationFragment.newInstance(navigationUiOptions)
        supportFragmentManager.beginTransaction()
            .add(R.id.navigation_fragment_container, navFragment, FRAGMENT_TAG_NAVIGATION)
            .commitNow()
        viewModel.navigation.stop()
        navFragment.setTomTomNavigation(viewModel.navigation)
        navFragment.addNavigationListener(navigationListener)
        navFragment.startNavigation(listOf(route))
    }

    private fun removeAndStopNavigationFragment() {
        navigationFragment()?.let {
            supportFragmentManager.beginTransaction()
                .remove(it)
                .commit()
        }
    }

    private val navigationListener = object : NavigationFragment.NavigationListener {
        override fun onCancelled() {
            Timber.d("onCancelled()")
            removeAndStopNavigationFragment()
            viewModel.navigationStopped()
            updateCurrentLocationButtonMargins(DEFAULT_CURRENT_LOCATION_ADDITIONAL_MARGIN)
        }

        override fun onFailed(exception: NavigationException) {
            Timber.d("onFailed()")
            removeAndStopNavigationFragment()
            viewModel.navigationStopped()
            updateCurrentLocationButtonMargins(DEFAULT_CURRENT_LOCATION_ADDITIONAL_MARGIN)
        }

        override fun onStarted() {
            Timber.d("onStarted()")
        }
    }

    private fun requestPermissions(onPermissionGranted: () -> Unit): Job =
        if (PermissionChecker.isPermissionCheckInProgress()) {
            PermissionChecker.resumeCheck(onPermissionGranted)
        } else {
            PermissionChecker.check(this, onPermissionGranted)
        }

    private fun createProgressDialog(): AlertDialog {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        val customLayout: View = layoutInflater.inflate(R.layout.dialog_progress, null)
        builder.setView(customLayout)
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun Route.removeFirstInstruction(): Route {
        val firstLeg = legs.first()
        val updatedInstructions = firstLeg.instructions.drop(1)
        val updatedLeg = firstLeg.copy(instructions = updatedInstructions)
        val updatedLegs = listOf(updatedLeg) + legs.drop(1)
        return copy(legs = updatedLegs)
    }

    override fun onResume() {
        super.onResume()
        supportFragmentManager.addOnBackStackChangedListener(onBackStackChangeListener)
    }

    override fun onPause() {
        super.onPause()
        supportFragmentManager.removeOnBackStackChangedListener(onBackStackChangeListener)
    }

    private val onBackStackChangeListener = FragmentManager.OnBackStackChangedListener {
        alignControlButtons()
    }

    private fun alignControlButtons() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            settingsButton.hide()
        } else {
            settingsButton.show()
        }
    }

    private fun toggleSpeedView() {
        val isNavigatingAndFollowingPosition = viewModel.state().value is MainViewState.Navigating
                && tomTomMap.cameraTrackingMode() == CameraTrackingMode.FOLLOW_WITH_HEADING
        navigationFragment()?.speedView?.isVisible = isNavigatingAndFollowingPosition
    }

    private fun adjustCompassVisibilityInOverview() {
        val isFreeDriving = viewModel.state().value is MainViewState.FreeDriving
        val canShowCompass =
            tomTomMap.compassButton.visibilityPolicy != CompassVisibilityPolicy.INVISIBLE_WHEN_NORTH_UP
        if (isFreeDriving && canShowCompass) {
            tomTomMap.compassButton.visibilityPolicy =
                CompassVisibilityPolicy.INVISIBLE_WHEN_NORTH_UP
        }
    }

    private fun isInOverview(): Boolean =
        tomTomMap.cameraPosition().tilt == CAMERA_TILT_NONE &&
                tomTomMap.cameraTrackingMode() == CameraTrackingMode.NONE

    private fun adjustCurrentLocationButtonMarginForNavigation() {
        val orientation = resources.configuration.orientation
        val additionalMargin = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            navigationFragment()?.guidanceView?.viewBoundaries?.height
                ?: DEFAULT_CURRENT_LOCATION_ADDITIONAL_MARGIN
        } else {
            DEFAULT_CURRENT_LOCATION_ADDITIONAL_MARGIN
        }
        updateCurrentLocationButtonMargins(additionalMargin)
    }

    private fun updateCurrentLocationButtonMargins(additionalMargin: Int) {
        tomTomMap.currentLocationButton.apply {
            val defaultMargin =
                resources.getDimensionPixelOffset(R.dimen.current_location_margin_bottom)
            val bottomMargin = defaultMargin + additionalMargin
            margin = margin.copy(bottom = bottomMargin)
        }
    }

    private fun navigationFragment(): NavigationFragment? =
        supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_NAVIGATION) as? NavigationFragment

    companion object {
        private const val FRAGMENT_TAG_NAVIGATION = "TT_NAVIGATION_FRAGMENT"
        private const val FRAGMENT_TAG_SETTINGS = "SETTINGS_FRAGMENT"
        private const val DEFAULT_CURRENT_LOCATION_ADDITIONAL_MARGIN = 0
        private const val CAMERA_TILT_NONE = 0.0
        private val DEFAULT_MAP_PADDING = Padding(0, 0, 0, 0)
    }
}
