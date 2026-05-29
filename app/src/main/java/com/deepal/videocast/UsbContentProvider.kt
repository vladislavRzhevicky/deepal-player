package com.deepal.videocast

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import java.nio.ByteBuffer

/**
 * Отдаёт UsbFile из плейлиста (pinned в UsbSession) как content:// URI.
 * ExoPlayer открывает через ContentResolver.openFileDescriptor() и
 * стримит напрямую с флешки — без копий.
 *
 * URI: content://com.deepal.videocast.usb/<token>/<index>/<name>
 * Index — позиция в плейлисте. PlayerActivity перезаряжает media item на
 * новый URI при prev/next; так ExoPlayer корректно сбрасывает кэш.
 */
class UsbContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.deepal.videocast.usb"

        fun buildUri(token: Long, index: Int, name: String): Uri =
            Uri.parse("content://$AUTHORITY/$token/$index/${Uri.encode(name)}")
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        val token = uri.pathSegments.getOrNull(0)?.toLongOrNull() ?: return null
        val index = uri.pathSegments.getOrNull(1)?.toIntOrNull() ?: 0
        val pinned = UsbSession.get(token) ?: return null
        val file = pinned.fileAt(index) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val c = MatrixCursor(cols)
        val row = cols.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length
                else -> null
            }
        }.toTypedArray()
        c.addRow(row)
        return c
    }

    override fun getType(uri: Uri): String = "video/*"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, sel: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, sel: String?, args: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val token = uri.pathSegments.getOrNull(0)?.toLongOrNull() ?: return null
        val index = uri.pathSegments.getOrNull(1)?.toIntOrNull() ?: 0
        val pinned = UsbSession.get(token) ?: return null
        val file = pinned.fileAt(index) ?: return null
        val length = file.length

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        val ctx = context ?: return null
        val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val handler = Handler(UsbSession.ioHandlerThread.looper)

        val cb = object : ProxyFileDescriptorCallback() {
            override fun onGetSize(): Long = length

            override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
                val remaining = (length - offset).coerceAtLeast(0)
                if (remaining == 0L) return 0
                val len = remaining.coerceAtMost(size.toLong()).toInt()
                val buf = ByteBuffer.wrap(data, 0, len)
                try {
                    file.read(offset, buf)
                } catch (e: Exception) {
                    throw ErrnoException("usb.read", OsConstants.EIO, e)
                }
                return len
            }

            override fun onRelease() {
                // не освобождаем pinned тут — это делает PlayerActivity.onDestroy
            }
        }
        return sm.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, cb, handler)
    }
}
