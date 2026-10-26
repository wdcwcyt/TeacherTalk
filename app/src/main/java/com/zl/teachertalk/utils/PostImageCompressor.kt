package com.zl.teachertalk.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 将相册图片压缩为可上传的 JPEG 文件。
 *
 * 输出最长边不超过 1280px、JPEG 质量 80，并保证最终文件不超过 2MB。
 *
 * @param context 用于访问 ContentResolver 和缓存目录的应用上下文
 */
class PostImageCompressor(
    context: Context
) {

    private val appContext = context.applicationContext

    /**
     * 压缩单张图片。
     *
     * @param uri 相册返回的图片地址
     * @return 位于应用缓存目录的临时 JPEG 文件
     */
    suspend fun compress(uri: Uri): File = withContext(Dispatchers.IO) {
        val sourceBitmap = decodeBitmap(uri)
        val opaqueBitmap = drawOnWhiteBackground(sourceBitmap)
        sourceBitmap.recycle()

        val outputDirectory = File(appContext.cacheDir, CACHE_DIRECTORY_NAME)
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            opaqueBitmap.recycle()
            throw PostImageCompressionException(Reason.WRITE_FAILED)
        }

        val outputFile = File(
            outputDirectory,
            "post_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
        )
        try {
            FileOutputStream(outputFile).use { outputStream ->
                val compressed = opaqueBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    JPEG_QUALITY,
                    outputStream
                )
                if (!compressed) {
                    throw PostImageCompressionException(Reason.WRITE_FAILED)
                }
            }
            if (outputFile.length() > MAX_UPLOAD_BYTES) {
                throw PostImageCompressionException(Reason.TOO_LARGE)
            }
            outputFile
        } catch (exception: PostImageCompressionException) {
            outputFile.delete()
            throw exception
        } catch (exception: IOException) {
            outputFile.delete()
            throw PostImageCompressionException(Reason.WRITE_FAILED, exception)
        } finally {
            opaqueBitmap.recycle()
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeWithImageDecoder(uri)
            } else {
                decodeWithBitmapFactory(uri)
            }
        } catch (exception: PostImageCompressionException) {
            throw exception
        } catch (exception: Exception) {
            throw PostImageCompressionException(Reason.DECODE_FAILED, exception)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val targetSize = calculateTargetSize(info.size.width, info.size.height)
            decoder.setTargetSize(targetSize.first, targetSize.second)
        }
    }

    private fun decodeWithBitmapFactory(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: throw PostImageCompressionException(Reason.DECODE_FAILED)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw PostImageCompressionException(Reason.DECODE_FAILED)
        }

        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) >
            MAX_IMAGE_DIMENSION
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val decoded = appContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw PostImageCompressionException(Reason.DECODE_FAILED)

        val orientation = readExifOrientation(uri)
        val oriented = applyOrientation(decoded, orientation)
        if (oriented !== decoded) {
            decoded.recycle()
        }
        return scaleToMaxDimension(oriented)
    }

    private fun readExifOrientation(uri: Uri): Int {
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (exception: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleToMaxDimension(bitmap: Bitmap): Bitmap {
        val targetSize = calculateTargetSize(bitmap.width, bitmap.height)
        if (targetSize.first == bitmap.width && targetSize.second == bitmap.height) {
            return bitmap
        }
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            targetSize.first,
            targetSize.second,
            true
        )
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        return scaled
    }

    private fun calculateTargetSize(width: Int, height: Int): Pair<Int, Int> {
        val longestEdge = max(width, height)
        val scale = min(1.0, MAX_IMAGE_DIMENSION.toDouble() / longestEdge)
        return Pair(
            max(1, (width * scale).roundToInt()),
            max(1, (height * scale).roundToInt())
        )
    }

    private fun drawOnWhiteBackground(bitmap: Bitmap): Bitmap {
        val opaqueBitmap = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(opaqueBitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return opaqueBitmap
    }

    /**
     * 图片压缩失败原因。
     */
    enum class Reason {
        DECODE_FAILED,
        WRITE_FAILED,
        TOO_LARGE
    }

    companion object {
        /**
         * 单张图片上传硬上限。
         */
        const val MAX_UPLOAD_SIZE_MB = 2

        private const val MAX_IMAGE_DIMENSION = 1280
        private const val JPEG_QUALITY = 80
        private const val MAX_UPLOAD_BYTES = MAX_UPLOAD_SIZE_MB * 1024L * 1024L
        private const val CACHE_DIRECTORY_NAME = "post_image_uploads"
    }
}

/**
 * 客户端图片压缩异常。
 */
class PostImageCompressionException(
    val reason: PostImageCompressor.Reason,
    cause: Throwable? = null
) : Exception("Post image compression failed: $reason", cause)
