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
    
    fun createOpenAQService(context: Context): ApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openaq.org/")
            .client(createOkHttpClient(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(ApiService::class.java)
    }
    
    fun createOverpassService(context: Context): ApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://overpass-api.de/")
            .client(createOkHttpClient(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(ApiService::class.java)
    }
    
    fun createGeocodingService(context: Context): GeocodingService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(createOkHttpClient(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(GeocodingService::class.java)
    }
    
    private fun createOkHttpClient(context: Context): OkHttpClient {
        val cache = Cache(
            File(context.cacheDir, "http_cache"),
            CACHE_SIZE
        )
        
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        // Interceptor para User-Agent (OBLIGATORIO para Nominatim)
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
            .addInterceptor(userAgentInterceptor) // CRÍTICO: User-Agent para Nominatim
            .addInterceptor(loggingInterceptor)
            .addInterceptor(offlineInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
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
