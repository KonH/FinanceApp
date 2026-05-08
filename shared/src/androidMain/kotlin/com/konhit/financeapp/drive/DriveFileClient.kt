package com.konhit.financeapp.drive

import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.io.File
import java.io.FileOutputStream

class DriveFileClient(private val authManager: DriveAuthManager) {

    private fun buildDrive(): Drive {
        val credential = authManager.getCredential()
            ?: error("Not signed in to Google Drive")
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("FinanceApp").build()
    }

    suspend fun download(fileId: String, destFile: File) = withContext(Dispatchers.IO) {
        destFile.parentFile?.mkdirs()
        buildDrive().files().get(fileId)
            .executeMediaAsInputStream()
            .use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
    }

    suspend fun upload(srcFile: File, fileId: String?): Pair<String, Instant> = withContext(Dispatchers.IO) {
        val drive = buildDrive()
        val mediaContent = FileContent("application/octet-stream", srcFile)
        if (fileId == null) {
            val metadata = DriveFile().setName(srcFile.name)
            val result = drive.files().create(metadata, mediaContent)
                .setFields("id,modifiedTime")
                .execute()
            result.id to Instant.fromEpochMilliseconds(result.modifiedTime.value)
        } else {
            val result = drive.files().update(fileId, DriveFile(), mediaContent)
                .setFields("modifiedTime")
                .execute()
            fileId to Instant.fromEpochMilliseconds(result.modifiedTime.value)
        }
    }

    suspend fun findFileIdByName(name: String): String? = withContext(Dispatchers.IO) {
        val escaped = name.replace("'", "\\'")
        buildDrive().files().list()
            .setQ("name='$escaped' and trashed=false")
            .setFields("files(id)")
            .setSpaces("drive")
            .execute()
            .files
            .firstOrNull()
            ?.id
    }

    suspend fun getRemoteModifiedTime(fileId: String): Instant = withContext(Dispatchers.IO) {
        val file = buildDrive().files().get(fileId)
            .setFields("modifiedTime")
            .execute()
        Instant.fromEpochMilliseconds(file.modifiedTime.value)
    }
}
