package com.echoreading.voice

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VoiceDownloadStatus { IDLE, DOWNLOADING, SUCCESS, ERROR }

/** Selects how the worker reconstructs the requested voice from its key. */
enum class VoiceDownloadSource { LOCAL, CATALOG }

data class VoiceDownloadState(
    val requestId: Long = 0L,
    val key: String? = null,
    val label: String? = null,
    val status: VoiceDownloadStatus = VoiceDownloadStatus.IDLE,
    val progress: Float = 0f,
    val copiedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null,
)

/**
 * WorkManager-backed voice transfer coordinator.
 *
 * Screens only observe [state]; the actual transfer runs in
 * [VoiceDownloadWorker], survives leaving the screen, and is rescheduled by
 * WorkManager after process death. `OfflineVoice` validates into a `.part`
 * file and renames atomically, so an interrupted transfer is safely restarted
 * rather than installed half-written.
 */
object VoiceDownloadManager {
    private const val WORK_NAME = "voice-download"
    private const val PREFS = "voice_download"
    private const val PREF_WORK_ID = "work_id"
    private const val PREF_KEY = "key"
    private const val PREF_LABEL = "label"
    private const val PREF_ACKNOWLEDGED = "acknowledged_work_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(VoiceDownloadState())
    val state: StateFlow<VoiceDownloadState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var observer: Job? = null
    private var lastObservedWorkId: String? = null

    /**
     * Starts observing durable work. Call this from Activity/Application start
     * so an in-flight transfer is visible after the process is recreated.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (observer?.isActive == true) return
        val app = context.applicationContext
        appContext = app
        observer = scope.launch {
            WorkManager.getInstance(app)
                .getWorkInfosForUniqueWorkFlow(WORK_NAME)
                .collect { infos ->
                    val pendingId = prefs(app).getString(PREF_WORK_ID, null)
                    val pendingInfo = infos.lastOrNull { it.id.toString() == pendingId }
                    // Right after enqueue the new WorkInfo may not be in the
                    // first emission yet. Do not fall back to an older finished
                    // transfer while the optimistic DOWNLOADING state is live.
                    if (pendingInfo == null &&
                        pendingId != null &&
                        _state.value.status == VoiceDownloadStatus.DOWNLOADING
                    ) {
                        return@collect
                    }
                    val info = pendingInfo ?: infos.lastOrNull()
                    lastObservedWorkId = info?.id?.toString()
                    _state.value = info?.toState(app) ?: VoiceDownloadState()
                }
        }
    }

    /**
     * Enqueues a transfer unless one is already running. Returns false when a
     * different voice is currently downloading.
     */
    @Synchronized
    fun start(
        context: Context,
        key: String,
        label: String,
        source: VoiceDownloadSource,
    ): Boolean {
        initialize(context)
        if (_state.value.status == VoiceDownloadStatus.DOWNLOADING) return false

        val app = context.applicationContext
        val request = OneTimeWorkRequestBuilder<VoiceDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                workDataOf(
                    VoiceDownloadWorker.KEY_VOICE to key,
                    VoiceDownloadWorker.KEY_LABEL to label,
                    VoiceDownloadWorker.KEY_SOURCE to when (source) {
                        VoiceDownloadSource.LOCAL -> VoiceDownloadWorker.SOURCE_LOCAL
                        VoiceDownloadSource.CATALOG -> VoiceDownloadWorker.SOURCE_CATALOG
                    },
                ),
            )
            .addTag(WORK_NAME)
            .build()

        prefs(app).edit()
            .putString(PREF_WORK_ID, request.id.toString())
            .putString(PREF_KEY, key)
            .putString(PREF_LABEL, label)
            .remove(PREF_ACKNOWLEDGED)
            .apply()

        WorkManager.getInstance(app)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)

        _state.value = VoiceDownloadState(
            requestId = request.id.stableId(),
            key = key,
            label = label,
            status = VoiceDownloadStatus.DOWNLOADING,
        )
        return true
    }

    fun cancel() {
        val app = appContext ?: return
        WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
        _state.value = VoiceDownloadState()
    }

    /** Lets the observing screen consume a terminal result exactly once. */
    fun acknowledge(requestId: Long) {
        val app = appContext ?: return
        if (_state.value.requestId != requestId) return
        val workId = lastObservedWorkId ?: prefs(app).getString(PREF_WORK_ID, null)
        if (workId != null) {
            prefs(app).edit().putString(PREF_ACKNOWLEDGED, workId).apply()
        }
        _state.value = VoiceDownloadState()
    }

    private fun WorkInfo.toState(context: Context): VoiceDownloadState {
        val prefs = prefs(context)
        val pendingId = prefs.getString(PREF_WORK_ID, null)
        val acknowledgedId = prefs.getString(PREF_ACKNOWLEDGED, null)
        val isPending = id.toString() == pendingId

        if (state.isFinished && id.toString() == acknowledgedId) {
            return VoiceDownloadState()
        }

        val key = when {
            isPending -> prefs.getString(PREF_KEY, null)
            else -> null
        } ?: progress.getString(VoiceDownloadWorker.KEY_VOICE)
            ?: outputData.getString(VoiceDownloadWorker.KEY_VOICE)

        val label = when {
            isPending -> prefs.getString(PREF_LABEL, null)
            else -> null
        } ?: progress.getString(VoiceDownloadWorker.KEY_LABEL)
            ?: outputData.getString(VoiceDownloadWorker.KEY_LABEL)

        val status = when (state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
            WorkInfo.State.RUNNING,
            -> VoiceDownloadStatus.DOWNLOADING

            WorkInfo.State.SUCCEEDED -> VoiceDownloadStatus.SUCCESS
            WorkInfo.State.FAILED -> VoiceDownloadStatus.ERROR
            WorkInfo.State.CANCELLED -> VoiceDownloadStatus.IDLE
        }

        val copied = progress.getLong(VoiceDownloadWorker.KEY_COPIED, 0L)
        val total = progress.getLong(VoiceDownloadWorker.KEY_TOTAL, 0L)
        val progressValue = when {
            status == VoiceDownloadStatus.SUCCESS -> 1f
            total > 0 -> copied.toFloat() / total
            else -> 0f
        }

        return VoiceDownloadState(
            requestId = id.stableId(),
            key = key,
            label = label,
            status = status,
            progress = progressValue.coerceIn(0f, 1f),
            copiedBytes = copied,
            totalBytes = total,
            error = if (status == VoiceDownloadStatus.ERROR) {
                outputData.getString(VoiceDownloadWorker.KEY_ERROR)
                    ?: "Falha na transferência"
            } else {
                null
            },
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun UUID.stableId(): Long {
        val value = mostSignificantBits xor leastSignificantBits
        return if (value == 0L) 1L else value
    }
}
