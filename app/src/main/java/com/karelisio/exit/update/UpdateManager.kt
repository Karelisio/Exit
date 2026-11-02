package com.karelisio.exit.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Réponse (partielle) de l'API GitHub `releases/latest`. */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long,
    /** « sha256:… », fourni par GitHub pour les assets récents. */
    val digest: String? = null,
)

data class UpdateInfo(
    val version: String,
    val changelog: String,
    val apkUrl: String,
    val size: Long,
    val sha256: String?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object CheckFailed : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState
}

val UpdateState.offer: UpdateInfo?
    get() = when (this) {
        is UpdateState.Available -> info
        is UpdateState.Downloading -> info
        is UpdateState.Installing -> info
        is UpdateState.Failed -> info
        else -> null
    }

/**
 * Mises à jour hors Play Store : dernière release GitHub (au plus toutes les 6 h), téléchargement
 * de l'APK avec progression, vérification (taille, SHA-256 si fourni, paquet), puis PackageInstaller.
 * Android refuse de toute façon une mise à jour signée avec une autre clé.
 */
class UpdateManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
    repo: String,
    private val currentVersion: String,
    private val scope: CoroutineScope,
) {
    private val repo = repo.trim()
    val isEnabled: Boolean get() = repo.contains('/')

    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _confirmation = MutableStateFlow<Intent?>(null)
    val confirmation: StateFlow<Intent?> = _confirmation.asStateFlow()

    /** Version proposée puis ignorée par l'utilisateur (« Plus tard ») : pas de feuille automatique. */
    val dismissedVersion: String? get() = prefs.getString(KEY_DISMISSED, null)

    private val dir get() = File(context.cacheDir, "updates")
    private var installJob: Job? = null
    @Volatile private var installing: UpdateInfo? = null

    fun dismiss(info: UpdateInfo) = prefs.edit { putString(KEY_DISMISSED, info.version) }

    /** Au lancement : au plus une vérification toutes les 6 h. */
    fun checkIfStale() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (isEnabled && System.currentTimeMillis() - last >= STALE_MS) scope.launch { check() }
    }

    fun checkNow() {
        scope.launch { check() }
    }

    suspend fun checkInBackground() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (isEnabled && System.currentTimeMillis() - last >= STALE_MS) check()
    }

    suspend fun check(): UpdateInfo? {
        if (!isEnabled) return null
        prefs.edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
        _state.update { if (it.busy) it else UpdateState.Checking }
        val info = try {
            fetchLatest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Vérification impossible", e)
            _state.update { if (it == UpdateState.Checking) UpdateState.CheckFailed else it }
            return null
        }
        val newer = info?.takeIf { compareVersions(it.version, currentVersion) > 0 }
        _state.update {
            when {
                it.busy -> it
                newer != null -> UpdateState.Available(newer)
                else -> UpdateState.UpToDate
            }
        }
        return newer
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun install(info: UpdateInfo) {
        if (installJob?.isActive == true) return
        installJob = scope.launch {
            _state.value = UpdateState.Downloading(info, 0f)
            try {
                val apk = download(info)
                verify(apk)
                installing = info
                _state.value = UpdateState.Installing(info)
                commit(apk)
            } catch (e: CancellationException) {
                _state.value = UpdateState.Available(info)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Mise à jour impossible", e)
                _state.value = UpdateState.Failed(info, e.message ?: "Erreur inconnue")
            }
        }
    }

    fun cancelDownload() {
        installJob?.cancel()
    }

    fun onInstallStatus(status: Int, confirmation: Intent?, message: String?) {
        val info = installing
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                if (confirmation != null) _confirmation.value = confirmation else fail(info, "Confirmation impossible")
            PackageInstaller.STATUS_SUCCESS -> {
                installing = null
                _state.value = UpdateState.UpToDate
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = info?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT -> fail(info, "Signature différente : désinstalle l'ancienne version (exporte une sauvegarde avant).")
            PackageInstaller.STATUS_FAILURE_STORAGE -> fail(info, "Espace de stockage insuffisant.")
            PackageInstaller.STATUS_FAILURE_BLOCKED -> fail(info, "Installation bloquée par le système.")
            else -> fail(info, message ?: "Installation impossible.")
        }
    }

    fun onConfirmationShown() {
        _confirmation.value = null
    }

    fun cleanup() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fail(info: UpdateInfo?, message: String) {
        _state.value = if (info != null) UpdateState.Failed(info, message) else UpdateState.Idle
    }

    private suspend fun fetchLatest(): UpdateInfo? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val response = http.newCall(request).await()
        return withContext(Dispatchers.IO) {
            response.use {
                if (it.code == 404) return@withContext null
                if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                val release = json.decodeFromString(GithubRelease.serializer(), it.body.string())
                if (release.draft || release.prerelease) return@withContext null
                val apk = release.assets.firstOrNull { a -> a.name.endsWith(".apk") } ?: return@withContext null
                UpdateInfo(
                    version = release.tagName.removePrefix("v"),
                    changelog = release.body?.trim().orEmpty(),
                    apkUrl = apk.url,
                    size = apk.size,
                    sha256 = apk.digest?.takeIf { d -> d.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                )
            }
        }
    }

    private suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val folder = dir.apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val partial = File(folder, "exit-${info.version}.apk.part")
        val apk = File(folder, "exit-${info.version}.apk")
        http.newCall(Request.Builder().url(info.apkUrl).build()).await().use { response ->
            if (!response.isSuccessful) throw IOException("Téléchargement impossible (HTTP ${response.code})")
            val total = info.size.takeIf { it > 0 } ?: response.body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var lastPercent = -1
            response.body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        if (total > 0) {
                            val percent = (received * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = UpdateState.Downloading(info, (received.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (info.size > 0 && received != info.size) throw IOException("Fichier incomplet")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && hash != info.sha256) throw IOException("Empreinte SHA-256 invalide")
        }
        if (!partial.renameTo(apk)) throw IOException("Impossible d'enregistrer l'APK")
        apk
    }

    private fun verify(apk: File) {
        val archive = context.packageManager.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(0))
        if (archive == null || archive.packageName != context.packageName) throw IOException("Ce fichier n'est pas une version d'Exit")
    }

    private suspend fun commit(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("exit.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(InstallResultReceiver.statusReceiver(context, id))
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    private val UpdateState.busy get() = this is UpdateState.Downloading || this is UpdateState.Installing

    companion object {
        private const val TAG = "ExitUpdate"
        private const val STALE_MS = 6 * 3600_000L
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_DISMISSED = "dismissed"

        /** Compare deux versions X.Y.Z (les suffixes comme « -debug » sont ignorés). */
        fun compareVersions(a: String, b: String): Int {
            fun parse(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val pa = parse(a)
            val pb = parse(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, value, _ -> value.close() }
        },
    )
}
