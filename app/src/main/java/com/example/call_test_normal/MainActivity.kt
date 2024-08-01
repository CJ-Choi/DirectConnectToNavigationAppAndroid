package com.example.call_test_normal

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.call_test_normal.databinding.ActivityMainBinding
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var naverMapClient: NaverMapClient? = null
    private var currentLocation: Location? = null

    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        naverMapClient = NaverMapClient()

        setupSpinner()
        setNaviButton()

        // 위치 권한 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun setupSpinner() {
        val spinner: Spinner = binding.navSpinner
        val options = arrayOf("KaKao Map", "Naver Map")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun setNaviButton() {
        val spinner = binding.navSpinner
        val locationEditText = binding.locationEditText
        val naviButton = binding.naviButton

        naviButton.setOnClickListener { view: View? ->
            val selectedApp = spinner.selectedItem.toString()
            val destination = locationEditText.text.toString().trim()

            if (destination.isEmpty()) {
                Toast.makeText(this@MainActivity, "도착지를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentLocation == null) {
                Toast.makeText(this@MainActivity, "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // API 호출, xy 좌표 획득
            getApiLocation(destination, "", "") { x, y ->
                if (x != null && y != null) {
                    val encodedDestination: String
                    try {
                        encodedDestination = URLEncoder.encode(destination, "UTF-8")
                    } catch (e: UnsupportedEncodingException) {
                        Toast.makeText(this@MainActivity, "도착지 인코딩 오류", Toast.LENGTH_SHORT).show()
                        return@getApiLocation
                    }

                    val startLat = currentLocation?.latitude
                    val startLng = currentLocation?.longitude

                    val url = when (selectedApp) {
                        "KaKao Map" -> {
                            // 카카오맵 호출 URL
                            "kakaomap://route?sp=$startLat,$startLng&ep=$y,$x&by=CAR"
                        }
                        "Naver Map" -> {
                            // 네이버 지도 호출 URL
                            "nmap://route/car?slat=$startLat&slng=$startLng&dlat=$y&dlng=$x&dname=$encodedDestination&appname=com.example.myapp"
                        }
                        else -> {
                            // 예상치 못한 앱 선택
                            Toast.makeText(this@MainActivity, "지도 앱을 선택하세요", Toast.LENGTH_SHORT).show()
                            return@getApiLocation
                        }
                    }

                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    try {
                        // URL Scheme으로 앱 실행
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        // 해당 앱이 설치되어 있지 않은 경우 Play Store로 이동
                        val marketIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                when (selectedApp) {
                                    "KaKao Map" -> "market://details?id=net.daum.android.map"
                                    "Naver Map" -> "market://details?id=com.nhn.android.nmap"
                                    else -> ""
                                }
                            )
                        )
                        startActivity(marketIntent)
                    }
                }
            }
        }
    }

    // 현재 위치를 가져오는 함수
    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                currentLocation = location
                if (location != null) {
                    Log.d("MainActivity", "Current Location: Latitude ${location.latitude}, Longitude ${location.longitude}")
                } else {
                    Toast.makeText(this, "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Naver Maps API를 이용해 목적지의 x, y 좌표 변환
    private fun getApiLocation(address: String, apiKeyId: String, apiKey: String, callback: (String?, String?) -> Unit) {
        lifecycleScope.launch {
            try {
                naverMapClient!!.getCoordinates(
                    address,
                    apiKeyId,
                    apiKey,
                    object : Callback<GeocodeResponse?> {
                        override fun onResponse(
                            call: Call<GeocodeResponse?>?,
                            response: Response<GeocodeResponse?>
                        ) {
                            if (response.isSuccessful && response.body() != null) {
                                val geocodeResponse = response.body()!!
                                val addresses: List<GeocodeResponse.Address> = geocodeResponse.addresses

                                if (addresses.isNotEmpty()) {
                                    val address = addresses[0]
                                    val x = address.x
                                    val y = address.y

                                    // 주소와 좌표를 사용
                                    Log.d("MainActivity", "Address: ${address.roadAddress}")
                                    Log.d("MainActivity", "X: $x, Y: $y")

                                    // 콜백 호출
                                    callback(x, y)
                                } else {
                                    Log.e("MainActivity", "No addresses found in the response")
                                    callback(null, null)
                                }
                            } else {
                                Log.e("MainActivity", "API response error: ${response.message()}")
                                callback(null, null)
                            }
                        }

                        override fun onFailure(call: Call<GeocodeResponse?>?, t: Throwable?) {
                            // 오류 처리
                            Log.e("MainActivity", "API call failed", t)
                            callback(null, null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.w("MainActivity", e.toString())
                callback(null, null)
            }
        }
    }
}
