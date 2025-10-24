package com.example.proyectoelectivai.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Módulo de configuración de red
 * Maneja Retrofit, OkHttp y detección de conectividad
 */
object NetworkModule {
    
    private const val CACHE_SIZE = 10 * 1024 * 1024L // 10 MB
    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L
    
    /**
     * Crea el servicio de Overpass API para lugares turísticos
     */
    fun createOverpassService(context: Context): OverpassApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://overpass-api.de/api/")
            .client(createOkHttpClient(context, timeoutSeconds = 60)) // Mayor timeout para Overpass
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(OverpassApiService::class.java)
    }
    
    private fun createOkHttpClient(context: Context, timeoutSeconds: Long = CONNECT_TIMEOUT): OkHttpClient {
        val cache = Cache(
            File(context.cacheDir, "http_cache"),
            CACHE_SIZE
        )
        
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        // Interceptor para User-Agent
        val userAgentInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "ProyectoElectivaI/1.0 (Android App)")
                .build()
            chain.proceed(request)
        }
        
        val offlineInterceptor = Interceptor { chain ->
            var request = chain.request()
            
            if (!isNetworkAvailable(context)) {
                // Usar caché cuando no hay red
                request = request.newBuilder()
                    .header("Cache-Control", "public, only-if-cached, max-stale=86400")
                    .build()
            } else {
                // Usar red cuando está disponible
                request = request.newBuilder()
                    .header("Cache-Control", "public, max-age=3600")
                    .build()
            }
            
            chain.proceed(request)
        }
        
        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(offlineInterceptor)
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
    }
    
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
