package com.example.call_test_normal

import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class NaverMapClient {
    private val naverMapService: NaverMapService

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        naverMapService = retrofit.create(NaverMapService::class.java)
    }

    fun getCoordinates(
        address: String?,
        apiKeyId: String?,
        apiKey: String?,
        callback: Callback<GeocodeResponse?>?
    ) {
        val call = naverMapService.getGeocode(apiKeyId, apiKey, address)
        call!!.enqueue(callback)
    }

    companion object {
        private const val BASE_URL = "https://naveropenapi.apigw.ntruss.com/"
    }
}
