package com.mychuannong.app.core.account

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow

/** Private on-device avatar keyed only by the authenticated platform account. */
object AccountAvatarStore {
    val revision = MutableStateFlow(0L)
    private fun file(context: Context, id: String): File {
        require(id.isNotBlank())
        val key = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "account-avatars/$key.jpg")
    }
    fun read(context: Context, id: String?): File? = id?.takeIf { it.isNotBlank() }?.let { file(context,it).takeIf(File::exists) }
    fun save(context: Context, id: String, uri: Uri) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytesLimited() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = BitmapFactory.Options().apply {
            var sample = 1
            while (maxOf(bounds.outWidth,bounds.outHeight) / sample > 1024) sample *= 2
            inSampleSize = sample
        }
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,options))
        val orientation = runCatching { android.media.ExifInterface(bytes.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = android.graphics.Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f,1f); 3 -> setRotate(180f); 4 -> setScale(1f,-1f)
                5 -> { setRotate(90f); postScale(-1f,1f) }; 6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f,1f) }; 8 -> setRotate(-90f)
            }
        }
        val bitmap = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also { if(it !== decoded) decoded.recycle() }
        val target = file(context,id)
        target.parentFile?.mkdirs()
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG,90,stream))
            atomic.finishWrite(stream)
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        } finally { bitmap.recycle() }
        revision.value += 1
    }
    private fun java.io.InputStream.readBytesLimited(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(out.size() + count <= 20 * 1024 * 1024)
            out.write(buffer,0,count)
        }
        return out.toByteArray()
    }
}

object AccountDisplayMask {
    fun phone(value: String?): String {
        val text = value.orEmpty().trim()
        return if (text.length == 11) text.take(3) + "****" + text.takeLast(4) else "未绑定"
    }
    fun student(value: String?): String {
        val text = value.orEmpty().trim()
        return when { text.isEmpty() -> "未绑定"; text.length <= 6 -> "*".repeat(text.length)
            else -> text.take(4) + "*".repeat(text.length - 6) + text.takeLast(2) }
    }
}
