package com.biosdk.livenessdetection.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
