package com.octaloop.technologies.manager

import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NgrokOkHttpClient {
    
    fun createClient(): OkHttpClient {
        return try {
            // Create a trust manager that accepts all certificates
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory

            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val originalRequest: Request = chain.request()
                    val requestWithHeaders = originalRequest.newBuilder()
                        .header("ngrok-skip-browser-warning", "true")
                        .header("User-Agent", "Android-MDM-Client/1.0")
                        .header("Accept", "*/*")
                        .header("Connection", "keep-alive")
                        .build()
                    chain.proceed(requestWithHeaders)
                }
                .build()
        } catch (e: Exception) {
            android.util.Log.e("NgrokOkHttpClient", "Error creating OkHttpClient: ${e.message}")
            // Fallback to default client
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
