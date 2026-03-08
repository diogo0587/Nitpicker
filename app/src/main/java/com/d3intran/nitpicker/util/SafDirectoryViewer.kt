package com.d3intran.nitpicker.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.d3intran.nitpicker.model.FileType
import com.d3intran.nitpicker.model.LocalFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object SafDirectoryViewer {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "3gp")

    /**
     * Reads the contents of a DocumentFile directory and returns mapped LocalFileItems.
     */
    suspend fun listFilesFromUri(context: Context, directoryUri: Uri): List<LocalFileItem> = withContext(Dispatchers.IO) {
        val documentFile = DocumentFile.fromTreeUri(context, directoryUri)
            ?: return@withContext emptyList()

        if (!documentFile.isDirectory) return@withContext emptyList()

        val items = mutableListOf<LocalFileItem>()
        
        // Use modern ContentResolver query for better performance than DocumentFile.listFiles()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            directoryUri,
            DocumentsContract.getTreeDocumentId(directoryUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val lastModifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    if (idIndex == -1 || nameIndex == -1 || mimeIndex == -1) continue
                    val documentId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex) ?: "Unknown"
                    val mimeType = cursor.getString(mimeIndex) ?: ""
                    
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        continue // Skip subdirectories for now
                    }

                    val size = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                    val lastModified = if (lastModifiedIndex != -1 && !cursor.isNull(lastModifiedIndex)) cursor.getLong(lastModifiedIndex) else 0L

                    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    
                    val type = when {
                        mimeType.startsWith("image/") || imageExtensions.contains(extension) -> FileType.IMAGE
                        mimeType.startsWith("video/") || videoExtensions.contains(extension) -> FileType.VIDEO
                        else -> null
                    }

                    if (type != null) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId)
                        
                        var durationMillis: Long? = null
                        if (type == FileType.VIDEO) {
                            var retriever: android.media.MediaMetadataRetriever? = null
                            try {
                                retriever = android.media.MediaMetadataRetriever()
                                retriever.setDataSource(context, fileUri)
                                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                durationMillis = durationStr?.toLongOrNull()
                            } catch (e: Exception) {
                                android.util.Log.e("SafDirectoryViewer", "Failed to get duration for $name", e)
                            } finally {
                                try {
                                    retriever?.release()
                                } catch (e: java.io.IOException) {
                                }
                            }
                        }

                        items.add(
                            LocalFileItem(
                                path = fileUri.toString(), // Use URI string as path
                                name = name,
                                type = type,
                                thumbnailUri = fileUri, // Use the content URI directly
                                size = size,
                                lastModified = lastModified,
                                durationMillis = durationMillis
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort videos first, then images, then by name
        return@withContext items.sortedWith(
            compareBy<LocalFileItem> {
                when (it.type) {
                    FileType.VIDEO -> 0
                    FileType.IMAGE -> 1
                }
            }.thenBy { it.name }
        )
    }
}
