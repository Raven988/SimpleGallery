package com.example.simplegallery

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Один элемент галереи — фото или видео. */
@Parcelize
data class MediaItem(
    val uri: Uri,
    val isVideo: Boolean
) : Parcelable
