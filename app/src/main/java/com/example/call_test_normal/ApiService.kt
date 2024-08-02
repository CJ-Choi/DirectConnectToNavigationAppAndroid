package com.example.call_test_normal

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("location")
    fun sendLocation(@Body request: LocationRequest): Call<ApiResponse>

    @POST("sendToken")
    suspend fun sendToken(@Body request: TokenRequest): TokenResponse
}

data class LocationRequest(val lat: Float, val lng: Float, val time: String)
data class ApiResponse(val success: Boolean, val message: String)
data class TokenRequest(val token: String, val driverNo: Int)
data class TokenResponse(val success: Boolean, val message: String)



