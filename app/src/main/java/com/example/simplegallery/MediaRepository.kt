package com.example.simplegallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Доступ к фото и видео через MediaStore: список альбомов и медиа конкретной папки. */
object MediaRepository {

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 и ниже: WRITE нужен для удаления чужих файлов
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private val collection: Uri = MediaStore.Files.getContentUri("external")

    private const val SELECTION =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"

    private val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
    )

    /** Группируем медиа по папкам (bucket) и формируем список альбомов с обложками. */
    fun queryAlbums(context: Context): List<Album> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        // Сохраняем порядок появления (самые свежие папки сверху).
        val albums = LinkedHashMap<Long, Album>()
        context.contentResolver
            .query(collection, projection, SELECTION, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val bucketIdCol =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
                val bucketNameCol =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val bucketId = cursor.getLong(bucketIdCol)
                    val existing = albums[bucketId]
                    if (existing == null) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(bucketNameCol) ?: "—"
                        val cover = ContentUris.withAppendedId(collection, id)
                        albums[bucketId] = Album(bucketId, name, cover, 1)
                    } else {
                        albums[bucketId] = existing.copy(count = existing.count + 1)
                    }
                }
            }
        return albums.values.toList()
    }

    /** Медиа конкретной папки (если bucketId == null — все фото и видео подряд). */
    fun queryMedia(context: Context, bucketId: Long?): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED
        )
        val selection: String
        val args: Array<String>
        if (bucketId == null) {
            selection = SELECTION
            args = selectionArgs
        } else {
            selection = "(${SELECTION}) AND ${MediaStore.Files.FileColumns.BUCKET_ID} = ?"
            args = selectionArgs + bucketId.toString()
        }
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val result = ArrayList<MediaItem>()
        context.contentResolver
            .query(collection, projection, selection, args, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeCol =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val isVideo = cursor.getInt(typeCol) ==
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val contentUri = if (isVideo)
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    result.add(MediaItem(ContentUris.withAppendedId(contentUri, id), isVideo))
                }
            }
        return result
    }
}
