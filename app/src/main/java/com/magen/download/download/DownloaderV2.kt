package com.magen.download.download

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.magen.download.App
import com.magen.download.R
import com.magen.download.download.Task.DownloadState
import com.magen.download.download.Task.DownloadState.Canceled
import com.magen.download.download.Task.DownloadState.Completed
import com.magen.download.download.Task.DownloadState.Error
import com.magen.download.download.Task.DownloadState.FetchingInfo
import com.magen.download.download.Task.DownloadState.Idle
import com.magen.download.download.Task.DownloadState.ReadyWithInfo
import com.magen.download.download.Task.DownloadState.Running
import com.magen.download.download.Task.RestartableAction.Download
import com.magen.download.download.Task.RestartableAction.FetchInfo
import com.magen.download.download.Task.TypeInfo
import com.magen.download.util.DownloadUtil
import com.magen.download.util.FileUtil
import com.magen.download.util.NotificationUtil
import com.magen.download.util.PreferenceUtil
import com.magen.download.util.VideoInfo
import com.magen.download.util.toStableNotificationId
import com.yausername.youtubedl_android.YoutubeDL
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

private const val TAG = "DownloaderV2"

private const val MAX_CONCURRENCY = 3

interface DownloaderV2 {
    fun getTaskStateMap(): SnapshotStateMap<Task, Task.State>

    /** Number of tasks currently fetching info or downloading (busy). */
    fun countBusyTasks(): Int =
        getTaskStateMap().values.count { state ->
            state.downloadState is Task.DownloadState.Running ||
                state.downloadState is Task.DownloadState.FetchingInfo
        }

    fun cancel(task: Task): Boolean

    fun cancel(taskId: String): Boolean {
        return getTaskStateMap().keys.find { it.id == taskId }?.let { cancel(it) } ?: false
    }

    fun restart(task: Task)

    /** Enqueue a [Task] with an empty [Task.State] */
    fun enqueue(task: Task)

    fun enqueue(task: Task, state: Task.State)

    fun enqueue(taskWithState: TaskFactory.TaskWithState) {
        val (task, state) = taskWithState
        enqueue(task, state)
    }

    fun remove(task: Task): Boolean
}

internal object FakeDownloaderV2 : DownloaderV2 {
    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
        return mutableStateMapOf()
    }

    override fun cancel(task: Task): Boolean {
        return false
    }

    override fun restart(task: Task) {}

    override fun enqueue(task: Task) {}

    override fun enqueue(task: Task, state: Task.State) {}

    override fun remove(task: Task): Boolean {
        return true
    }
}

/**
 * TODO:
 *     - Notification
 *     - Custom commands
 *     - States for ViewModels
 */
