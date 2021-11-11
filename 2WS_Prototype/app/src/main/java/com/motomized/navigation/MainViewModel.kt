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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tomtom.sdk.common.Result
import com.tomtom.sdk.common.event.LiveDataEvent
import com.tomtom.sdk.common.location.GeoCoordinate
import com.tomtom.sdk.common.location.GeoLocation
import com.tomtom.sdk.common.route.Route
import com.tomtom.sdk.location.AndroidLocationEngine
import com.tomtom.sdk.location.LocationEngine
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationEngine
import com.tomtom.sdk.location.simulation.SimulationLocationEngine
import com.tomtom.sdk.location.simulation.strategy.InterpolationStrategy
import com.tomtom.sdk.navigation.NavigationConfiguration
import com.tomtom.sdk.navigation.NavigationException
import com.tomtom.sdk.navigation.OnDestinationReachedListener
import com.tomtom.sdk.navigation.OnErrorListener
import com.tomtom.sdk.navigation.OnGuidanceUpdateListener
import com.tomtom.sdk.navigation.OnLocationContextUpdateListener
import com.tomtom.sdk.navigation.OnLocationMatchedListener
import com.tomtom.sdk.navigation.OnNavigationStartedListener
import com.tomtom.sdk.navigation.OnRouteDeviationListener
import com.tomtom.sdk.navigation.OnRouteRefreshListener
import com.tomtom.sdk.navigation.OnRouteUpdatedListener
import com.tomtom.sdk.navigation.TomTomNavigation
import com.motomized.navigation.ui.SettingsProvider
import com.tomtom.sdk.navigation.guidance.model.GuidanceAnnouncement
import com.tomtom.sdk.navigation.guidance.model.GuidanceInstruction
import com.tomtom.sdk.navigation.locationcontext.LocationContext
import com.motomized.navigation.R
import com.tomtom.sdk.routing.client.RoutingApi
import com.tomtom.sdk.routing.client.RoutingException
import com.tomtom.sdk.routing.client.RoutingOptions
import com.tomtom.sdk.routing.client.RoutingResult
import com.tomtom.sdk.routing.client.model.calculation.InstructionsType
import com.tomtom.sdk.routing.client.model.diagnostic.ReportType
import com.tomtom.sdk.routing.client.model.guidance.AnnouncementPoints
import com.tomtom.sdk.routing.client.model.guidance.InstructionPhoneticsType
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MainViewModel(application: Application) :
    AndroidViewModel(application),
    OnLocationMatchedListener,
    OnLocationContextUpdateListener,
    OnRouteDeviationListener,
    OnGuidanceUpdateListener,
    OnNavigationStartedListener,
    OnErrorListener,
    OnDestinationReachedListener,
    OnRouteRefreshListener,
    OnRouteUpdatedListener {

    private val routingApi: RoutingApi =
        RoutingApi.create(application, application.getString(R.string.routingApiKey))

    private var navigationLocationEngine: LocationEngine
    private val mapMatchedLocationEngine: LocationEngine
    val navigation: TomTomNavigation
    private val state = MutableLiveData<MainViewState>(MainViewState.FreeDriving)
    private val distanceToInstruction = LiveDataEvent<Unit>()
    private val messages = LiveDataEvent<String>()
    private val settingsProvider: SettingsProvider
    private var isNavigationInitialized = false

    init {
        navigationLocationEngine = AndroidLocationEngine(application)
        settingsProvider = SettingsProvider(application)
        val navigationConfiguration = NavigationConfiguration.Builder(
            context = application,
            navigationApiKey = application.getString(R.string.navigationApiKey),
            locationEngine = navigationLocationEngine
        ).build()
        navigation = TomTomNavigation.create(navigationConfiguration)
        mapMatchedLocationEngine = MapMatchedLocationEngine(navigation)
        registerListeners()
    }

    fun state(): LiveData<MainViewState> = state
    fun mapMatchedLocationEngine() = mapMatchedLocationEngine
    fun navigation() = navigation
    fun messages(): LiveData<String> = messages
    fun distanceToInstruction(): LiveData<Unit> = distanceToInstruction

    fun onPermissionsGranted() {
        if (!isNavigationInitialized) {
            startLocationUpdates()
            navigation.start()
            isNavigationInitialized = true
        }
    }

    private fun startLocationUpdates() {
        Timber.d("startLocationUpdates()")
        navigationLocationEngine.enable()
        mapMatchedLocationEngine.enable()
    }

    private fun stopLocationUpdates() {
        Timber.d("stopLocationUpdates()")
        navigationLocationEngine.disable()
        mapMatchedLocationEngine.disable()
    }

    fun planRoute(to: GeoCoordinate) {
        viewModelScope.launch {
            val lastKnownLocation = navigationLocationEngine.lastKnownLocation
            if (lastKnownLocation != null) {
                state.value = MainViewState.Loading
                val planResult = withContext(Dispatchers.IO) {
                    planRoute(origin = lastKnownLocation, destination = to)
                }
                handleRoutingResult(planResult) { route -> startNavigation(route) }
            } else {
                messages.value = "Cannot plan route as there is no starting location."
            }
        }
    }

    private fun updateRoute(currentPosition: GeoLocation, destination: GeoCoordinate) {
        viewModelScope.launch {
            state.value = MainViewState.Loading
            val planResult = withContext(Dispatchers.IO) {
                planRoute(origin = currentPosition, destination = destination)
            }
            handleRoutingResult(planResult) { route ->
                state.value = MainViewState.Navigating(route)
            }
        }
    }

    private fun handleRoutingResult(routingResult: Result<RoutingResult, RoutingException>,
                                    handleRoute : (Route) -> Unit) {
        when (routingResult) {
            is Result.Success -> {
                val firstRoute = routingResult.value().routes.first()
                handleRoute(firstRoute)
            }
            is Result.Failure -> planRouteFailure(routingResult.failure())
        }
    }

    private fun planRouteFailure(throwable: Throwable) {
        state.value = MainViewState.FreeDriving
        messages.value = throwable.message
    }

    private fun startNavigation(route: Route) {
        setLocationEngineForNavigation(route)
        state.value = MainViewState.Navigating(route)
    }

    private fun planRoute(
        origin: GeoLocation,
        destination: GeoCoordinate
    ): Result<RoutingResult, RoutingException> {
        val routingOptions =
            createRoutingOptions(
                origin = origin.position,
                destination = destination,
                vehicleHeading = origin.bearing?.toInt()
            )
        return routingApi.planRoute(routingOptions)
    }

    private fun createRoutingOptions(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        vehicleHeading: Int?
    ): RoutingOptions {
        return RoutingOptions(
            origin = origin,
            destination = destination,
            vehicleHeading = vehicleHeading,
            reportType = ReportType.EFFECTIVE_SETTINGS,
            language = Locale.US,
            instructionsType = InstructionsType.TAGGED,
            instructionPhonetics = InstructionPhoneticsType.IPA,
            instructionAnnouncementPoints = AnnouncementPoints.ALL
        )
    }

    override fun onLocationMatched(location: GeoLocation) {
        Timber.v("Location snapped: $location")
    }

    override fun onLocationContextUpdated(locationContext: LocationContext) {
        Timber.v("Location context updated.")
    }

    override fun onRouteDeviated(location: GeoLocation, route: Route) {
        Timber.d("Route deviated.")
        updateRoute(location, route.endOfRoute)
    }

    override fun onNavigationStarted(routes: List<Route>) {
        Timber.d("Navigation started.")
    }

    override fun onError(exception: NavigationException) {
        Timber.d("Navigation error: $exception")
        messages.value = exception.message
    }

    override fun onDestinationReached() {
        Timber.d("Destination reached.")
    }

    override fun onRouteRefreshRequested(location: GeoLocation, currentRoute: Route) {
        Timber.d("Route refresh requested.")
    }

    override fun onRouteUpdated(route: Route) {
        Timber.d("Route updated.")
    }

    override fun onAnnouncementGenerated(announcement: GuidanceAnnouncement) {
        Timber.d("Announcement generated: $announcement")
    }

    override fun onDistanceToCurrentInstructionChanged(
        distance: Double,
        instructions: List<GuidanceInstruction>
    ) {
        Timber.d("Distance to the current instruction: $distance")
        distanceToInstruction.postValue(Unit)
    }

    override fun onInstructionsChanged(instructions: List<GuidanceInstruction>) {
        Timber.d("Instructions changed: $instructions")
    }

    override fun onCleared() {
        Timber.d("onCleared")
        super.onCleared()
        unregisterListeners()
        stopLocationUpdates()
        navigation.stop()
        navigation.dispose()
    }

    private fun registerListeners() {
        navigation.addOnLocationMatchedListener(this)
        navigation.addOnLocationContextUpdateListener(this)
        navigation.addOnRouteDeviationListener(this)
        navigation.addOnGuidanceUpdateListener(this)
        navigation.addOnNavigationStartedListener(this)
        navigation.addOnNavigationErrorListener(this)
        navigation.addOnDestinationReachedListener(this)
        navigation.addOnRouteRefreshListener(this)
        navigation.addOnRouteUpdatedListener(this)
    }

    private fun unregisterListeners() {
        navigation.removeOnLocationMatchedListener(this)
        navigation.removeOnLocationContextUpdateListener(this)
        navigation.removeOnRouteDeviationListener(this)
        navigation.removeOnGuidanceUpdateListener(this)
        navigation.removeOnNavigationStartedListener(this)
        navigation.removeOnNavigationErrorListener(this)
        navigation.removeOnDestinationReachedListener(this)
        navigation.removeOnRouteRefreshListener(this)
        navigation.removeOnRouteUpdatedListener(this)
    }

    fun navigationStopped() {
        setLocationEngineForFreeDriving()
        state.value = MainViewState.FreeDriving
    }

    private fun setLocationEngineForNavigation(route: Route) {
        navigationLocationEngine.disable()
        navigationLocationEngine = if (settingsProvider.simulateDrive()) {
            SimulationLocationEngine.create(
                InterpolationStrategy(
                    route.legs.flatMap { it.points.map { position -> GeoLocation(position) } },
                    500L, 1000L, 160.0
                )
            )
        } else {
            AndroidLocationEngine(getApplication())
        }
        navigationLocationEngine.enable()
        navigation.setLocationEngine(navigationLocationEngine)
    }

    private fun setLocationEngineForFreeDriving() {
        navigationLocationEngine.disable()
        navigationLocationEngine = AndroidLocationEngine(getApplication())
        navigationLocationEngine.enable()
        navigation.setLocationEngine(navigationLocationEngine)
    }
}
