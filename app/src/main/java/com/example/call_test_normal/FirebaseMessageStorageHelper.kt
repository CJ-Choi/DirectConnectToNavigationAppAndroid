package com.example.call_test_normal


import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class FirebaseMessageStorageHelper(context : Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "FCM_STORAGE"
        const val TABLE_NAME = "fcm_storage"
        const val TOKEN_TABLE_NAME = "fcm_token_storage"
        const val MESSAGE_ID = "id"
        const val MESSAGE_TEXT = "message_text"
        const val MESSAGE_TIME = "received_time"
        const val TOKEN = "token"
        const val DRIVER_NO = "driver_no"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_FCM_TABLE = """
            CREATE TABLE $TABLE_NAME(
                $MESSAGE_ID TEXT PRIMARY KEY,
                $MESSAGE_TEXT TEXT,
                $MESSAGE_TIME TEXT
            )
        """.trimIndent()
        db.execSQL(CREATE_FCM_TABLE)

        val CREATE_TOKEN_TABLE = """
            CREATE TABLE $TOKEN_TABLE_NAME(
                $TOKEN TEXT PRIMARY KEY,
                $DRIVER_NO TEXT
            )
        """.trimIndent()
        db.execSQL(CREATE_TOKEN_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        db.execSQL("DROP TABLE IF EXISTS $TOKEN_TABLE_NAME")
        onCreate(db)
    }

    fun setFcmData(id: String, message: String, time: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(MESSAGE_ID, id)
            put(MESSAGE_TEXT, message)
            put(MESSAGE_TIME, time)
        }
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun setTokenData(token: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(TOKEN, token)
            put(DRIVER_NO, null as String?)
        }

        try {
            db.insertWithOnConflict(TOKEN_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            Log.d("FirebaseMessageStorageHelper", "Token inserted successfully: $token")
        } catch (e: Exception) {
            Log.e("FirebaseMessageStorageHelper", "Error inserting token: ${e.message}")
        } finally {
            db.close()
        }
    }

    fun getFcmMessageList(): ArrayList<FcmMessage>? {
        val messageList = ArrayList<FcmMessage>()
        val db = this.readableDatabase
        val selectQuery = "SELECT * FROM $TABLE_NAME ORDER BY $MESSAGE_TIME DESC"

        db.rawQuery(selectQuery, null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val idIndex = cursor.getColumnIndex(MESSAGE_ID)
                    val messageIndex = cursor.getColumnIndex(MESSAGE_TEXT)
                    val timeIndex = cursor.getColumnIndex(MESSAGE_TIME)

                    if (idIndex != -1 && messageIndex != -1 && timeIndex != -1) {
                        val fcmMessage = FcmMessage(
                            id = cursor.getString(idIndex).toIntOrNull() ?: 0,
                            message = cursor.getString(messageIndex),
                            time = cursor.getString(timeIndex)
                        )
                        messageList.add(fcmMessage)
                    }
                } while (cursor.moveToNext())
            }
        }

        db.close()
        return if (messageList.isNotEmpty()) messageList else null
    }

    fun getCurrentFcmMessage(): FcmMessage? {
        val db = this.readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, null, null)
        var fcmMessage: FcmMessage? = null
        if (cursor.moveToFirst()) {
            val idIndex = cursor.getColumnIndex(MESSAGE_ID)
            val messageIndex = cursor.getColumnIndex(MESSAGE_TEXT)
            val timeIndex = cursor.getColumnIndex(MESSAGE_TIME)

            if (idIndex != -1 && messageIndex != -1 && timeIndex != -1) {
                fcmMessage = FcmMessage(
                    id = cursor.getInt(idIndex),
                    message = cursor.getString(messageIndex),
                    time = cursor.getString(timeIndex)
                )
            }
        }
        cursor.close()
        db.close()
        return fcmMessage
    }

    fun getToken(): String? {
        val db = this.readableDatabase
        val selectQuery = "SELECT $TOKEN FROM $TOKEN_TABLE_NAME LIMIT 1"
        var token: String? = null

        db.rawQuery(selectQuery, null).use { cursor ->
            if (cursor.moveToFirst()) {
                val tokenIndex = cursor.getColumnIndex(TOKEN)
                if (tokenIndex != -1) {
                    token = cursor.getString(tokenIndex)
                }
            }
        }
        db.close()
        return token
    }
}

data class FcmMessage(
    val id: Int = 0,
    val message: String,
    val time: String
)
