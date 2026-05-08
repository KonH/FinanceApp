package com.konhit.financeapp.domain.usecase

import com.konhit.financeapp.db.DatabaseFactory
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.drive.DriveAuthManager
import com.konhit.financeapp.drive.DriveFileClient
import com.konhit.financeapp.domain.repository.SettingsRepository
import java.io.File
import kotlinx.datetime.Clock

class InitialiseFileUseCase(
    private val dbFactory: DatabaseFactory,
    private val dbHolder: DatabaseHolder,
    private val driveClient: DriveFileClient,
    private val authManager: DriveAuthManager,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(localFile: File, fileName: String) {
        val conn = dbFactory.createNew(localFile)
        val db = conn.database

        db.infoTableQueries.insert("DATAVERSION", "3")
        db.infoTableQueries.insert("BASECURRENCYID", "1")
        db.infoTableQueries.insert("MMEXVERSION", "1.7.0")
        db.infoTableQueries.insert("CREATEDATE", Clock.System.now().toString().substring(0, 10))
        db.infoTableQueries.insert("DATEFORMAT", "%d/%m/%Y")

        val payeeId = System.currentTimeMillis() * 1000L
        db.payeeQueries.insert(payeeId, "Default")

        dbHolder.open(db, conn.driver, localFile, payeeId)
        settings.saveLocalFilePath(localFile.absolutePath)

        if (authManager.isSignedIn()) {
            val (fileId, uploadedTime) = driveClient.upload(localFile, null)
            settings.saveDriveFileId(fileId)
            settings.saveLastSyncTime(uploadedTime.toEpochMilliseconds())
        }
    }
}
