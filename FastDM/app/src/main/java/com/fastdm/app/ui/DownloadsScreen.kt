package com.fastdm.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fastdm.app.R
import com.fastdm.app.core.DownloadEngine
import com.fastdm.app.core.DownloadItem
import com.fastdm.app.core.FileKind
import com.fastdm.app.core.Status
import com.fastdm.app.core.fileKind
import com.fastdm.app.core.formatBytes
import com.fastdm.app.core.formatEta
import com.fastdm.app.core.formatSpeed
import com.fastdm.app.ui.theme.BrandGradient
import com.fastdm.app.ui.theme.Success

@Composable
fun DownloadsScreen(
    engine: DownloadEngine,
    onAdd: () -> Unit,
    onRefreshLink: (DownloadItem) -> Unit
) {
    val all by engine.items.collectAsState()
    val speeds by engine.speeds.collectAsState()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<DownloadItem?>(null) }

    val shown = remember(all, filter) {
        when (filter) {
            1 -> all.filter { it.status != Status.COMPLETED }
            2 -> all.filter { it.status == Status.COMPLETED }
            else -> all
        }
    }
    val totalSpeed = speeds.values.sum()
    val activeCount = all.count { it.isActive }
    val doneCount = all.count { it.status == Status.COMPLETED }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SummaryCard(
                    speed = totalSpeed,
                    active = activeCount,
                    done = doneCount,
                    onPauseAll = { engine.pauseAll() },
                    onResumeAll = { engine.resumeAll() }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text(stringResource(R.string.filter_all)) })
                    FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text(stringResource(R.string.filter_active)) })
                    FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text(stringResource(R.string.filter_done)) })
                }
            }
            if (shown.isEmpty()) {
                item { EmptyState() }
            }
            items(shown, key = { it.id }) { item ->
                DownloadCard(
                    item = item,
                    speed = speeds[item.id] ?: 0L,
                    engine = engine,
                    onRefreshLink = onRefreshLink,
                    onDelete = { deleteTarget = it }
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text(stringResource(R.string.add_link), fontWeight = FontWeight.SemiBold) }
        )
    }

    deleteTarget?.let { target ->
        DeleteDialog(
            item = target,
            onConfirm = { alsoFile ->
                engine.delete(target.id, alsoFile)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun SummaryCard(speed: Long, active: Int, done: Int, onPauseAll: () -> Unit, onResumeAll: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BrandGradient)
            .padding(20.dp)
    ) {
        Text(stringResource(R.string.total_speed), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
        Text(
            formatSpeed(speed),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.active_count, active) + "  ·  " + stringResource(R.string.done_count, done),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = onPauseAll, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                Icon(Icons.Rounded.Pause, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.pause_all))
            }
            TextButton(onClick = onResumeAll, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.resume_all))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.CloudDownload, null,
            Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.empty_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

private fun kindStyle(k: FileKind): Pair<ImageVector, Color> = when (k) {
    FileKind.VIDEO -> Icons.Rounded.Videocam to Color(0xFFFF7A59)
    FileKind.AUDIO -> Icons.Rounded.MusicNote to Color(0xFFB07CFF)
    FileKind.IMAGE -> Icons.Rounded.Image to Color(0xFF34D399)
    FileKind.ARCHIVE -> Icons.Rounded.FolderZip to Color(0xFFFBBF24)
    FileKind.DOCUMENT -> Icons.Rounded.Description to Color(0xFF5B9DFF)
    FileKind.APK -> Icons.Rounded.Android to Color(0xFF7BD88F)
    FileKind.OTHER -> Icons.Rounded.InsertDriveFile to Color(0xFF9AA6C4)
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    speed: Long,
    engine: DownloadEngine,
    onRefreshLink: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val (icon, tint) = kindStyle(remember(item.fileName) { fileKind(item.fileName) })
    val done = item.downloadedBytes
    val total = item.totalBytes
    val pct = if (total > 0) "${(item.progress * 100).toInt()}% · " else ""

    val line = when (item.status) {
        Status.COMPLETED -> stringResource(R.string.status_completed, formatBytes(total))
        Status.FAILED -> stringResource(R.string.status_failed, item.error ?: "")
        Status.QUEUED -> stringResource(R.string.status_queued)
        Status.SAVING -> stringResource(R.string.status_saving)
        Status.PAUSED ->
            if (total > 0) pct + stringResource(R.string.status_paused, formatBytes(done), formatBytes(total))
            else stringResource(R.string.status_paused_unknown, formatBytes(done))
        Status.DOWNLOADING -> buildString {
            append(pct)
            append(formatBytes(done))
            if (total > 0) append(" / ${formatBytes(total)}")
            if (speed > 0) {
                append(" · ${formatSpeed(speed)}")
                if (total > 0) append(" · ${formatEta((total - done) / speed)}")
            }
        }
    }
    val lineColor = if (item.status == Status.FAILED) cs.error else cs.onSurfaceVariant
    val barColor = when (item.status) {
        Status.COMPLETED -> Success
        Status.FAILED -> cs.error
        Status.PAUSED, Status.QUEUED -> cs.outline
        else -> cs.primary
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = item.status == Status.COMPLETED) { openFile(context, item) }
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = tint) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.fileName.ifBlank { item.url },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(3.dp))
                Text(line, style = MaterialTheme.typography.bodySmall, color = lineColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.status != Status.COMPLETED) {
                    Spacer(Modifier.height(8.dp))
                    SegmentBar(item, barColor, cs.surfaceVariant, cs.surface)
                }
            }
            when (item.status) {
                Status.DOWNLOADING, Status.QUEUED ->
                    IconButton(onClick = { engine.pause(item.id) }) {
                        Icon(Icons.Rounded.Pause, stringResource(R.string.action_pause), tint = cs.primary)
                    }
                Status.PAUSED ->
                    IconButton(onClick = { engine.resume(item.id) }) {
                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.action_resume), tint = cs.primary)
                    }
                Status.FAILED ->
                    IconButton(onClick = { engine.resume(item.id) }) {
                        Icon(Icons.Rounded.Replay, stringResource(R.string.action_retry), tint = cs.error)
                    }
                Status.COMPLETED ->
                    IconButton(onClick = { openFile(context, item) }) {
                        Icon(Icons.Rounded.OpenInNew, stringResource(R.string.action_open), tint = Success)
                    }
                Status.SAVING ->
                    CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.5.dp)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = cs.onSurfaceVariant) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (item.status != Status.COMPLETED) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_refresh_link)) },
                            leadingIcon = { Icon(Icons.Rounded.Link, null) },
                            onClick = { menu = false; onRefreshLink(item) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_copy_link)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = {
                            menu = false
                            clipboard.setText(AnnotatedString(item.url))
                            Toast.makeText(context, R.string.msg_link_copied, Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_delete), color = cs.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = cs.error) },
                        onClick = { menu = false; onDelete(item) }
                    )
                }
            }
        }
    }
}

/** One thin bar split into the real download segments, so parallel parts are visible. */
@Composable
private fun SegmentBar(item: DownloadItem, fill: Color, track: Color, gap: Color) {
    val progress by animateFloatAsState(item.progress, tween(400), label = "progress")
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
    ) {
        drawRect(track)
        val total = item.totalBytes
        if (item.segments.size > 1 && total > 0) {
            item.segments.forEachIndexed { idx, s ->
                val x0 = s.start.toFloat() / total * size.width
                val w = s.length.toFloat() / total * size.width
                val f = (s.downloaded.toFloat() / s.length).coerceIn(0f, 1f)
                drawRect(fill, Offset(x0, 0f), Size(w * f, size.height))
                if (idx > 0) drawRect(gap, Offset(x0 - 1f, 0f), Size(2f, size.height))
            }
        } else {
            drawRect(fill, Offset.Zero, Size(size.width * progress, size.height))
        }
    }
}

private fun openFile(context: Context, item: DownloadItem) {
    val uri = item.savedUri?.let { Uri.parse(it) } ?: return
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, item.mime.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.msg_cant_open, Toast.LENGTH_SHORT).show()
    }
}
