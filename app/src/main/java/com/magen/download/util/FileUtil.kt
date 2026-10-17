package com.magen.download.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.CheckResult
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.magen.download.App.Companion.context
import com.magen.download.R
import java.io.File
import okhttp3.internal.closeQuietly

const val AUDIO_REGEX = "\\.(mp3|aac|opus|m4a|flac|wav|wma)$"
const val THUMBNAIL_REGEX = "\\.(jpg|png)$"
const val SUBTITLE_REGEX = "\\.(lrc|vtt|srt|ass|json3|srv.|ttml)$"
private const val PRIVATE_DIRECTORY_SUFFIX = ".MagenDownload"

object FileUtil {
    fun openFileFromResult(downloadResult: Result<List<String>>) {
        val filePaths = downloadResult.getOrNull()
        if (filePaths.isNullOrEmpty()) return
        openFile(filePaths.first()) {
            ToastUtil.makeToastSuspend(context.getString(R.string.file_unavailable))
        }
    }

    inline fun openFile(path: String, onFailureCallback: (Throwable) -> Unit) =
        path
            .runCatching {
                createIntentForOpeningFile(this)?.run { context.startActivity(this) }
                    ?: throw Exception()
            }
            .onFailure { onFailureCallback(it) }

    private fun createIntentForFile(path: String?): Intent? {
        if (path == null) return null

        val uri =
            path
                .runCatching {
                    DocumentFile.fromSingleUri(context, Uri.parse(path)).run {
                        if (this?.exists() == true) {
                            this.uri
                        } else if (File(this@runCatching).exists()) {
                            FileProvider.getUriForFile(
                                context,
                                context.getFileProvider(),
                                File(this@runCatching),
                            )
                        } else null
                    }
                }
                .getOrNull() ?: return null

        return Intent().apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            data = uri
        }
    }

    fun createIntentForOpeningFile(path: String?): Intent? =
        createIntentForFile(path)?.let {
            it.apply {
                action = (Intent.ACTION_VIEW)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

    fun createIntentForSharingFile(path: String?): Intent? =
        createIntentForFile(path)?.apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, data)
            val mimeType = data?.let { context.contentResolver.getType(it) } ?: "media/*"
            setDataAndType(this.data, mimeType)
            clipData = ClipData(null, arrayOf(mimeType), ClipData.Item(data))
        }

    fun Context.getFileProvider() = "$packageName.provider"

    fun String.getFileSize(): Long =
        this.run {
            val length = File(this).length()
            if (length == 0L) DocumentFile.fromSingleUri(context, Uri.parse(this))?.length() ?: 0L
            else length
        }

    fun String.getFileName(): String =
        this.run {
            File(this).nameWithoutExtension.ifEmpty {
                DocumentFile.fromSingleUri(context, Uri.parse(this))?.name ?: "video"
            }
        }

    fun deleteFile(path: String) =
        path.runCatching {
            if (!File(path).delete()) DocumentFile.fromSingleUri(context, Uri.parse(this))?.delete()
        }

    @CheckResult
    fun scanFileToMediaLibraryPostDownload(
        title: String,
        downloadDir: String,
        videoId: String? = null,
    ): List<String> =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                val path = file.absolutePath
                // The default output template embeds the video id
                // ("Title [id].ext"), which is unique — a bare title substring
                // cross-matches similar titles ("Foo" vs "Foo bar") or misses
                // sanitized/renamed names entirely.
                if (!videoId.isNullOrBlank() && path.contains("[$videoId]")) {
                    return@filter true
                }
                path.contains(title)
            }
            .map { it.absolutePath }
            .toMutableList()
            .apply {
                MediaScannerConnection.scanFile(context, this.toList().toTypedArray(), null, null)
                removeAll {
                    it.contains(Regex(THUMBNAIL_REGEX)) || it.contains(Regex(SUBTITLE_REGEX))
                }
            }

    fun scanDownloadDirectoryToMediaLibrary(downloadDir: String) =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath }
            // Chunked scanning: materializing every file of a large download
            // folder into one array (and one scan call) spiked memory on
            // devices with thousands of downloaded files.
            .chunked(200)
            .forEach { chunk ->
                MediaScannerConnection.scanFile(context, chunk.toTypedArray(), null, null)
            }

    @CheckResult
    fun moveFilesToSdcard(tempPath: File, sdcardUri: String): Result<List<String>> {
        val uriList = mutableListOf<String>()
        val destDir =
            Uri.parse(sdcardUri).run {
                DocumentsContract.buildDocumentUriUsingTree(
                    this,
                    DocumentsContract.getTreeDocumentId(this),
                )
            }
        val res =
            tempPath.runCatching {
                walkTopDown().forEach {
                    if (it.isDirectory) return@forEach
                    val mimeType =
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.extension) ?: "*/*"

                    val destUri =
                        DocumentsContract.createDocument(
                            context.contentResolver,
                            destDir,
                            mimeType,
                            it.name,
                        ) ?: return@forEach

                    // Open/close streams defensively: a throw between open
                    // calls leaked the input stream, and a failed copy left an
                    // orphaned partial document in the user's SD folder
                    // (duplicated as "name (1)" on the next attempt).
                    val copied =
                        runCatching {
                            it.inputStream().use { input ->
                                context.contentResolver.openOutputStream(destUri)?.use { output ->
                                    input.copyTo(output)
                                } ?: return@forEach
                            }
                            true
                        }
                            .onFailure {
                                it.printStackTrace()
                                runCatching {
                                    DocumentsContract.deleteDocument(
                                        context.contentResolver,
                                        destUri,
                                    )
                                }
                            }
                            .getOrDefault(false)
                    if (copied) uriList.add(destUri.toString())
                }
                uriList
            }
        // Clean the temp dir regardless of outcome: keeping it made every
        // retry re-copy everything from scratch.
        tempPath.deleteRecursively()
        return res
    }

    fun clearTempFiles(downloadDir: File, keep: ((File) -> Boolean)? = null): Int {
        var count = 0
        downloadDir.walkTopDown().forEach {
            if (it.isFile && !it.isHidden && keep?.invoke(it) != true) {
                if (it.delete()) count++
            }
        }
        return count
    }

    fun Context.getConfigDirectory(): File = cacheDir

    fun Context.getConfigFile(suffix: String = "") = File(getConfigDirectory(), "config$suffix.txt")

    fun Context.getCookiesFile() = File(getConfigDirectory(), "cookies.txt")

    fun getExternalTempDir() =
        File(getExternalDownloadDirectory(), "tmp").apply {
            mkdirs()
            createEmptyFile(".nomedia")
        }

    fun Context.getSdcardTempDir(child: String?): File =
        getExternalTempDir().run { child?.let { resolve(it) } ?: this }

    fun Context.getArchiveFile(): File = filesDir.createEmptyFile("archive.txt").getOrThrow()

    fun Context.getInternalTempDir() = File(filesDir, "tmp")

    internal fun getExternalDownloadDirectory() =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Magen download",
        )
            .also { it.mkdirs() }

    internal fun getExternalPrivateDownloadDirectory() =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            PRIVATE_DIRECTORY_SUFFIX,
        )

    fun File.createEmptyFile(fileName: String): Result<File> =
        this.runCatching {
                mkdirs()
                resolve(fileName).apply { this@apply.createNewFile() }
            }
            .onFailure { it.printStackTrace() }

    fun writeContentToFile(content: String, file: File): File = file.apply { writeText(content) }

    fun getRealPath(treeUri: Uri): String {
        val path: String = treeUri.path.toString()
        Log.d(TAG, path)
        if (!path.contains("primary:")) {
            ToastUtil.makeToast("This directory is not supported")
            return getExternalDownloadDirectory().absolutePath
        }
        val last: String = path.split("primary:").last()
        return Environment.getExternalStorageDirectory().absolutePath + "/$last"
    }

    private const val TAG = "FileUtil"
}
