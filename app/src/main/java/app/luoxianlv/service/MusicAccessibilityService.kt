package app.luoxianlv.service
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.hardware.display.DisplayManager
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import app.luoxianlv.core.playback.PlaybackTimeline
import app.luoxianlv.core.score.NoteEvent
import app.luoxianlv.core.score.PlayMode
import app.luoxianlv.data.ConfigStore
import app.luoxianlv.data.KeyLayout
import app.luoxianlv.data.Song
import app.luoxianlv.data.SongRepository
import app.luoxianlv.profile.ScreenRecognizer
import app.luoxianlv.update.HotUpdateCoordinator

class MusicAccessibilityService : AccessibilityService() {
    companion object {
        var instance: MusicAccessibilityService? = null
            private set
        const val TAG = "落弦律Gesture"

        /** 系统无障碍设置中本服务是否已开启（instance 只在服务运行期间非空，不能用于判断）。
         * 走 AccessibilityManager 已启用服务列表按包名匹配：
         * Settings.Secure 字符串存在全类名/短类名两种格式，逐字比对在部分 ROM 上会误判。 */
        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            return manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: SongRepository
    private lateinit var keys: KeyLayout
    private lateinit var floating: FloatingControls
    private lateinit var hotUpdates: HotUpdateCoordinator
    lateinit var song: Song
        private set
    private lateinit var timeline: PlaybackTimeline
    var playing = false
        private set

    /** True while the pre-play screenshot recognition is in flight. */
    var preparing = false
        private set
    var error: String? = null
        private set
    var speed = 1f
        private set
    private var baseMs = 0L
    private var anchor = 0L
    private var generation = 0
    private var busy = false
    private var gestureFailure: String? = null
    private var halfToneOn = false
    private var pitchMode = PlayMode.NATURAL
    private var playbackDisplay: Triple<Int, Int, Int>? = null
    private var recoveringDisplay = false
    private var recoveryAttempts = 0
    private var recoveryWasPlaying = false
    private val displayStability = DisplayStability()
    private val monitorDisplay = object : Runnable {
        override fun run() {
            if ((playing || preparing) && !recoveringDisplay && playbackDisplay != displayState()) beginDisplayRecovery()
            handler.postDelayed(this, 150)
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY && (playing || preparing) && playbackDisplay != displayState()) {
                beginDisplayRecovery()
            }
        }
    }

    private fun beginDisplayRecovery() {
        if (recoveringDisplay) return
        recoveryWasPlaying = playing || preparing
        baseMs = positionMs
        playing = false
        preparing = true
        busy = false
        generation++
        handler.removeCallbacks(next)
        recoveringDisplay = true
        recoveryAttempts = 0
        displayStability.reset()
        error = "屏幕方向变化，正在重新识别…"
        floating.refresh()
        handler.post(recoverDisplay)
    }

    private val recoverDisplay = object : Runnable {
        override fun run() {
            if (!recoveringDisplay) return
            val current = displayState()
            recoveryAttempts++
            // The logical display and screenshot producer settle at different times.
            if (!displayStability.ready(current, SystemClock.uptimeMillis())) {
                handler.postDelayed(this, 120)
                return
            }
            playbackDisplay = current
            val token = ++generation
            var completed = false
            val timeout = Runnable {
                if (token == generation && recoveringDisplay && !completed) {
                    completed = true
                    generation++
                    handler.postDelayed(this, 500)
                }
            }
            handler.postDelayed(timeout, 2000)
            syncWithScreen(token) { recognized ->
                if (token != generation || !recoveringDisplay || completed) return@syncWithScreen
                completed = true
                handler.removeCallbacks(timeout)
                if (recognized) finishDisplayRecovery(recoveryWasPlaying)
                else handler.postDelayed(this, if (recoveryAttempts < 10) 350 else 1000)
            }
        }
    }

    private fun finishDisplayRecovery(resume: Boolean) {
        recoveringDisplay = false
        preparing = false
        handler.removeCallbacks(recoverDisplay)
        if (resume && timeline.events.isNotEmpty()) {
            error = null
            playing = true
            anchor = SystemClock.uptimeMillis()
            drive()
        } else {
            playing = false
            error = if (resume) "屏幕识别失败，请重试" else null
        }
        floating.refresh()
    }

