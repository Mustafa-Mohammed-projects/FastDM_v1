package com.fastdm.app.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val maxConcurrent = MutableStateFlow(p.getInt("max_concurrent", 3))
    val segments = MutableStateFlow(p.getInt("segments", 8))
    val adBlock = MutableStateFlow(p.getBoolean("ad_block", true))
    val homepage = MutableStateFlow(p.getString("homepage", "https://duckduckgo.com") ?: "https://duckduckgo.com")

    fun setMaxConcurrent(v: Int) {
        p.edit().putInt("max_concurrent", v).apply()
        maxConcurrent.value = v
    }

    fun setSegments(v: Int) {
        p.edit().putInt("segments", v).apply()
        segments.value = v
    }

    fun setAdBlock(v: Boolean) {
        p.edit().putBoolean("ad_block", v).apply()
        adBlock.value = v
    }

    fun setHomepage(v: String) {
        p.edit().putString("homepage", v).apply()
        homepage.value = v
    }
}