@OptIn(FlowPreview::class)
class DownloaderV2Impl(private val appContext: Context) : DownloaderV2, KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskStateMap = mutableStateMapOf<Task, Task.State>()
    private val snapshotFlow = snapshotFlow { taskStateMap.toMap() }

    init {
        scope.launch(Dispatchers.Default) {
            snapshotFlow
                .onEach {
                    // A single unexpected task-state transition (e.g. resuming a
                    // custom-command task) must never kill the scheduler coroutine,
                    // otherwise the whole download queue freezes forever with tasks
                    // stuck on "Downloading".
                    runCatching { doYourWork() }.onFailure { throwable ->
                        Log.e(TAG, "download scheduler failed", throwable)
                    }
                }
                .map { it.countRunning() }
                .distinctUntilChanged()
                .collect { if (it > 0) App.startService() else App.stopService() }
        }

        scope.launch(Dispatchers.IO) {
            // don't write before we read
            enqueueFromBackup()

            snapshotFlow
                .map { it.filter { it.value.downloadState !is Completed } }
                .distinctUntilChanged()
                .collect {
                    it.forEach { Log.d(TAG, it.value.viewState.title) }
                    PreferenceUtil.encodeTaskListBackup(it)
                }
        }
    }

    private fun enqueueFromBackup() {
        val taskList =
            PreferenceUtil.decodeTaskListBackup()
                .filter { it.value.downloadState !is Completed }
                .mapValues { (_, state) ->
                    val preState = state.downloadState
                    val downloadState =
                        when (preState) {
                            is FetchingInfo,
                            Idle -> {
                                Canceled(action = FetchInfo)
                            }
                            is Running -> {
                                Canceled(action = Download, progress = preState.progress)
                            }

                            ReadyWithInfo -> {
                                Canceled(action = Download, progress = null)
                            }
                            else -> {
                                preState
                            }
                        }
                    state.copy(downloadState = downloadState)
                }
        taskList.forEach(::enqueue)
    }

    private fun Map<Task, Task.State>.countRunning(): Int = count { (_, state) ->
        state.downloadState is Running || state.downloadState is FetchingInfo
    }

    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
        return taskStateMap
    }

    override fun enqueue(task: Task) {
        // Route through the two-arg overload so a running duplicate of the same
        // task is cancelled first. Enqueuing a second instance of a running task
        // with a fresh state would otherwise start a second yt-dlp process with
        // the same process id, which fails with "Process ID already exists".
        enqueue(task, Task.State(Idle, null, Task.ViewState(url = task.url, title = task.url)))
    }

    override fun enqueue(task: Task, state: Task.State) {
        // Cancel any running instance of the same task before (re-)enqueuing it
        taskStateMap[task]?.let { existingState ->
            if (
                existingState.downloadState is Running ||
                    existingState.downloadState is FetchingInfo
            ) {
                runCatching { task.cancelImpl() }
            }
        }
        taskStateMap += task to state
    }

    /**
     * Noted the caller is responsible for stopping the [task] before removing it
     *
     * @return true if the task was removed
     */
    override fun remove(task: Task): Boolean {
        if (taskStateMap.contains(task)) {
            taskStateMap.remove(task)
            return true
        }
        return false
    }

    override fun cancel(task: Task): Boolean = task.cancelImpl()

    override fun restart(task: Task) {
        task.restartImpl()
    }

    private var Task.state: Task.State
        get() =
            taskStateMap[this]
                ?: Task.State(Idle, null, Task.ViewState(url = this.url, title = this.url))
        set(value) {
            // Don't resurrect a task that was removed while its download
            // coroutine was still finishing up (e.g. writing its final state).
            if (!taskStateMap.contains(this)) return
            taskStateMap[this] = value
        }

    private var Task.downloadState: DownloadState
        get() = state.downloadState
        set(value) {
            state = state.copy(downloadState = value)
        }

    private var Task.info: VideoInfo?
        get() = state.videoInfo
        set(value) {
            state = state.copy(videoInfo = value)
        }

    private var Task.viewState: Task.ViewState
        get() = state.viewState
        set(value) {
            state = state.copy(viewState = value)
        }

    private val Task.notificationId: Int
        get() = id.toStableNotificationId()

    /** Processes pending tasks, prioritizing downloads. */
    private fun doYourWork() {
        if (taskStateMap.countRunning() >= MAX_CONCURRENCY) return

        taskStateMap.entries
            .sortedBy { (_, state) -> state.downloadState }
            .firstOrNull { (_, state) ->
                state.downloadState == ReadyWithInfo || state.downloadState == Idle
            }
            ?.let { (task, state) ->
                when (state.downloadState) {
                    Idle -> task.prepare()
                    ReadyWithInfo -> task.download()
                    // Can only happen through a race with a concurrent state
                    // change; log it instead of crashing the scheduler.
                    else -> Log.w(TAG, "unexpected task state in scheduler: ${state.downloadState}")
                }
            }
    }

    private fun Task.prepare() {
        check(downloadState == Idle)
        if (type is TypeInfo.CustomCommand) {
            execute()
        } else {
            fetchInfo()
        }
    }

    private fun Task.fetchInfo() {
        if (downloadState != Idle) return
        val task = this
        val taskInfo = task.type
        val playlistIndex = if (taskInfo is TypeInfo.Playlist) taskInfo.index else null
        scope
            // Blocking yt-dlp process waits belong on IO, not on the small
            // Default pool: on low-core devices saturated Default threads starve
            // the scheduler collector and progress freezes.
            .launch(Dispatchers.IO) {
                DownloadUtil.fetchVideoInfoFromUrl(
                        url = url,
                        playlistIndex = playlistIndex,
                        preferences = preferences,
                        taskKey = id,
                    )
                    .onSuccess {
                        info = it
                        downloadState = ReadyWithInfo
                        viewState = Task.ViewState.fromVideoInfo(it)
                    }
                    .onFailure { throwable ->
                        if (throwable is YoutubeDL.CanceledException) {
                            return@onFailure
                        }
                        task.downloadState = Error(throwable = throwable, action = FetchInfo)
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.fetch_info_error_msg,
                            notificationId = notificationId,
                            report = throwable.stackTraceToString(),
                        )
                    }
            }
            .also { job -> downloadState = FetchingInfo(job = job, taskId = id) }
    }

    private fun Task.download() {
        // Resuming a canceled/error task lands here with the state already set
        // to ReadyWithInfo by restartImpl(); a strict check() would throw and
        // kill the scheduler. Guard instead of crash.
        if (downloadState != ReadyWithInfo) return
        if (info == null) {
            // Task restored from backup without a serializable VideoInfo:
            // silently returning would leave it stuck on ReadyWithInfo forever
            // (the scheduler would re-pick it every cycle and do nothing).
            // Fall back to re-fetching the info instead.
            downloadState = Idle
            return
        }
        if (type is TypeInfo.CustomCommand) {
            execute()
            return
        }
        scope
            .launch(Dispatchers.IO) {
                // Throttle notification spam: yt-dlp invokes this callback for
                // every output line (download ticks, merger, post-processing),
                // so refresh the notification at most twice per second.
                var lastNotifyTime = 0L
                DownloadUtil.downloadVideo(
                        videoInfo = info,
                        taskId = id,
                        downloadPreferences = preferences,
                        progressCallback = { progressPercentage, _, text ->
                            when (val preState = downloadState) {
                                is Running -> {
                                    // Lines that don't report progress (e.g.
                                    // "[Merger]", "[ExtractAudio]") arrive as -1;
                                    // keep the status text but never regress the
                                    // progress bar.
                                    if (progressPercentage >= 0f) {
                                        downloadState =
                                            preState.copy(
                                                progress = progressPercentage / 100f,
                                                progressText = text,
                                            )
                                    } else if (preState.progressText != text) {
                                        downloadState = preState.copy(progressText = text)
                                    }
                                    // Only post a notification for lines with real
                                    // numeric progress: -1 would render a bogus 0%
                                    // determinate bar in the notification shade.
                                    val now = System.currentTimeMillis()
                                    if (progressPercentage >= 0f && now - lastNotifyTime >= 500) {
                                        lastNotifyTime = now
                                        NotificationUtil.notifyProgress(
                                            notificationId = notificationId,
                                            progress = progressPercentage.toInt(),
                                            text = text,
                                            title = viewState.title,
                                            taskId = id,
                                        )
                                    }
                                }
                                else -> {}
                            }
                        },
                    )
                    .onSuccess { pathList ->
                        downloadState = Completed(pathList.firstOrNull())

                        val text =
                            appContext.getString(
                                if (pathList.isEmpty()) R.string.status_completed
                                else R.string.download_finish_notification
                            )
                        FileUtil.createIntentForOpeningFile(pathList.firstOrNull()).run {
                            NotificationUtil.finishNotification(
                                notificationId,
                                title = viewState.title,
                                text = text,
                                intent =
                                    if (this != null)
                                        PendingIntent.getActivity(
                                            appContext,
                                            0,
                                            this,
                                            PendingIntent.FLAG_IMMUTABLE,
                                        )
                                    else null,
                            )
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is YoutubeDL.CanceledException) {
                            return@onFailure
                        }
                        downloadState = Error(throwable = throwable, action = Download)
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.download_error_msg,
                            notificationId = notificationId,
                            report = throwable.stackTraceToString(),
                        )
                    }
            }
            .also { job -> downloadState = Running(job = job, taskId = id) }
    }

    private fun Task.cancelImpl(): Boolean {
        when (val preState = downloadState) {
            is DownloadState.Cancelable -> {
                val res = YoutubeDL.destroyProcessById(preState.taskId)
                if (res) {
                    preState.job.cancel()
                    val progress = if (preState is Running) preState.progress else null
                    NotificationUtil.cancelNotification(notificationId)
                    downloadState =
                        DownloadState.Canceled(action = preState.action, progress = progress)
                }
                return res
            }
            Idle -> {
                downloadState = DownloadState.Canceled(action = FetchInfo)
            }
            ReadyWithInfo -> {
                downloadState = DownloadState.Canceled(action = Download)
            }

            else -> {
                return false
            }
        }
        return true
    }

    private fun Task.restartImpl() {
        when (val preState = downloadState) {
            is DownloadState.Restartable -> {
                downloadState =
                    when (preState.action) {
                        Download -> ReadyWithInfo
                        FetchInfo -> Idle
                    }
            }
            // Never crash the caller (and the UI) for a stale restart request.
            else -> Log.w(TAG, "restart() on non-restartable state: $preState")
        }
    }

    /**
     * Execute a custom command task
     *
     * @see Task.TypeInfo.CustomCommand
     */
    private fun Task.execute() {
        // Called from prepare() (state Idle) and from download() when resuming
        // (state ReadyWithInfo); a strict Idle-only check() made resuming a
        // custom-command task throw and kill the whole scheduler.
        if (downloadState is Running || downloadState is FetchingInfo) return
        check(type is TypeInfo.CustomCommand)
        val template = type.template
        scope
            .launch {
                DownloadUtil.executeCustomCommandTask(url, id, template, preferences) {
                        progressPercentage,
                        _,
                        text ->
                        when (val preState = downloadState) {
                            is Running -> {
                                // Same guard as download(): lines without a
                                // numeric progress report -1 — keep the text but
                                // never regress the progress bar.
                                if (progressPercentage >= 0f) {
                                    downloadState =
                                        preState.copy(
                                            progress = progressPercentage / 100f,
                                            progressText = text,
                                        )
                                } else if (preState.progressText != text) {
                                    downloadState = preState.copy(progressText = text)
                                }
                                NotificationUtil.makeNotificationForCustomCommand(
                                    notificationId = notificationId,
                                    taskId = id,
                                    progress = progressPercentage.toInt(),
                                    templateName = template.name,
                                    taskUrl = url,
                                    text = text,
                                )
                            }
                            else -> {}
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is YoutubeDL.CanceledException) {
                            return@onFailure
                        }
                        downloadState = Error(throwable = throwable, action = Download)
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.download_error_msg,
                            notificationId = notificationId,
                            report = throwable.stackTraceToString(),
                        )
                    }
                    .onSuccess {
                        downloadState = Completed(null)

                        val text = appContext.getString(R.string.status_completed)

                        NotificationUtil.finishNotification(
                            notificationId = notificationId,
                            title = viewState.title,
                            text = text,
                            intent = null,
                        )
                    }
            }
            .also { downloadState = Running(job = it, taskId = id) }
    }
}
