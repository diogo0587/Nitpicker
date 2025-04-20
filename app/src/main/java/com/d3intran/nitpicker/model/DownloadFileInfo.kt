package com.d3intran.nitpicker.model

import java.util.UUID

// Mirrors Rust's DownloadFile
data class DownloadFileInfo(
    val id: String = UUID.randomUUID().toString(), // Unique ID for tracking
    val fileName: String,
    val fileType: String,
    val downloadPageUrl: String, // The intermediate page (used as Referer)
    val fileUrl: String,         // The actual direct download URL
    val albumTitle: String       // To determine the save directory
)