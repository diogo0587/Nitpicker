package com.d3intran.nitpicker.model

// Mirrors Rust's DownloadProgress and DownloadStatus
data class DownloadProgress(
    val id: String,
    val fileName: String,
    val albumTitle: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progressPercent: Int = 0,
    val status: DownloadStatus = DownloadStatus.Pending,
    val error: String? = null,
    val filePath: String? = null // Store final path upon completion
)
