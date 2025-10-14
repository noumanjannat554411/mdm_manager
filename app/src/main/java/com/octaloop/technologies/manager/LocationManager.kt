package com.octaloop.technologies.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class LocationManager(private val context: Context) {
    
    private val tag = "LocationManager"
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var isLocationUpdatesActive = false
    private var locationUpdateListener: ((LocationData) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Location data class
    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Double,
        val timestamp: Long,
        val provider: String,
        val address: String? = null
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("latitude", latitude)
                put("longitude", longitude)
                put("accuracy", accuracy)
                put("timestamp", timestamp)
                put("provider", provider)
                put("address", address ?: "")
                put("formatted_address", "$latitude, $longitude")
            }
        }
    }
    
    init {
        initializeLocationServices()
    }
    
    private fun initializeLocationServices() {
        try {
            // Check Google Play Services availability
            val googlePlayServicesAvailable = try {
                val gms = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                val resultCode = gms.isGooglePlayServicesAvailable(context)
                val available = resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS
                println("LocationManager:-📍 Google Play Services available: $available (code: $resultCode)")
                available
            } catch (e: Exception) {
                println("LocationManager:-❌ Error checking Google Play Services: ${e.message}")
                false
            }
            
            if (googlePlayServicesAvailable) {
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                Log.d(tag, "📍 Location services initialized with Google Play Services")
                println("LocationManager:-📍 FusedLocationClient initialized successfully")
            } else {
                Log.w(tag, "⚠️ Google Play Services not available, will use fallback only")
                println("LocationManager:-⚠️ Google Play Services not available, will use fallback only")
                fusedLocationClient = null
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error initializing location services: ${e.message}")
            println("LocationManager:-❌ Error initializing location services: ${e.message}")
            fusedLocationClient = null
        }
    }
    
    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermissions(): Boolean {
        val fineLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        println("LocationManager:-📍 Fine location permission: $fineLocation")
        println("LocationManager:-📍 Coarse location permission: $coarseLocation")
        
        return fineLocation && coarseLocation
    }
    
    /**
     * Get current location with IP geolocation fallback
     */
    fun getCurrentLocation(callback: (LocationData?) -> Unit) {
        println("LocationManager:-📍 getCurrentLocation called")
        
        if (!hasLocationPermissions()) {
            Log.w(tag, "⚠️ Location permissions not granted, trying IP location")
            println("LocationManager:-⚠️ Location permissions not granted, trying IP location")
            getLocationByIP(callback)
            return
        }
        
        // Try GPS first (fastest if available)
        getLocationFromGPS { gpsLocation ->
            if (gpsLocation != null) {
                println("LocationManager:-✅ Got GPS location: ${gpsLocation.latitude}, ${gpsLocation.longitude}")
                callback(gpsLocation)
            } else {
                println("LocationManager:-🌐 GPS failed, trying IP location...")
                getLocationByIP(callback)
            }
        }
    }
    
    /**
     * Get location from GPS/Network providers with short timeout
     */
    private fun getLocationFromGPS(callback: (LocationData?) -> Unit) {
        if (!isLocationEnabled()) {
            Log.w(tag, "⚠️ Location services disabled")
            println("LocationManager:-⚠️ Location services disabled")
            callback(null)
            return
        }
        
        if (fusedLocationClient == null) {
            Log.e(tag, "❌ FusedLocationClient not initialized")
            println("LocationManager:-❌ FusedLocationClient not initialized")
            callback(null)
            return
        }
        
        try {
            Log.d(tag, "📍 Requesting GPS location...")
            println("LocationManager:-📍 Requesting GPS location...")
            
            var isCallbackCalled = false
            val cancellationToken = CancellationTokenSource()
            
            // Use balanced priority for faster indoor positioning
            val priority = try {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            } catch (e: Exception) {
                LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            }
            
            println("LocationManager:-📍 Using balanced priority for indoor positioning")
            
            // Very short timeout for GPS - if it doesn't work quickly, fallback to IP
            val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (!isCallbackCalled) {
                    println("LocationManager:-⏰ GPS timeout (2s), using IP location")
                    isCallbackCalled = true
                    cancellationToken.cancel()
                    callback(null) // Let IP location handle it
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, 2000) // Only 2 seconds for GPS
            
            fusedLocationClient?.getCurrentLocation(priority, cancellationToken.token)
                ?.addOnSuccessListener { location: Location? ->
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    if (!isCallbackCalled) {
                        isCallbackCalled = true
                        if (location != null) {
                            val locationData = LocationData(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = location.accuracy.toDouble(),
                                timestamp = location.time,
                                provider = "fused_gps"
                            )
                            println("LocationManager:-✅ GPS location: ${locationData.latitude}, ${locationData.longitude}")
                            callback(locationData)
                        } else {
                            println("LocationManager:-⚠️ GPS returned null")
                            callback(null)
                        }
                    }
                }?.addOnFailureListener { exception ->
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    if (!isCallbackCalled) {
                        isCallbackCalled = true
                        println("LocationManager:-❌ GPS failed: ${exception.message}")
                        callback(null)
                    }
                }
                
        } catch (e: Exception) {
            println("LocationManager:-❌ Error in GPS request: ${e.message}")
            callback(null)
        }
    }
    
    /**
     * Get location using IP-based geolocation (works anywhere with internet)
     */
    private fun getLocationByIP(callback: (LocationData?) -> Unit) {
        println("LocationManager:-🌐 Getting location by IP...")
        
        scope.launch {
            try {
                val locationData = getLocationFromIPAPI()
                withContext(Dispatchers.Main) {
                    if (locationData != null) {
                        println("LocationManager:-✅ Got IP location: ${locationData.latitude}, ${locationData.longitude}")
                        callback(locationData)
                    } else {
                        println("LocationManager:-❌ IP location failed, using mock location")
                        provideMockLocationForTesting(callback)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Error getting IP location: ${e.message}")
                println("LocationManager:-❌ Error getting IP location: ${e.message}")
                withContext(Dispatchers.Main) {
                    provideMockLocationForTesting(callback)
                }
            }
        }
    }
    
    /**
     * Get location using IP geolocation services
     */
    private suspend fun getLocationFromIPAPI(): LocationData? {
        val services = listOf(
            "http://ip-api.com/json/?fields=status,lat,lon,city,regionName,country",
            "https://ipapi.co/json/",
            "https://ipinfo.io/json"
        )
        
        for (serviceUrl in services) {
            try {
                println("LocationManager:-🌐 Trying service: ${serviceUrl.split("/")[2]}")
                val result = makeHttpRequest(serviceUrl)
                val locationData = parseLocationResponse(result, serviceUrl)
                
                if (locationData != null) {
                    println("LocationManager:-✅ Got location from ${serviceUrl.split("/")[2]}: ${locationData.latitude}, ${locationData.longitude}")
                    return locationData
                }
            } catch (e: Exception) {
                println("LocationManager:-⚠️ Service ${serviceUrl.split("/")[2]} failed: ${e.message}")
                continue
            }
        }
        
        return null
    }
    
    /**
     * Make HTTP request to get location data
     */
    private suspend fun makeHttpRequest(urlString: String): String = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000 // 5 seconds
            connection.readTimeout = 5000 // 5 seconds
            connection.setRequestProperty("User-Agent", "MDM-Manager/1.0")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                return@withContext response
            } else {
                throw Exception("HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Parse location response from different APIs
     */
    private fun parseLocationResponse(response: String, serviceUrl: String): LocationData? {
        try {
            val json = JSONObject(response)
            
            return when {
                serviceUrl.contains("ip-api.com") -> {
                    if (json.getString("status") == "success") {
                        LocationData(
                            latitude = json.getDouble("lat"),
                            longitude = json.getDouble("lon"),
                            accuracy = 1000.0, // IP-based accuracy is typically ~1km
                            timestamp = System.currentTimeMillis(),
                            provider = "ip-api.com",
                            address = "${json.optString("city")}, ${json.optString("regionName")}, ${json.optString("country")}"
                        )
                    } else null
                }
                serviceUrl.contains("ipapi.co") -> {
                    if (json.has("latitude") && json.has("longitude")) {
                        LocationData(
                            latitude = json.getDouble("latitude"),
                            longitude = json.getDouble("longitude"),
                            accuracy = 1000.0,
                            timestamp = System.currentTimeMillis(),
                            provider = "ipapi.co",
                            address = "${json.optString("city")}, ${json.optString("region")}, ${json.optString("country_name")}"
                        )
                    } else null
                }
                serviceUrl.contains("ipinfo.io") -> {
                    if (json.has("loc")) {
                        val loc = json.getString("loc").split(",")
                        if (loc.size == 2) {
                            LocationData(
                                latitude = loc[0].toDouble(),
                                longitude = loc[1].toDouble(),
                                accuracy = 1000.0,
                                timestamp = System.currentTimeMillis(),
                                provider = "ipinfo.io",
                                address = "${json.optString("city")}, ${json.optString("region")}, ${json.optString("country")}"
                            )
                        } else null
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error parsing response: ${e.message}")
            println("LocationManager:-❌ Error parsing response: ${e.message}")
            return null
        }
    }
    
    /**
     * Provide mock location as last resort
     */
    private fun provideMockLocationForTesting(callback: (LocationData?) -> Unit) {
        println("LocationManager:-🧪 Providing mock location for testing...")
        
        val mockLocationData = LocationData(
            latitude = 37.7749, // San Francisco coordinates
            longitude = -122.4194,
            accuracy = 100.0,
            timestamp = System.currentTimeMillis(),
            provider = "mock_for_testing"
        )
        
        println("LocationManager:-🧪 Mock location: ${mockLocationData.latitude}, ${mockLocationData.longitude}")
        callback(mockLocationData)
    }
    
    /**
     * Start continuous location updates
     */
    fun startLocationUpdates(updateListener: (LocationData) -> Unit) {
        if (!hasLocationPermissions()) {
            Log.w(tag, "⚠️ Cannot start location updates - permissions not granted")
            return
        }
        
        if (isLocationUpdatesActive) {
            Log.d(tag, "📍 Location updates already active")
            return
        }
        
        locationUpdateListener = updateListener
        
        try {
            val locationRequest = LocationRequest.create().apply {
                interval = 10000 // 10 seconds (faster updates)
                fastestInterval = 5000 // 5 seconds minimum
                priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY // Better for indoor
                smallestDisplacement = 5f // 5 meters (more sensitive)
            }
            
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val locationData = LocationData(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy.toDouble(),
                            timestamp = location.time,
                            provider = location.provider ?: "fused"
                        )
                        Log.d(tag, "📍 Location update: ${locationData.latitude}, ${locationData.longitude}")
                        updateListener(locationData)
                    }
                }
                
                override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                    Log.d(tag, "📍 Location availability: ${locationAvailability.isLocationAvailable}")
                    if (!locationAvailability.isLocationAvailable) {
                        // If GPS becomes unavailable, get IP location
                        getLocationByIP { ipLocation ->
                            ipLocation?.let { updateListener(it) }
                        }
                    }
                }
            }
            
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            
            isLocationUpdatesActive = true
            Log.d(tag, "✅ Location updates started")
            
        } catch (e: Exception) {
            Log.e(tag, "❌ Error starting location updates: ${e.message}")
        }
    }
    
    /**
     * Stop location updates
     */
    fun stopLocationUpdates() {
        if (!isLocationUpdatesActive) {
            Log.d(tag, "📍 Location updates not active")
            return
        }
        
        try {
            locationCallback?.let { callback ->
                fusedLocationClient?.removeLocationUpdates(callback)
            }
            isLocationUpdatesActive = false
            locationCallback = null
            locationUpdateListener = null
            Log.d(tag, "🛑 Location updates stopped")
        } catch (e: Exception) {
            Log.e(tag, "❌ Error stopping location updates: ${e.message}")
        }
    }
    
    /**
     * Check if location services are enabled
     */
    fun isLocationEnabled(): Boolean {
        val androidLocationManager = context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
        val gpsEnabled = androidLocationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)
        val networkEnabled = androidLocationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
        
        println("LocationManager:-📍 Location services - GPS: $gpsEnabled, Network: $networkEnabled")
        
        return gpsEnabled || networkEnabled
    }
    
    /**
     * Get location status information
     */
    fun getLocationStatus(): JSONObject {
        return JSONObject().apply {
            put("permissions_granted", hasLocationPermissions())
            put("location_enabled", isLocationEnabled())
            put("updates_active", isLocationUpdatesActive)
            put("provider_gps", context.getSystemService(Context.LOCATION_SERVICE)
                .let { it as AndroidLocationManager }
                .isProviderEnabled(AndroidLocationManager.GPS_PROVIDER))
            put("provider_network", context.getSystemService(Context.LOCATION_SERVICE)
                .let { it as AndroidLocationManager }
                .isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER))
            put("google_services_available", fusedLocationClient != null)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopLocationUpdates()
        scope.cancel()
        fusedLocationClient = null
        Log.d(tag, "🧹 LocationManager cleaned up")
    }
}