    private fun displayState(): Triple<Int, Int, Int> {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        return Triple(size.x, size.y, display.rotation)
    }
    val durationMs get() = timeline.durationMs
    val positionMs get() = (baseMs + if (playing) ((SystemClock.uptimeMillis() - anchor) * speed).toLong() else 0).coerceIn(0, durationMs)
    val modeLabel get() = pitchMode.label + if (halfToneOn) " · 半音" else ""
    private val next = Runnable { drive() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = SongRepository(this)
        keys = ConfigStore.load(this)
        song = repository.selected()
        timeline = PlaybackTimeline(song.events, song.bpm)
        speed = repository.speed
        floating = FloatingControls(this)
        hotUpdates = HotUpdateCoordinator(this, repository)
        instance = this
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, handler)
        handler.post(monitorDisplay)
        // Content updates are intentionally silent and run whenever the
        // accessibility service reconnects. Updated songs/layouts are picked
        // up by the existing repository and calibration store immediately.
        hotUpdates.check {
            handler.post {
                song = repository.selected()
                timeline = PlaybackTimeline(song.events, song.bpm)
                floating.refresh()
            }
        }
        if (repository.floatingEnabled) handler.postDelayed({ floating.show() }, 250)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = pause()

    override fun onDestroy() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        playing = false
        generation++
        handler.removeCallbacksAndMessages(null)
        recoveringDisplay = false
        if (::floating.isInitialized) floating.destroy()
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun select(selected: Song) {
        pause()
        song = selected
        repository.selectedId = selected.id
        timeline = PlaybackTimeline(selected.events, selected.bpm)
        baseMs = 0
        error = null
        floating.refresh()
    }

    fun toggle() {
        if (playing || preparing) pause() else play()
    }

