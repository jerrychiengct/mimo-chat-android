package com.jerry.mimochat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

object PortraitStore {
    fun file(context: Context, name: String): File? =
        name.takeIf { it.matches(Regex("[a-f0-9-]+\\.jpg")) }?.let { File(File(context.filesDir, "portraits"), it) }

    suspend fun import(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Choose a valid image.")
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Could not open this image.")
        val orientation = runCatching { resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } }.getOrNull()
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        val upright = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        val size = minOf(upright.width, upright.height)
        val cropped = Bitmap.createBitmap(upright, (upright.width - size) / 2, (upright.height - size) / 2, size, size)
        val portrait = Bitmap.createScaledBitmap(cropped, minOf(size, 1024), minOf(size, 1024), true)
        val directory = File(context.filesDir, "portraits").apply { mkdirs() }
        val name = "${UUID.randomUUID()}.jpg"
        val destination = File(directory, name)
        try {
            destination.outputStream().use { if (!portrait.compress(Bitmap.CompressFormat.JPEG, 90, it)) throw IOException("Could not save the portrait.") }
        } catch (failure: Exception) { destination.delete(); throw failure }
        name
    }
}

@Composable
fun CharacterPortrait(character: CharacterCard, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, character.portraitFile) {
        value = withContext(Dispatchers.IO) {
            PortraitStore.file(context, character.portraitFile)?.let { file ->
                runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
            }
        }
    }
    Box(modifier.clip(CircleShape).background(Color(character.primaryColour)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), "${character.name} portrait", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(character.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
    }
}
