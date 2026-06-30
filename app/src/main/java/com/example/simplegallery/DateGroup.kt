package com.example.simplegallery

import android.net.Uri

data class DateGroup(
    val label: String,
    val coverUri: Uri,
    val count: Int,
    val dateFrom: Long,
    val dateTo: Long
)
