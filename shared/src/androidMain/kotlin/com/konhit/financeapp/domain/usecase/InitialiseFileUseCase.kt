package com.konhit.financeapp.domain.usecase

import com.konhit.financeapp.db.DatabaseFactory
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.drive.DriveFileClient
import com.konhit.financeapp.domain.repository.SettingsRepository
import kotlinx.datetime.Clock
import java.io.File

class InitialiseFileUseCase(
    private val dbFactory: DatabaseFactory,
    private val dbHolder: DatabaseHolder,
    private val driveClient: DriveFileClient,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(localFile: File, fileName: String) {
        val db = dbFactory.createNew(localFile)

        // Insert INFOTABLE defaults required by MMEX
        db.infoTableQueries.insert("DATAVERSION", "3")
        db.infoTableQueries.insert("BASECURRENCYID", "1")
        db.infoTableQueries.insert("MMEXVERSION", "1.7.0")
        db.infoTableQueries.insert("CREATEDATE", Clock.System.now().toString().substring(0, 10))
        db.infoTableQueries.insert("DATEFORMAT", "%d/%m/%Y")

        // Insert a default payee required for all transactions
        val payeeId = System.currentTimeMillis() * 1000L
        db.payeeQueries.insert(payeeId, "Default")

        dbHolder.open(db, localFile, payeeId)

        // Upload to Drive and store the file ID
        val fileId = driveClient.upload(localFile, null)
        settings.saveDriveFileId(fileId)
        settings.saveLastSyncTime(Clock.System.now().toEpochMilliseconds())
    }
}
