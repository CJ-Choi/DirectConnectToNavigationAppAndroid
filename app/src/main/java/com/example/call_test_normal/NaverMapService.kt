package com.example.call_test_normal

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query


interface NaverMapService {
    @GET("map-geocode/v2/geocode")
    fun getGeocode(
        @Header("X-NCP-APIGW-API-KEY-ID") apiKeyId: String?,
        @Header("X-NCP-APIGW-API-KEY") apiKey: String?,
        @Query("query") address: String?
    ): Call<GeocodeResponse?>?
}
