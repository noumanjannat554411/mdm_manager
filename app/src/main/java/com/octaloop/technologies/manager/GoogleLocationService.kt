package com.octaloop.technologies.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GoogleLocationService(private val context: Context) {
    
    private val tag = "GoogleLocationService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Get location using IP-based geolocation (works indoors/anywhere with internet)
     */
    fun getLocationByIP(callback: (LocationManager.LocationData?) -> Unit) {
        println("GoogleLocationService:-🌐 Getting location by IP...")
        
        scope.launch {
            try {
                val locationData = getLocationFromIPAPI()
                withContext(Dispatchers.Main) {
                    callback(locationData)
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Error getting IP location: ${e.message}")
                println("GoogleLocationService:-❌ Error getting IP location: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }
    
    /**
     * Get location using multiple IP geolocation services
     */
    private suspend fun getLocationFromIPAPI(): LocationManager.LocationData? {
        val services = listOf(
            "http://ip-api.com/json/?fields=status,message,country,regionName,city,lat,lon,timezone,isp",
            "https://ipapi.co/json/",
            "https://ipinfo.io/json"
        )
        
        for (serviceUrl in services) {
            try {
                println("GoogleLocationService:-🌐 Trying service: $serviceUrl")
                val result = makeHttpRequest(serviceUrl)
                val locationData = parseLocationResponse(result, serviceUrl)
                
                if (locationData != null) {
                    println("GoogleLocationService:-✅ Got location from ${serviceUrl.split("/")[2]}: ${locationData.latitude}, ${locationData.longitude}")
                    return locationData
                }
            } catch (e: Exception) {
                println("GoogleLocationService:-⚠️ Service ${serviceUrl.split("/")[2]} failed: ${e.message}")
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
            connection.connectTimeout = 10000 // 10 seconds
            connection.readTimeout = 10000 // 10 seconds
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
    private fun parseLocationResponse(response: String, serviceUrl: String): LocationManager.LocationData? {
        try {
            val json = JSONObject(response)
            
            return when {
                serviceUrl.contains("ip-api.com") -> {
                    if (json.getString("status") == "success") {
                        LocationManager.LocationData(
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
                        LocationManager.LocationData(
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
                            LocationManager.LocationData(
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
            println("GoogleLocationService:-❌ Error parsing response: ${e.message}")
            return null
        }
    }
    
    /**
     * Get location using Google's Geolocation API (requires API key)
     */
    fun getLocationByGoogleAPI(apiKey: String, callback: (LocationManager.LocationData?) -> Unit) {
        if (apiKey.isEmpty()) {
            println("GoogleLocationService:-⚠️ No Google API key provided")
            callback(null)
            return
        }
        
        scope.launch {
            try {
                val locationData = getLocationFromGoogleAPI(apiKey)
                withContext(Dispatchers.Main) {
                    callback(locationData)
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Error getting Google API location: ${e.message}")
                println("GoogleLocationService:-❌ Error getting Google API location: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }
    
    /**
     * Use Google's Geolocation API
     */
    private suspend fun getLocationFromGoogleAPI(apiKey: String): LocationManager.LocationData? {
        val url = "https://www.googleapis.com/geolocation/v1/geolocate?key=$apiKey"
        
        try {
            println("GoogleLocationService:-🔍 Using Google Geolocation API...")
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            // Send request body for better accuracy
            val requestBody = JSONObject().apply {
                put("considerIp", true)
                put("radioType", "gsm")
            }
            
            connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                
                if (json.has("location")) {
                    val location = json.getJSONObject("location")
                    val accuracy = json.optDouble("accuracy", 1000.0)
                    
                    return LocationManager.LocationData(
                        latitude = location.getDouble("lat"),
                        longitude = location.getDouble("lng"),
                        accuracy = accuracy,
                        timestamp = System.currentTimeMillis(),
                        provider = "google_geolocation_api",
                        address = "Google API Location"
                    )
                }
            } else {
                println("GoogleLocationService:-❌ Google API error: HTTP $responseCode")
            }
            
        } catch (e: Exception) {
            println("GoogleLocationService:-❌ Google API request failed: ${e.message}")
        }
        
        return null
    }
    
    fun cleanup() {
        scope.cancel()
        Log.d(tag, "🧹 GoogleLocationService cleaned up")
    }
}
