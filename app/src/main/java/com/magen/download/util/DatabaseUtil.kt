package com.magen.download.util

import androidx.room.Room
import com.magen.download.App.Companion.applicationScope
import com.magen.download.App.Companion.context
import com.magen.download.database.AppDatabase
import com.magen.download.database.backup.Backup
import com.magen.download.database.backup.BackupUtil.BackupType
import com.magen.download.database.backup.BackupUtil.decodeToBackup
import com.magen.download.database.objects.CommandTemplate
import com.magen.download.database.objects.CookieProfile
import com.magen.download.database.objects.DownloadedVideoInfo
import com.magen.download.database.objects.OptionShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseUtil {
    private const val DATABASE_NAME = "app_database"
    private val db =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .build()
    private val dao = db.videoInfoDao()

    fun insertInfo(vararg infoList: DownloadedVideoInfo) {
        applicationScope.launch(Dispatchers.IO) {
            infoList.forEach { dao.insertInfoDistinctByPath(it) }
        }
    }

    init {
        applicationScope.launch {
            getTemplateFlow().collect {
                if (it.isEmpty()) PreferenceUtil.initializeTemplateSample()
            }
        }
    }

    fun getDownloadHistoryFlow() = dao.getDownloadHistoryFlow()

    private suspend fun getDownloadHistory() = dao.getDownloadHistory()

    fun getTemplateFlow() = dao.getTemplateFlow()

    fun getCookiesFlow() = dao.getCookieProfileFlow()

    fun getShortcuts() = dao.getOptionShortcuts()

    suspend fun deleteShortcut(shortcut: OptionShortcut) = dao.deleteShortcut(shortcut)

    suspend fun insertShortcut(shortcut: OptionShortcut) = dao.insertShortcut(shortcut)

    suspend fun getCookieById(id: Int) = dao.getCookieById(id)

    suspend fun deleteCookieProfile(profile: CookieProfile) = dao.deleteCookieProfile(profile)

    suspend fun insertCookieProfile(profile: CookieProfile) = dao.insertCookieProfile(profile)

    suspend fun updateCookieProfile(profile: CookieProfile) = dao.updateCookieProfile(profile)

    suspend fun getTemplateList() = dao.getTemplateList()

    suspend fun getShortcutList() = dao.getShortcutList()

    suspend fun deleteInfoList(infoList: List<DownloadedVideoInfo>, deleteFile: Boolean = false) {
        dao.deleteInfoList(infoList)
        infoList.forEach { info -> if (deleteFile) FileUtil.deleteFile(info.videoPath) }
    }

    suspend fun getInfoById(id: Int): DownloadedVideoInfo = dao.getInfoById(id)

    suspend fun deleteInfoById(id: Int) = dao.deleteInfoById(id)

    suspend fun insertTemplate(commandTemplate: CommandTemplate) =
        dao.insertTemplate(commandTemplate)

    suspend fun updateTemplate(commandTemplate: CommandTemplate) {
        dao.updateTemplate(commandTemplate)
    }

    suspend fun importBackup(backup: Backup, types: Set<BackupType>): Int {
        var cnt = 0
        backup.run {
            if (types.contains(BackupType.DownloadHistory)) {
                if (!downloadHistory.isNullOrEmpty()) {
                    // Deduplicate by the LOGICAL key (videoPath), not by full
                    // data-class equality: exported rows carry their original
                    // primary-key ids, so comparing whole objects both missed
                    // real duplicates (same path, different id) and wrongly
                    // skipped legit rows (same id, different path).
                    val existingPaths = getDownloadHistory().map { it.videoPath }.toHashSet()
                    dao.insertAll(
                        downloadHistory
                            .filter { it.videoPath !in existingPaths }
                            .map { it.copy(id = 0) }
                            .also { cnt += it.size }
                    )
                }
            }
            if (types.contains(BackupType.CommandTemplate)) {
                if (templates != null) {
                    val templateList = getTemplateList()
                    dao.importTemplates(
                        templates
                            .filterNot { templateList.contains(it) }
                            .map { it.copy(id = 0) }
                            .also { cnt += it.size }
                    )
                }
            }
            if (types.contains(BackupType.CommandShortcut)) {
                val shortcutList = getShortcutList()
                if (shortcuts != null) {
                    dao.insertAllShortcuts(
                        shortcuts
                            .filterNot { shortcutList.contains(it) }
                            .map { it.copy(id = 0) }
                            .also { cnt += it.size }
                    )
                }
            }
        }
        return cnt
    }

    suspend fun importTemplatesFromJson(json: String): Int {
        json
            .decodeToBackup()
            .onSuccess { backup ->
                return importBackup(
                    backup = backup,
                    types = setOf(BackupType.CommandTemplate, BackupType.CommandShortcut),
                )
            }
            .onFailure { it.printStackTrace() }
        return 0
    }

    suspend fun deleteTemplateById(id: Int) = dao.deleteTemplateById(id)

    suspend fun deleteTemplates(templates: List<CommandTemplate>) = dao.deleteTemplates(templates)

    private const val TAG = "DatabaseUtil"
}
