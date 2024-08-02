package com.example.call_test_normal

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.call_test_normal.databinding.ActivityMainBinding
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
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

    // 토큰 브로드캐스트 리시버
    private val tokenReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val token = intent?.getStringExtra("data") ?: return
            val driverNo = intent.getIntExtra("driverNo", -1)

            Log.d("MainActivity", "FCM 토큰 수신: $token, Driver No: $driverNo")

            // 토큰 보내기, 네트워크 작업이므로 코루틴 처리
            lifecycleScope.launch {
                sendTokenToServer(token, driverNo)
            }
        }
    }

    // FCM 메세지 브로드캐스트 리시버, 메세지 수신 후 알림 다이얼로그 팝업
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val title = intent.getStringExtra("title")
            val message = intent.getStringExtra("message")
            Log.d("MainActivity - notificationReceiver", "message : $message")
            showAlertDialog(title, message)
        }
    }

    //생명 주기 - 액티비티 생성 시
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FirebaseApp.initializeApp(this)

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

        // FCM Setting
        setupFirebaseMessaging()

        // 토큰 전송 브로드캐스트 리시버 등록
        LocalBroadcastManager.getInstance(this).registerReceiver(
            tokenReceiver, IntentFilter("com.example.broadcast.TOKEN_BROADCAST")
        )
        // 메세지 전송 브로드캐스트 리시버 등록
        LocalBroadcastManager.getInstance(this).registerReceiver(
            notificationReceiver, IntentFilter("com.example.NOTIFICATION_RECEIVED")
        )
    }

    // 생명 주기 - 액티비티 파괴 시
    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tokenReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
    }

    //콜 메세지 수신 시 알림 다이얼로그 팝업, 수락 -> 내비 앱 실행, 거절 -> 팝업 해제
    private fun showAlertDialog(title: String?, message: String?) {
        val dialogView = layoutInflater.inflate(R.layout.custom_dialog, null)

        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val positiveButton = dialogView.findViewById<Button>(R.id.acceptButton)
        val negativeButton = dialogView.findViewById<Button>(R.id.rejectButton)

        dialogTitle.text = title
        dialogMessage.text = message

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        positiveButton.setOnClickListener {
            val spinner = binding.navSpinner

            val selectedApp = spinner.selectedItem.toString()
            val destination = message.toString().trim()

            if (!validateInputs(destination)) return@setOnClickListener
            if (!checkCurrentLocation()) return@setOnClickListener

            getApiLocation(destination, "", "") { x, y ->
                if (x != null && y != null) {
                    val encodedDestination = encodeDestination(destination) ?: return@getApiLocation
                    val url = generateNavigationUrl(selectedApp, encodedDestination, x, y) ?: return@getApiLocation
                    launchNavigationApp(url, selectedApp)
                }
            }
            alertDialog.dismiss()
        }

        negativeButton.setOnClickListener {
            // 거절 버튼 클릭 시 다이얼로그 닫기
            alertDialog.dismiss()
            // TODO 기사가 콜을 거절했음을 서버에 알려야 함
        }

        alertDialog.show()
    }


    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFirebaseMessaging() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result
                Log.d("MainActivity", "FCM registration token: $token")

                // 브로드캐스트 송신 - token
                Intent().also { intent ->
                    intent.action = "com.example.broadcast.TOKEN_BROADCAST"
                    intent.putExtra("data", token)
                    intent.putExtra("driverNo", 12345) // 예제 driverNo 설정
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                }
            }
    }

    private suspend fun sendTokenToServer(token: String, driverNo: Int) {
        val retrofit = TokenInstance.api
        val tokenRequest = TokenRequest(token, driverNo)

        try {
            val response = retrofit.sendToken(tokenRequest)
            Log.d("MainActivity", "Token sent successfully: ${response.message}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Exception in sending token: $e")
        }
    }


    private fun setupSpinner() {
        val spinner: Spinner = binding.navSpinner
        val options = arrayOf("KaKao Map", "Naver Map", "T-map")
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

            if (!validateInputs(destination)) return@setOnClickListener
            if (!checkCurrentLocation()) return@setOnClickListener

            getApiLocation(destination, "", "") { x, y ->
                if (x != null && y != null) {
                    val encodedDestination = encodeDestination(destination) ?: return@getApiLocation
                    val url = generateNavigationUrl(selectedApp, encodedDestination, x, y) ?: return@getApiLocation
                    launchNavigationApp(url, selectedApp)
                }
            }
        }
    }

    private fun validateInputs(destination: String): Boolean {
        if (destination.isEmpty()) {
            Toast.makeText(this@MainActivity, "도착지를 입력하세요", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun checkCurrentLocation(): Boolean {
        if (currentLocation == null) {
            Toast.makeText(this@MainActivity, "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    // 도착지 값을 문자열 -> UTF-8로 변환
    private fun encodeDestination(destination: String): String? {
        return try {
            URLEncoder.encode(destination, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            Toast.makeText(this@MainActivity, "도착지 인코딩 오류", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // API로 받은 값을 이용해 URL Scheme 설정
    private fun generateNavigationUrl(selectedApp: String, encodedDestination: String, x: String?, y: String?): String? {
        val startLat = currentLocation?.latitude
        val startLng = currentLocation?.longitude

        return when (selectedApp) {
            "KaKao Map" -> "kakaomap://route?sp=$startLat,$startLng&ep=$y,$x&by=CAR"
            "Naver Map" -> "nmap://route/car?slat=$startLat&slng=$startLng&dlat=$y&dlng=$x&dname=$encodedDestination&appname=com.example.myapp"
            "T-map" -> "tmap://route?startx=$startLng&starty=$startLat&goalx=$x&goaly=$y&reqCoordType=WGS84&resCoordType=WGS84"
            else -> {
                Toast.makeText(this@MainActivity, "지도 앱을 선택하세요", Toast.LENGTH_SHORT).show()
                null
            }
        }
    }

    // 사용자 기기에 내비게이션 앱이 없을 경우 마켓으로 이동
    private fun launchNavigationApp(url: String, selectedApp: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val marketUri = when (selectedApp) {
                "KaKao Map" -> "market://details?id=net.daum.android.map"
                "Naver Map" -> "market://details?id=com.nhn.android.nmap"
                "T-map" -> "market://details?id=com.skt.tmap.ku"
                else -> ""
            }
            if (marketUri.isNotEmpty()) {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse(marketUri))
                startActivity(marketIntent)
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