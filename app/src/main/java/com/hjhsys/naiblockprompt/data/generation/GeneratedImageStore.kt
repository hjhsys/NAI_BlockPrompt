package com.hjhsys.naiblockprompt.data.generation

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import java.io.File

class GeneratedImageStore(private val context: Context) {
    fun savePng(bytes: ByteArray, displayName: String, treeUri: String? = null): String {
        if (!treeUri.isNullOrBlank()) {
            val resolver = context.contentResolver
            val tree = Uri.parse(treeUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val uri = DocumentsContract.createDocument(resolver, parent, "image/png", displayName)
                ?: throw IllegalStateException("Selected folder could not create an image")
            try {
                resolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: throw IllegalStateException("Selected folder output stream is unavailable")
                return uri.toString()
            } catch (error: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: throw IllegalStateException("External pictures directory is unavailable")
            val file = File(root, "NAI_BlockPrompt/$displayName").apply { parentFile?.mkdirs() }
            file.writeBytes(bytes)
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            return file.absolutePath
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NAI_BlockPrompt")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore could not create an image")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                ?: throw IllegalStateException("MediaStore output stream is unavailable")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    fun exists(reference: String): Boolean = if (reference.startsWith("content://")) {
        runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(reference), "r")?.use { true } ?: false
        }.getOrDefault(false)
    } else File(reference).isFile

    fun sizeBytes(reference: String): Long? = if (reference.startsWith("content://")) {
        runCatching {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(reference), "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0L }
                    ?: descriptor.parcelFileDescriptor.statSize.takeIf { it >= 0L }
            }
        }.getOrNull()
    } else {
        File(reference).takeIf(File::isFile)?.length()?.takeIf { it > 0L }
    }

    fun delete(reference: String): Boolean = if (reference.startsWith("content://")) {
        runCatching { context.contentResolver.delete(Uri.parse(reference), null, null) > 0 }.getOrDefault(false)
    } else File(reference).delete()
}
