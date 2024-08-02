package com.example.call_test_normal

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("onMessageReceived", "Message Received : 1")
        remoteMessage.notification?.let {
            sendNotification(it.title, it.body)
        }

        Log.d("onMessageReceived", "Message Received : 2")

        //TODO 이거 하고 있었음 - 메세지 ID, 메세지 내용, 메세지 받은 시간 객체 할당해 내부 DB에 저장하기
        // TODO 현재는 ID, 메세지, 시간 값이지만 추후 콜 정보로 수정 예정
        // 메시지 보낸 시간 참조
        val sentTime = remoteMessage.sentTime


        Log.d("onMessageReceived", "Sent Time : $sentTime")

        // 타임스탬프를 읽기 쉬운 형식으로 변환 (예: Date 객체 사용)
        val sentDate: Date = Date(sentTime)
        val sdf: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate: String = sdf.format(sentDate)
        Log.d("onMessageReceived", "formattedDate : $formattedDate")

        Log.d("FCM", sdf.toString())
        Log.d("FCM", remoteMessage.sentTime.toString())

        // FCM 메시지 저장
        val messageId = remoteMessage.messageId ?: return
        val messageBody = remoteMessage.notification?.body ?: return
        Log.d("onMessageReceived", "messageBody : $messageBody")

        val dbHelper = FirebaseMessageStorageHelper(applicationContext)
        dbHelper.setFcmData(messageId, messageBody, formattedDate)
        Log.d("onMessageReceived", "setFcmData : $messageId $messageBody $formattedDate ")
    }

    private fun sendNotification(title: String?, message: String?) {
        // TODO 무조건 MainActivity로 가지 말고 SQLite 로그인 정보와 대조하고 Intent 처리할 것
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("title", title)
            putExtra("message", message)
        }
        startActivity(intent)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")

        // 토큰을 새로운 테이블에 저장
        val dbHelper = FirebaseMessageStorageHelper(applicationContext)
        dbHelper.setTokenData(token)

        // 브로드캐스트 송신
        val broadcastIntent = Intent().also {
            it.action = "com.example.broadcast.TOKEN_BROADCAST"
            it.putExtra("data", token)
            // example로 driverNo를 12345로 설정합니다. 필요에 따라 동적 할당할 수 있습니다.
            it.putExtra("driverNo", 12345)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)

        // TODO Firestore 혹은 Retrofit API 자동 토큰 전송 및 등록
    }

}