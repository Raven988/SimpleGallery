package com.example.simplegallery

import android.net.Uri

/** Папка-альбом: имя, идентификатор bucket, обложка и количество элементов. */
data class Album(
    val bucketId: Long,
    val name: String,
    val coverUri: Uri,
    val count: Int
)
