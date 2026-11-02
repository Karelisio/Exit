package com.karelisio.exit.data

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.karelisio.exit.domain.HOUR_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sauvegarde automatique quotidienne dans un dossier choisi par l'utilisateur (accès SAF persistant).
 * Le fichier est écrasé à chaque fois : il contient toujours l'état complet le plus récent.
 */
class AutoBackup(private val context: Context, private val repository: ExitRepository) {

    /** Lance la sauvegarde si un dossier est choisi et que la dernière date de plus de 20 h (ou si [force]). */
    suspend fun runIfDue(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val c = repository.config()
        val uri = c.autoBackupUri ?: return@withContext false
        val now = System.currentTimeMillis()
        if (!force && c.lastAutoBackupAt != null && now - c.lastAutoBackupAt < 20 * HOUR_MS) return@withContext false
        val ok = runCatching {
            val tree = DocumentFile.fromTreeUri(context, uri.toUri()) ?: error("Dossier introuvable")
            val file = tree.findFile(FILE_NAME) ?: tree.createFile("application/json", FILE_NAME) ?: error("Création impossible")
            val json = repository.exportJson()
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(json.toByteArray()) } ?: error("Écriture impossible")
        }.onFailure { Log.w("ExitBackup", "Sauvegarde automatique impossible", it) }.isSuccess
        if (ok) repository.markAutoBackup(now)
        ok
    }

    companion object {
        const val FILE_NAME = "exit-sauvegarde-auto.json"
    }
}
