package com.example.autoplaymusic.service
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.autoplaymusic.PlayerUi
import com.example.autoplaymusic.PlayerUi.dp
import com.example.autoplaymusic.R
import com.example.autoplaymusic.data.SongRepository
import com.example.autoplaymusic.data.timeLabel
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import kotlin.math.abs

class FloatingControls(
    private val service: MusicAccessibilityService,
) {
    private val context = ContextThemeWrapper(service, R.style.AppTheme)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val prefs = service.getSharedPreferences("floating_position", 0)
    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var popup: View? = null
    private var marker: View? = null
    private var expanded = false
    private var title: TextView? = null
    private var status: TextView? = null
    private var play: MaterialButton? = null
    private var slider: Slider? = null
    private var seeking = false
    private var resumeAfterSeek = false
    private var displayRequested = false
    private var showRetries = 0
    private var x = prefs.getInt("x", context.dp(16))
    private var y = prefs.getInt("y", context.dp(140))
    private val tick =
        object : Runnable {
            override fun run() {
                refresh()
                if (root != null) handler.postDelayed(this, 200)
            }
        }
    private val removeMarker =
        Runnable {
            marker?.let { wm.removeView(it) }
            marker = null
        }

    fun show() {
        displayRequested = true
        showRetries = 0
        if (root != null) return
        handler.post { if (displayRequested && root == null) render(false) }
    }

    fun hide() {
        displayRequested = false
        dismissPlaylist()
        handler.removeCallbacks(tick)
        root?.let { wm.removeView(it) }
        root = null
        title = null
        status = null
        slider = null
        play = null
    }

    fun destroy() {
        hide()
        handler.removeCallbacksAndMessages(null)
        removeMarker.run()
    }

    fun reposition() {
        if (root != null) render(expanded)
    }

    private fun layout(
        width: Int,
        height: Int,
    ) = WindowManager
        .LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun render(open: Boolean) {
        if (!displayRequested) return
        hide()
        displayRequested = true
        expanded = open
        val bounds = service.screenBounds()
        val view: View
        val width: Int
        if (!open) {
            width = context.dp(52)
            view =
                FloatingActionButton(context).apply {
                    customSize = context.dp(48)
                    useCompatPadding = false
                    setImageResource(R.drawable.ic_music_note)
                    backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                    imageTintList = ColorStateList.valueOf(PlayerUi.BLUE)
                    compatElevation = context.dp(3).toFloat()
                    contentDescription = "展开播放器"
                    tooltipText = "展开播放器"
                    setOnClickListener { render(true) }
                }
            attachDrag(view, true)
        } else {
            width = minOf(context.dp(304), bounds.width() - context.dp(16))
            val panel =
                PlayerUi.column(context).apply {
                    setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(1))
                    background = PlayerUi.background(context, 0xcc151b1f.toInt(), 14, true).apply { setStroke(context.dp(1), 0x66ffffff) }
                }
            val header = PlayerUi.row(context)
            title =
                PlayerUi.text(context, service.song.title, 13f, Color.WHITE, bold = true).apply {
                    setPadding(0, context.dp(4), context.dp(8), context.dp(4))
                }
            header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            val collapse =
                PlayerUi.button(context, "×", iconOnly = true).apply {
                    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    setTextColor(0xffc5ceca.toInt())
                    textSize = 18f
                    setOnClickListener { render(false) }
                }
            header.addView(collapse, LinearLayout.LayoutParams(context.dp(34), context.dp(32)))
            panel.addView(header)
            val controls = PlayerUi.row(context)
            play =
                PlayerUi.button(context, "播放", R.drawable.ic_play, iconOnly = true).apply {
                    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    setTextColor(Color.WHITE)
                    iconTint = ColorStateList.valueOf(Color.WHITE)
                    setOnClickListener { service.toggle() }
                }
            controls.addView(play, LinearLayout.LayoutParams(context.dp(34), context.dp(29)))
            val songs =
                PlayerUi.button(context, "歌单", R.drawable.ic_folder_music, iconOnly = true).apply {
                    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    setTextColor(Color.WHITE)
                    iconTint = ColorStateList.valueOf(0xffd8ed6b.toInt())
                    setOnClickListener { if (popup == null) showPlaylist() else dismissPlaylist() }
                }
            controls.addView(songs, LinearLayout.LayoutParams(context.dp(34), context.dp(29)).apply { leftMargin = context.dp(2) })
            status = PlayerUi.text(context, "", 10f, 0xffc5ceca.toInt()).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
            controls.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
            panel.addView(controls, LinearLayout.LayoutParams(-1, context.dp(29)))
            slider =
                Slider(context).apply {
                    valueFrom = 0f
                    valueTo = 1000f
                    setLabelFormatter { timeLabel((it / 1000 * service.durationMs).toLong()) }
                    contentDescription = "播放进度"
                    addOnSliderTouchListener(
                        object : Slider.OnSliderTouchListener {
                            override fun onStartTrackingTouch(slider: Slider) {
                                seeking = true
                                resumeAfterSeek = service.playing
                                service.pause()
                            }

                            override fun onStopTrackingTouch(slider: Slider) {
                                service.seek((slider.value / 1000 * service.durationMs).toLong())
                                seeking = false
                                if (resumeAfterSeek) service.play()
                            }
                        },
                    )
                }
            panel.addView(slider, LinearLayout.LayoutParams(-1, context.dp(23)))
            view = panel
            // Keep the controls interactive. Only the title area acts as the
            // drag handle; attaching the listener to the whole panel would
            // swallow play, playlist and slider touch events.
            attachDrag(title!!, false)
        }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        params =
            layout(width, if (open) -2 else context.dp(52)).apply {
                x = this@FloatingControls.x.coerceIn(0, (bounds.width() - width).coerceAtLeast(0))
                y =
                    this@FloatingControls.y.coerceIn(
                        context.dp(24),
                        (bounds.height() - view.measuredHeight - context.dp(24)).coerceAtLeast(context.dp(24)),
                    )
            }
        try {
            wm.addView(view, params)
            root = view
            showRetries = 0
            handler.post(tick)
        } catch (_: WindowManager.BadTokenException) {
            root = null
            // Some OEMs bind the accessibility window a moment after the
            // callback. Retry once the window token is available.
            retryShow()
        } catch (_: IllegalStateException) {
            root = null
            retryShow()
        }
    }

    private fun retryShow() {
        if (++showRetries <= 3) {
            handler.postDelayed({ if (displayRequested && root == null) render(expanded) }, 500)
        }
    }

    private fun attachDrag(
        handle: View,
        clickable: Boolean,
    ) {
        var sx = 0f
        var sy = 0f
        var bx = 0
        var by = 0
        var moved = false
        handle.setOnTouchListener { v, e ->
            val p = params ?: return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                    bx = p.x
                    by = p.y
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx
                    val dy = e.rawY - sy
                    if (abs(dx) + abs(dy) > ViewConfiguration.get(context).scaledTouchSlop) moved = true
                    if (moved) {
                        dismissPlaylist()
                        val bounds = service.screenBounds()
                        p.x = (bx + dx).toInt().coerceIn(0, (bounds.width() - (root?.width ?: 0)).coerceAtLeast(0))
                        p.y = (by + dy).toInt().coerceIn(0, (bounds.height() - (root?.height ?: 0)).coerceAtLeast(0))
                        root?.let { wm.updateViewLayout(it, p) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        x = p.x
                        y = p.y
                        prefs
                            .edit()
                            .putInt("x", x)
                            .putInt("y", y)
                            .apply()
                    } else if (clickable) {
                        v.performClick()
                    }
                    true
                }

                else -> {
                    true
                }
            }
        }
    }

    fun refresh() {
        title?.text = service.song.title
        status?.text = service.error ?: if (service.preparing) "识别按键中…" else "${service.modeLabel} · ${timeLabel(service.positionMs)}"
        play?.apply {
            setIconResource(if (service.playing) R.drawable.ic_pause else R.drawable.ic_play)
            contentDescription = if (service.playing) "暂停" else "播放"
            tooltipText = contentDescription
        }
        if (!seeking) slider?.value = (service.positionMs.toFloat() / service.durationMs.coerceAtLeast(1) * 1000).coerceIn(0f, 1000f)
    }

    private fun showPlaylist() {
        val anchor = params ?: return
        val list = PlayerUi.column(context).apply { setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8)) }
        SongRepository(service).songs().forEach { song ->
            val button =
                PlayerUi.button(context, song.title).apply {
                    isSingleLine = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
                    backgroundTintList = ColorStateList.valueOf(if (song.id == service.song.id) 0xdd5d78d6.toInt() else 0xcc151b1f.toInt())
                    setTextColor(Color.WHITE)
                    minHeight = context.dp(48)
                    setOnClickListener {
                        service.select(song)
                        dismissPlaylist()
                        refresh()
                    }
                }
            list.addView(button, LinearLayout.LayoutParams(-1, -2))
        }
        val scroll =
            ScrollView(context).apply {
                background = PlayerUi.background(context, 0xdd151b1f.toInt(), 12, true).apply { setStroke(context.dp(1), 0x66ffffff) }
                addView(list)
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_OUTSIDE) {
                        dismissPlaylist()
                        true
                    } else {
                        false
                    }
                }
            }
        val bounds = service.screenBounds()
        val height = minOf(context.dp(240), context.dp(16 + SongRepository(service).songs().size * 64), bounds.height() / 2)
        val p =
            layout(anchor.width, height).apply {
                flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                x = anchor.x
                val below = anchor.y + (root?.height ?: 0) + context.dp(6)
                y = if (below + height < bounds.height()) below else (anchor.y - height - context.dp(6)).coerceAtLeast(0)
            }
        popup = scroll
        wm.addView(scroll, p)
    }

    private fun dismissPlaylist() {
        popup?.let { wm.removeView(it) }
        popup = null
    }

    fun mark(
        x: Float,
        y: Float,
    ) {
        handler.removeCallbacks(removeMarker)
        if (marker == null) {
            marker = View(context).apply { background = PlayerUi.background(context, 0x55007aff, 20, true) }
            val p = layout(context.dp(20), context.dp(20)).apply { flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE }
            wm.addView(marker, p)
        }
        val p = marker!!.layoutParams as WindowManager.LayoutParams
        p.x = x.toInt() - context.dp(10)
        p.y = y.toInt() - context.dp(10)
        wm.updateViewLayout(marker, p)
        handler.postDelayed(removeMarker, 120)
    }
}
