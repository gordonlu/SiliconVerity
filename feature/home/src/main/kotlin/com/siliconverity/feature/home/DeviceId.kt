package com.siliconverity.feature.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.UUID

@Composable
fun rememberDeviceId(): String {
    val context = LocalContext.current
    return remember {
        val prefs = context.getSharedPreferences("sv_prefs", Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        if (existing != null) {
            existing
        } else {
            val id = "SV-" + UUID.randomUUID().toString().take(5).uppercase()
            prefs.edit().putString(KEY, id).apply()
            id
        }
    }
}

private const val KEY = "device_id"
