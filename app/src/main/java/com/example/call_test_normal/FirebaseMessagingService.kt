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
    }

    private fun sendNotification(title: String?, message: String?) {
        val intent = Intent("com.example.NOTIFICATION_RECEIVED").apply {
            putExtra("title", title)
            putExtra("message", message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
    }

}