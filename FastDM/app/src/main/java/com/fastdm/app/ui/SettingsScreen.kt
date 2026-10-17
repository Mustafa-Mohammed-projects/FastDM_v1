package com.fastdm.app.ui

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fastdm.app.R
import com.fastdm.app.core.AdBlocker
import com.fastdm.app.core.DownloadEngine
import com.fastdm.app.core.Settings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(settings: Settings, engine: DownloadEngine) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val maxC by settings.maxConcurrent.collectAsState()
    val segments by settings.segments.collectAsState()
    val adBlock by settings.adBlock.collectAsState()
    val homepage by settings.homepage.collectAsState()
    var updating by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Section(R.string.settings_downloads) {
                SliderRow(stringResource(R.string.settings_max_concurrent), maxC, 1f..8f, 6) { settings.setMaxConcurrent(it) }
                Spacer(Modifier.height(8.dp))
                SliderRow(stringResource(R.string.settings_segments), segments, 1f..16f, 14) { settings.setSegments(it) }
                Text(
                    stringResource(R.string.settings_segments_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Section(R.string.settings_browser) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_adblock), fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.settings_adblock_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = adBlock, onCheckedChange = { settings.setAdBlock(it) })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = homepage,
                    onValueChange = { settings.setHomepage(it) },
                    label = { Text(stringResource(R.string.settings_homepage)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = !updating,
                    onClick = {
                        scope.launch {
                            updating = true
                            try {
                                val n = AdBlocker.updateFromRemote(engine.client, context.filesDir)
                                engine.messages.tryEmit(context.getString(R.string.msg_filters_updated, n))
                            } catch (e: Exception) {
                                engine.messages.tryEmit(e.message ?: context.getString(R.string.err_network))
                            }
                            updating = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (updating) R.string.settings_updating else R.string.settings_update_filters))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        CookieManager.getInstance().removeAllCookies(null)
                        WebStorage.getInstance().deleteAllData()
                        Toast.makeText(context, R.string.msg_data_cleared, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_clear_data)) }
            }
        }
        item {
            Section(R.string.settings_storage) {
                Text(stringResource(R.string.settings_storage_path), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Section(R.string.settings_about) {
                Text(
                    stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Int, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Medium)
            Text("$value", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range,
            steps = steps
        )
    }
}
