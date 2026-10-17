package com.fastdm.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.fastdm.app.R
import com.fastdm.app.core.DEFAULT_UA
import com.fastdm.app.core.DownloadEngine
import com.fastdm.app.core.DownloadItem
import com.fastdm.app.core.DownloadRequest
import com.fastdm.app.core.Settings

data class IncomingLink(val url: String, val openInBrowser: Boolean, val stamp: Long = System.nanoTime())

@Composable
fun MainScreen(
    engine: DownloadEngine,
    settings: Settings,
    incoming: IncomingLink?,
    onIncomingHandled: () -> Unit
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val browser = remember { BrowserState(context, settings) }
    val captureId by engine.refreshTarget.collectAsState()
    val all by engine.items.collectAsState()

    var pending by remember { mutableStateOf<DownloadRequest?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var addPrefill by remember { mutableStateOf("") }
    var refreshItem by remember { mutableStateOf<DownloadItem?>(null) }

    DisposableEffect(Unit) { onDispose { browser.destroy() } }

    SideEffect {
        browser.onDownload = { req ->
            val target = engine.refreshTarget.value
            if (target != null) {
                engine.refreshTarget.value = null
                engine.refreshLink(target, req.url, req.userAgent, req.referer, req.cookie)
                tab = 0
            } else {
                pending = req
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) { engine.messages.collect { snackbar.showSnackbar(it) } }

    LaunchedEffect(incoming) {
        incoming?.let { link ->
            if (link.openInBrowser) {
                browser.openUrl(link.url)
                tab = 1
            } else {
                addPrefill = link.url
                showAdd = true
                tab = 0
            }
            onIncomingHandled()
        }
    }

    BackHandler(enabled = tab == 2) { tab = 0 }

    val activeCount = all.count { it.isActive }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val entries = listOf(
                    Triple(R.string.nav_downloads, Icons.Rounded.Download, 0),
                    Triple(R.string.nav_browser, Icons.Rounded.Language, 1),
                    Triple(R.string.nav_settings, Icons.Rounded.Settings, 2)
                )
                entries.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            BadgedBox(badge = {
                                if (index == 0 && activeCount > 0) Badge { Text("$activeCount") }
                            }) { Icon(icon, null) }
                        },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (tab) {
                0 -> DownloadsScreen(
                    engine = engine,
                    onAdd = { addPrefill = ""; showAdd = true },
                    onRefreshLink = { refreshItem = it }
                )
                1 -> BrowserScreen(
                    state = browser,
                    settings = settings,
                    captureName = captureId?.let { id -> all.firstOrNull { it.id == id }?.fileName },
                    onCancelCapture = { engine.refreshTarget.value = null },
                    onExit = { tab = 0 }
                )
                else -> SettingsScreen(settings, engine)
            }
        }
    }

    pending?.let { req ->
        ConfirmDownloadDialog(
            req = req,
            onConfirm = { name ->
                if (engine.add(req.copy(fileName = name))) {
                    engine.messages.tryEmit(context.getString(R.string.msg_added))
                }
                pending = null
            },
            onDismiss = { pending = null }
        )
    }

    if (showAdd) {
        AddLinkDialog(
            prefill = addPrefill,
            onAdd = { url, name ->
                engine.add(DownloadRequest(url = url, fileName = name, userAgent = DEFAULT_UA))
                showAdd = false
                tab = 0
            },
            onDismiss = { showAdd = false }
        )
    }

    refreshItem?.let { target ->
        RefreshLinkDialog(
            item = target,
            onUpdate = { link ->
                engine.refreshLink(target.id, link, "", "", "")
                refreshItem = null
            },
            onCapture = {
                engine.refreshTarget.value = target.id
                refreshItem = null
                tab = 1
            },
            onDismiss = { refreshItem = null }
        )
    }
}
