package com.example.call_test_normal
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TokenInstance {
    private const val BASE_URL = "https://ji18hys72bu5ie-3030.proxy.runpod.net/" // 에뮬레이터의 로컬 서버 주소

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}