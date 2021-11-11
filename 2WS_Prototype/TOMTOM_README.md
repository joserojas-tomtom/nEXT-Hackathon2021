GO SDK Sample App
=================

This sample app shows you how easy it is to build a simple turn-by-turn navigation app using GO SDK!
The code implements a standard map display, a simple route planner and the ability to navigate. To use the sample app, simply select a destination by _long-clicking_ anywhere on the map display, the route planner will then plan a route to the selected destination and turn-by-turn navigation will be started.

API Keys configuration
----------------------
To compile and use the sample app you must first add your TomTom API keys. These keys can be generated from you developer account at https://developer.tomtom.com.

- Add the TomTom Maps API key to `local.properties` or your global gradle properties:
```
routingApiKey=yourKey
navigationApiKey=yourKey
mapsApiKey=yourKey
```

Configuring artifacts
---------------------

Before building, you must first provide your credentials for our maven repository by replacing the username/password placeholders in the main `build.gradle` file.
```
maven {
    credentials {
        username = nexusUsername
        password = nexusPassword
    }
    url = uri("https://dl.tomtom.com:8443/nexus/content/repositories/go-sdk-android/")
}
```

Building and installing
-----------------------
You can install the app from Android Studio by pressing the run button that is found next to the device details in the toolbar or by entering the following command into the command line from your project root directory:

 * `./gradlew installDebug`

Location updates
----------------

_Simulation_
------------
By default, the navigation engine will use real gps information for turn-by-turn navigation and forward the determined vehicle position, which has been matched (aligned) to the road network, to the map display.
In settings (3 dots button) we can enable simulated location engine for position updates. The vehicle position marker (chevron) will automatically follow the planned route until it reaches the destination. This is great for testing the app and navigation functionality without doing a real drive test in a vehicle.



