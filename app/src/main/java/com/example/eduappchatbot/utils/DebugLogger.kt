package com.example.eduappchatbot.utils

import android.util.Log
import com.example.eduappchatbot.BuildConfig


object DebugLogger {
    fun debugLog(tag: String, message: String){
        if (BuildConfig.DEBUG){
            Log.d(tag, message)
        }
    }

    fun errorLog(tag: String, message: String){
        if (BuildConfig.DEBUG){
            Log.e(tag, message)
        }
    }
}