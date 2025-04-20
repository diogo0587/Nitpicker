package com.d3intran.nitpicker.model

enum class DownloadStatus {
    Pending,      // Waiting in queue
    FetchingUrl,  // Getting the direct download URL
    Downloading,  // Actively downloading
    Paused,       // (Future feature)
    Completed,    // Download successful
    Error,        // An error occurred
    Cancelled     // User cancelled
}