package com.fastdm.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fastdm.app.FastDMApp
import com.fastdm.app.core.extractUrl
import com.fastdm.app.ui.theme.FastDMTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val incoming = mutableStateOf<IncomingLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FastDMApp
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            FastDMTheme {
                var splash by remember { mutableStateOf(true) }
                // presplash.png is visible for exactly 3 seconds, then fades out.
                LaunchedEffect(Unit) {
                    delay(3000)
                    splash = false
                }
                Box(Modifier.fillMaxSize()) {
                    MainScreen(
                        engine = app.engine,
                        settings = app.settings,
                        incoming = incoming.value,
                        onIncomingHandled = { incoming.value = null }
                    )
                    AnimatedVisibility(visible = splash, exit = fadeOut(tween(500))) {
                        SplashOverlay()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        when (i?.action) {
            Intent.ACTION_VIEW -> i.dataString?.let { incoming.value = IncomingLink(it, openInBrowser = true) }
            Intent.ACTION_SEND -> extractUrl(i.getStringExtra(Intent.EXTRA_TEXT) ?: "")
                ?.let { incoming.value = IncomingLink(it, openInBrowser = false) }
        }
    }
}
