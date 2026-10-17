package com.fastdm.app

import android.app.Application
import com.fastdm.app.core.AdBlocker
import com.fastdm.app.core.DownloadEngine
import com.fastdm.app.core.Settings
import com.fastdm.app.service.Notifier

class FastDMApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var engine: DownloadEngine
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        engine = DownloadEngine(this, settings)
        Notifier.createChannels(this)
        AdBlocker.load(filesDir)
    }
}
