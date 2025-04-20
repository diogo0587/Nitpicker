package com.d3intran.nitpicker.model

// Mirrors the Rust FileInfo struct
data class FileInfo(
    val fileName: String,
    val fileType: String, // Extracted from fileName extension
    val fileSize: String,
    val thumbnailUrl: String,
    val pageUrl: String, // Full URL to the file's page
    var isSelected: Boolean = false // For selection state tracking
)