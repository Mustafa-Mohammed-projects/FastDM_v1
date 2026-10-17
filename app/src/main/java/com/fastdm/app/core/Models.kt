package com.fastdm.app.core

enum class Status { QUEUED, DOWNLOADING, PAUSED, SAVING, COMPLETED, FAILED }

data class Segment(val start: Long, val end: Long, val downloaded: Long) {
    /** Length in bytes, or -1 when the size is unknown. */
    val length: Long get() = if (end >= 0) end - start + 1 else -1L
}

data class DownloadRequest(
    val url: String,
    val fileName: String = "",
    val mime: String = "",
    val size: Long = -1,
    val userAgent: String = "",
    val referer: String = "",
    val cookie: String = ""
)

data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val mime: String,
    val totalBytes: Long,
    val status: Status,
    val segments: List<Segment>,
    val resumable: Boolean,
    val createdAt: Long,
    val error: String?,
    val savedUri: String?,
    val maxSegments: Int,
    val userAgent: String,
    val referer: String,
    val cookie: String
) {
    val downloadedBytes: Long
        get() = if (status == Status.COMPLETED) totalBytes else segments.sumOf { it.downloaded }

    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val isActive: Boolean
        get() = status == Status.DOWNLOADING || status == Status.QUEUED || status == Status.SAVING
}