    fun play() {
        if (playing || preparing || recoveringDisplay || timeline.events.isEmpty()) return
        if (baseMs >= durationMs) baseMs = 0
        error = null
        // Sync with the real screen once before the first note: locate the
        // keyboard by image recognition (stored ratios break on tablets and
        // other aspect ratios) and read back the pitch state in case the user
        // toggled 半音/升降调 directly in the game. Screenshot needs API 30;
        // below that the stored layout is used as before.
        playbackDisplay = displayState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            preparing = true
            val token = ++generation
            floating.refresh()
            syncWithScreen(token) { recognized ->
                if (token != generation) return@syncWithScreen
                preparing = false
                if (!recognized && playbackDisplay != displayState()) {
                    preparing = true
                    beginDisplayRecovery()
                    return@syncWithScreen
                }
                if (token == generation) startPlaying() else floating.refresh()
            }
        } else {
            startPlaying()
        }
    }

    private fun startPlaying() {
        if (playing || timeline.events.isEmpty()) return
        playing = true
        generation++
        anchor = SystemClock.uptimeMillis()
        drive()
        floating.refresh()
    }

    /** Takes a screenshot, recognizes the keyboard, persists the layout and
     * syncs the pitch state. The callback runs on the main thread. */
    private fun syncWithScreen(token: Int, done: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            done(false)
            return
        }
        try {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val currentDisplay = displayState()
                    if (token != generation || playbackDisplay != currentDisplay ||
                        screenshot.hardwareBuffer.width != currentDisplay.first ||
                        screenshot.hardwareBuffer.height != currentDisplay.second) {
                        screenshot.hardwareBuffer.close()
                        done(false)
                        return
                    }
                    val result = runCatching {
                        val hardware = try {
                            Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        } finally { screenshot.hardwareBuffer.close() }
                        val bitmap = try { hardware?.copy(Bitmap.Config.ARGB_8888, false) }
                            finally { hardware?.recycle() }
                        try { bitmap?.let(ScreenRecognizer::fromBitmap) }
                        finally { bitmap?.recycle() }
                    }.onFailure { Log.w(TAG, "截图识别失败", it) }.getOrNull()
                    if (result != null) {
                        keys = result.layout
                        ConfigStore.save(this@MusicAccessibilityService, result.layout)
                        result.mode?.let { pitchMode = it }
                        result.halfTone?.let { halfToneOn = it }
                        Log.i(TAG, "按键识别成功 mode=${result.mode} half=${result.halfTone}")
                        floating.refresh()
                    }
                    done(result != null)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "截图失败 errorCode=$errorCode")
                    done(false)
                }
            },
        )
        } catch (failure: Exception) {
            Log.w(TAG, "无法请求截图", failure)
            done(false)
        }
    }

    fun pause() {
        if (recoveringDisplay) {
            generation++
            busy = false
            recoveryWasPlaying = false
            finishDisplayRecovery(false)
            return
        }
        preparing = false
        baseMs = positionMs
        playing = false
        busy = false
        generation++
        handler.removeCallbacks(next)
        if (::floating.isInitialized) floating.refresh()
    }

    fun stop() {
        pause()
        baseMs = 0
        floating.refresh()
    }

    fun seek(milliseconds: Long) {
        val resume = playing
        pause()
        baseMs = milliseconds.coerceIn(0, durationMs)
        if (resume) play() else floating.refresh()
    }

    fun setSpeed(value: Float) {
        val resume = playing
        pause()
        speed = value.coerceIn(.5f, 2f)
        repository.speed = speed
        if (resume) play()
    }

    fun showFloating(enabled: Boolean) {
        repository.floatingEnabled = enabled
        if (enabled) floating.show() else floating.hide()
    }

    fun reloadConfig() {
        keys = ConfigStore.load(this)
    }

    fun screenBounds(): Rect {
        val (width, height) = displayState()
        return Rect(0, 0, width, height)
    }

    private fun drive() {
        handler.removeCallbacks(next)
        if (!playing || busy) return
        if (playbackDisplay != displayState()) {
            beginDisplayRecovery()
            return
        }
        val at = positionMs
        val index = timeline.indexAt(at)
        if (index >= timeline.events.size || at >= durationMs) {
            pause()
            baseMs = durationMs
            return
        }
        val note = timeline.events[index]
        val end = timeline.offsets[index + 1]
        if (note.rest) {
            handler.postDelayed(next, ((end - at) / speed).toLong().coerceAtLeast(1))
            return
        }
        busy = true
        val token = generation
        ensurePitch(note) { success ->
            if (token != generation) return@ensurePitch
            if (!success) {
                finishGesture(false)
                return@ensurePitch
            }
            if (!playing || token != generation) {
                finishGesture(true)
                return@ensurePitch
            }
            val remaining = ((end - positionMs) / speed).toLong()
            if (remaining <= 0) {
                finishGesture(true)
            } else {
                press(keys.noteX[note.keyIndex], keys.noteY, remaining, { playing && token == generation }, ::finishGesture)
            }
        }
    }

    private fun finishGesture(success: Boolean) {
        busy = false
        if (!success) {
            pause()
            error = gestureFailure ?: "手势未完成"
            Log.w(TAG, error ?: "手势未完成")
        } else if (playing) {
            handler.post(next)
        }
    }

    private fun ensurePitch(
        note: NoteEvent,
        done: (Boolean) -> Unit,
    ) {
        val token = generation
        fun half() {
            if (token != generation) return
            if (halfToneOn == note.halfTone) {
                done(true)
            } else {
                control(PlayMode.SEMITONE) { success ->
                    if (token != generation) return@control
                    if (success) halfToneOn = note.halfTone
                    done(success)
                }
            }
        }
        if (pitchMode == note.mode) {
            half()
        } else {
            control(note.mode) { success ->
                if (token != generation) return@control
                if (success) {
                    pitchMode = note.mode
                    half()
                } else {
                    done(false)
                }
            }
        }
    }

    private fun control(
        mode: PlayMode,
        done: (Boolean) -> Unit,
    ) {
        val point = keys.modes.getValue(mode)
        // Pitch controls are toggle taps. Keep the configured 1 ms click so
        // switching remains instantaneous and never turns into a hold.
        press(point[0], point[1], 1, { true }, done)
    }

    private fun press(
        x: Float,
        y: Float,
        duration: Long,
        active: () -> Boolean,
        done: (Boolean) -> Unit,
    ) {
        val currentDisplay = displayState()
        if (playing && playbackDisplay != currentDisplay) {
            beginDisplayRecovery()
            return
        }
        if (!playing || !active() || playbackDisplay != currentDisplay) {
            gestureFailure = "播放已停止或屏幕方向已变化，请重新点击播放"
            done(false)
            return
        }
        val bounds = Rect(0, 0, currentDisplay.first, currentDisplay.second)
        // Clamp normalized coordinates against the active landscape display.
        // This prevents a stale calibration value or a cutout inset from
        // producing an out-of-bounds gesture that Android cancels.
        val px = x.coerceIn(0f, 1f) * (bounds.width() - 1).coerceAtLeast(1)
        val py = y.coerceIn(0f, 1f) * (bounds.height() - 1).coerceAtLeast(1)
        gestureFailure = null
        val path = Path().apply { moveTo(px, py) }
        var completed = false
        val token = generation

        fun finish(value: Boolean) {
            if (token != generation) return
            if (playbackDisplay != displayState()) {
                beginDisplayRecovery()
                return
            }
            if (!completed) {
                completed = true
                done(value)
            }
        }
        // Dispatch one complete static stroke per note. Splitting a long note
        // across several dispatchGesture calls is not supported consistently
        // by Android/OEM accessibility implementations and causes cancellation.
        val length = duration.coerceAtLeast(1)
        val stroke = GestureDescription.StrokeDescription(path, 0, length, false)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted =
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        if (token != generation) return
                        floating.mark(px, py)
                        finish(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        if (token != generation) return
                        gestureFailure = "手势被系统取消 (${px.toInt()},${py.toInt()} / ${bounds.width()}x${bounds.height()})"
                        finish(false)
                    }
                },
                handler,
            )
        if (!accepted) {
            gestureFailure = "系统拒绝手势 (${px.toInt()},${py.toInt()} / ${bounds.width()}x${bounds.height()})"
            finish(false)
        }
    }
}
