package com.example.model

import androidx.compose.ui.graphics.ImageBitmap

enum class SearchResultKind {
    IMAGE,
    FILE
}

data class DeviceSearchFile(
    val id: String,
    val name: String,
    val displayPath: String,
    val parentPath: String,
    val uriString: String,
    val parentUriString: String? = null,
    val localFilePath: String? = null,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val kind: SearchResultKind,
    val thumbnail: ImageBitmap? = null
)
