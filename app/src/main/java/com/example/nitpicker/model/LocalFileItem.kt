package com.example.nitpicker.model

import android.net.Uri

data class LocalFileItem(
    val path: String,
    val name: String,
    val type: FileType,
    val thumbnailUri: Uri?, // URI for Coil to load (can be file path for images, or cached thumb for videos)
    val size: Long,
    val lastModified: Long,
    val durationMillis: Long? = null // Add this field for video duration
    // Add isSelected later if needed
)

enum class FileType {
    IMAGE, VIDEO,
}