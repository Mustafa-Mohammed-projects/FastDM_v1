package com.fastdm.app.ui

import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fastdm.app.R
import com.fastdm.app.core.DownloadRequest
import com.fastdm.app.core.Settings
import com.fastdm.app.core.displayUrl
import com.fastdm.app.core.normalizeInput
import com.fastdm.app.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    state: BrowserState,
    settings: Settings,
    captureName: String?,
    onCancelCapture: () -> Unit,
    onExit: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val tab = state.current
    val adBlock by settings.adBlock.collectAsState()

    var showTabs by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var linkMenu by remember { mutableStateOf<String?>(null) }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        state.filePathCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
        state.filePathCallback = null
    }
    SideEffect {
        state.launchChooser = { chooser.launch(it) }
        state.onLinkLongPress = { linkMenu = it }
    }
    DisposableEffect(tab.id) {
        tab.webView.onResume()
        onDispose {
            tab.webView.onPause()
            CookieManager.getInstance().flush()
        }
    }
    LaunchedEffect(tab.id, tab.url, focused) {
        if (!focused) field = TextFieldValue(displayUrl(tab.url))
    }

    BackHandler {
        when {
            state.customView != null -> state.hideCustomView()
            tab.canBack -> tab.webView.goBack()
            state.tabs.size > 1 -> state.closeTab(state.selected)
            else -> onExit()
        }
    }

    fun go() {
        val u = normalizeInput(field.text)
        if (u.isNotBlank()) tab.webView.loadUrl(u)
        focusManager.clearFocus()
    }

    Column(Modifier.fillMaxSize()) {
        // ---- address bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = cs.surfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val secure = tab.url.startsWith("https")
                    Icon(
                        if (secure) Icons.Rounded.Lock else Icons.Rounded.Public, null,
                        Modifier.size(18.dp),
                        tint = if (secure) Success else cs.onSurfaceVariant
                    )
                    Spacer(Modifier.size(8.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(
                            value = field,
                            onValueChange = { field = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                            cursorBrush = SolidColor(cs.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                            keyboardActions = KeyboardActions(onGo = { go() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { f ->
                                    focused = f.isFocused
                                    if (f.isFocused) field = TextFieldValue(tab.url, TextRange(0, tab.url.length))
                                }
                        )
                        if (field.text.isEmpty()) {
                            Text(
                                stringResource(R.string.browser_search_hint),
                                color = cs.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    if (focused && field.text.isNotEmpty()) {
                        IconButton(onClick = { field = TextFieldValue("") }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.size(6.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .border(2.dp, cs.onSurface, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showTabs = true },
                contentAlignment = Alignment.Center
            ) {
                Text("${state.tabs.size}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_new_tab)) },
                        leadingIcon = { Icon(Icons.Rounded.Add, null) },
                        onClick = { menuOpen = false; state.newTab(settings.homepage.value) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_forward)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null) },
                        enabled = tab.canForward,
                        onClick = { menuOpen = false; tab.webView.goForward() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_home)) },
                        leadingIcon = { Icon(Icons.Rounded.Home, null) },
                        onClick = { menuOpen = false; tab.webView.loadUrl(normalizeInput(settings.homepage.value)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_desktop)) },
                        leadingIcon = { Icon(Icons.Rounded.DesktopWindows, null) },
                        trailingIcon = { if (state.desktopMode) Icon(Icons.Rounded.Check, null) },
                        onClick = { menuOpen = false; state.toggleDesktop() }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(stringResource(R.string.browser_adblock))
                                if (adBlock) Text(
                                    stringResource(R.string.browser_blocked, tab.blocked),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = { Icon(Icons.Rounded.Shield, null) },
                        trailingIcon = { if (adBlock) Icon(Icons.Rounded.Check, null, tint = Success) },
                        onClick = { menuOpen = false; settings.setAdBlock(!adBlock) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_copy_link)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(tab.url))
                            Toast.makeText(context, R.string.msg_link_copied, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // ---- progress line
        if (tab.progress < 100) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        // ---- capture banner (refresh link from browser)
        if (captureName != null) {
            Surface(color = cs.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.capture_banner, captureName),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancelCapture) { Text(stringResource(R.string.dlg_cancel)) }
                }
            }
        }

        // ---- web content
        Box(Modifier.weight(1f)) {
            key(tab.id) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { FrameLayout(it) },
                    update = { frame ->
                        val wv = tab.webView
                        if (wv.parent !== frame) {
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            frame.removeAllViews()
                            frame.addView(
                                wv,
                                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            )
                        }
                    }
                )
            }
            val cv = state.customView
            if (cv != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { FrameLayout(it) },
                        update = { frame ->
                            if (cv.parent !== frame) {
                                (cv.parent as? ViewGroup)?.removeView(cv)
                                frame.removeAllViews()
                                frame.addView(cv)
                            }
                        }
                    )
                }
            }
        }

        // ---- bottom controls
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { tab.webView.goBack() }, enabled = tab.canBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.browser_back))
            }
            IconButton(onClick = { tab.webView.goForward() }, enabled = tab.canForward) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.browser_forward))
            }
            IconButton(onClick = { if (tab.progress < 100) tab.webView.stopLoading() else tab.webView.reload() }) {
                if (tab.progress < 100) Icon(Icons.Rounded.Close, stringResource(R.string.browser_stop))
                else Icon(Icons.Rounded.Refresh, stringResource(R.string.browser_reload))
            }
            IconButton(onClick = { tab.webView.loadUrl(normalizeInput(settings.homepage.value)) }) {
                Icon(Icons.Rounded.Home, stringResource(R.string.browser_home))
            }
        }
    }

    // ---- tabs sheet
    if (showTabs) {
        ModalBottomSheet(onDismissRequest = { showTabs = false }, containerColor = cs.surface) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.browser_tabs), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    FilledTonalButton(onClick = { state.newTab(settings.homepage.value); showTabs = false }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.browser_new_tab))
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.tabs, key = { _, t -> t.id }) { i, t ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (i == state.selected) cs.primaryContainer else cs.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { state.selected = i; showTabs = false }
                        ) {
                            Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t.title.ifBlank { displayUrl(t.url).ifBlank { stringResource(R.string.browser_new_tab) } },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        displayUrl(t.url),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { state.closeTab(i) }) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.browser_close))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- link long-press actions
    linkMenu?.let { link ->
        AlertDialog(
            onDismissRequest = { linkMenu = null },
            containerColor = cs.surface,
            title = { Text(displayUrl(link), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = {
                        linkMenu = null
                        state.onDownload(
                            DownloadRequest(
                                url = link,
                                fileName = URLUtil.guessFileName(link, null, null),
                                userAgent = tab.webView.settings.userAgentString,
                                referer = tab.url,
                                cookie = CookieManager.getInstance().getCookie(link) ?: ""
                            )
                        )
                    }) { Text(stringResource(R.string.link_download)) }
                    TextButton(onClick = {
                        linkMenu = null
                        state.newTab(link)
                    }) { Text(stringResource(R.string.link_open_tab)) }
                    TextButton(onClick = {
                        linkMenu = null
                        clipboard.setText(AnnotatedString(link))
                        Toast.makeText(context, R.string.msg_link_copied, Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.link_copy)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { linkMenu = null }) { Text(stringResource(R.string.dlg_cancel)) } }
        )
    }
}
