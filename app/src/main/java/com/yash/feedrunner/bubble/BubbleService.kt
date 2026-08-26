package com.yash.feedrunner.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import com.yash.feedrunner.capture.AutoScrollCapture
import com.yash.feedrunner.capture.CaptureService
import com.yash.feedrunner.data.ReadState
import com.yash.feedrunner.data.IdeaBankRepository
import com.yash.feedrunner.data.PlatformStore
import com.yash.feedrunner.ui.Platform
import com.yash.feedrunner.data.ResultStore
import com.yash.feedrunner.ui.MenuAnchor
import com.yash.feedrunner.ui.MenuController
import com.yash.feedrunner.ui.PanelController
import com.yash.feedrunner.ui.RepostController
import com.yash.feedrunner.work.AnalysisManager
import kotlin.math.abs

/**
 * Foreground service that hosts the floating bubble over other apps.
 *
 * Tapping the bubble opens a three-action menu: Capture (this screen),
 * Hold (auto-scroll and stitch), and Last result (reopen the stored analysis
 * with no capture and no API call). Dragging moves the bubble.
 */
class BubbleService : Service() {

    private lateinit var windowManager: WindowManager

    /** Outer window-sized container; also the drag target. */
    private var bubbleView: View? = null

    /** The visible gradient circle inside [bubbleView]. */
    private var bubbleCircle: FrameLayout? = null
    private var bubbleIcon: ImageView? = null
    private var bubbleCount: TextView? = null

    /** Small corner badge showing results generated but not yet opened. */
    private var bubbleBadge: TextView? = null

    /** Layout params of the bubble window — also used to anchor the action menu. */
    private var bubbleParams: WindowManager.LayoutParams? = null

    /** Non-null while an auto-scroll capture ("Hold") is running. */
    private var autoCapture: AutoScrollCapture? = null

    /** Frames captured so far by an active Hold, or null when not capturing. */
    private var holdFrames: Int? = null

    /** Analyses currently in flight, shown on the bubble. */
    private var pendingAnalyses = 0

    // Four unrelated things want the bubble out of the way, and they overlap: a
    // panel can open over held repost drafts, a capture can start from either.
    // Each sets its own flag and one function decides, so closing one thing can
    // never bring the bubble back while another still needs it gone.
    private var panelUp = false
    private var composerUp = false
    private var capturing = false
    private var ownUiUp = false

    private lateinit var resultStore: ResultStore
    private lateinit var readState: ReadState
    private lateinit var analysisManager: AnalysisManager
    private lateinit var panelController: PanelController
    private lateinit var repostController: RepostController
    private lateinit var menuController: MenuController
    private lateinit var platformStore: PlatformStore

    /** Platform under Hold's stitched capture, chosen when the Hold started. */
    private var holdPlatform = Platform.X

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running.value = true
        ownUiUp = ownScreens > 0
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        resultStore = ResultStore(this)
        readState = ReadState(this)
        analysisManager = AnalysisManager(this, resultStore, IdeaBankRepository(this))
        panelController = PanelController(
            this,
            windowManager,
            resultStore,
            analysisManager,
        ) { panelVisible ->
            // Hide the bubble while the panel is up so it doesn't sit on the scrim.
            panelUp = panelVisible
            applyBubbleVisibility()
            if (!panelVisible) refreshUnreadBadge()
        }

        analysisManager.onActiveCountChanged = { count ->
            pendingAnalyses = count
            refreshBubbleBadge()
        }
        analysisManager.onUpdate = ::onAnalysisUpdate
        repostController = RepostController(this, windowManager) { visible ->
            composerUp = visible
            applyBubbleVisibility()
        }
        platformStore = PlatformStore(this)
        menuController = MenuController(
            context = this,
            windowManager = windowManager,
            resultStore = resultStore,
            onCapture = ::startSingleCapture,
            onHold = ::startAutoCapture,
            onRepost = ::startRepostCapture,
            repostDraftsAge = { repostController.heldResultAge.takeIf { _ ->
                repostController.hasUnseenResult
            } },
            onLastResult = { panelController.showLastResult() },
            onPlatformChosen = { platformStore.last = it },
        )
        startForeground(NOTIFICATION_ID, buildNotification())
        addBubble()
        // The view is created visible, so apply the state the flags already hold:
        // started from our own screen, the bubble should not appear on top of it.
        applyBubbleVisibility()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_OWN_UI -> {
                ownUiUp = intent.getBooleanExtra(EXTRA_VISIBLE, false)
                applyBubbleVisibility()
            }
        }
        return START_STICKY
    }

    /**
     * INVISIBLE rather than GONE during a capture: the window keeps its place so
     * the screenshot is taken with the same layout the user was looking at.
     */
    private fun applyBubbleVisibility() {
        val view = bubbleView ?: return
        val wanted = when {
            capturing -> View.INVISIBLE
            panelUp || composerUp || ownUiUp -> View.GONE
            else -> View.VISIBLE
        }
        view.visibility = wanted
    }

    /**
     * A finished job goes to the panel if it is still watching that job, and to
     * a toast otherwise, so a result generated while scrolling is never silent.
     */
    private fun onAnalysisUpdate(update: AnalysisManager.Update) {
        val watched = panelController.isWatching(update.jobId)
        when (update) {
            is AnalysisManager.Update.Done -> {
                if (watched) {
                    panelController.showFinished(update.resultId)
                } else {
                    val who = update.author.takeIf { it.isNotBlank() } ?: "that post"
                    Toast.makeText(this, "Drafts ready for $who", Toast.LENGTH_SHORT).show()
                }
                // Showing it in the panel marks it read, so recount either way.
                refreshUnreadBadge()
            }

            is AnalysisManager.Update.Failed -> {
                if (watched) {
                    panelController.showFailure(update.message)
                } else {
                    Toast.makeText(this, update.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        running.value = false
        autoCapture?.stop()
        analysisManager.shutdown()
        menuController.dismiss()
        repostController.shutdown()
        panelController.shutdown()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        bubbleParams = null
        super.onDestroy()
    }

    private fun addBubble() {
        // The window is larger than the visible circle so the drop shadow has
        // room to render instead of being clipped at the window edge.
        val windowSize = dp(BUBBLE_WINDOW_DP)
        val circleSize = dp(BUBBLE_CIRCLE_DP)

        val params = WindowManager.LayoutParams(
            windowSize,
            windowSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - windowSize - dp(4f)
            y = resources.displayMetrics.heightPixels / 3
        }

        val root = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        val circle = FrameLayout(this).apply {
            setBackgroundResource(com.yash.feedrunner.R.drawable.bubble_bg)
            elevation = dp(8f).toFloat()
        }
        root.addView(
            circle,
            FrameLayout.LayoutParams(circleSize, circleSize, Gravity.CENTER),
        )

        val icon = ImageView(this).apply {
            setImageResource(com.yash.feedrunner.R.drawable.ic_send_bubble)
        }
        circle.addView(
            icon,
            FrameLayout.LayoutParams(dp(25f), dp(25f), Gravity.CENTER),
        )

        // Shown in place of the icon while a Hold capture is counting frames.
        val count = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            visibility = View.GONE
        }
        circle.addView(
            count,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val badge = TextView(this).apply {
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(com.yash.feedrunner.R.drawable.badge_bg)
            // Above the circle's own elevation so it is never drawn underneath.
            elevation = dp(10f).toFloat()
            visibility = View.GONE
        }
        root.addView(
            badge,
            FrameLayout.LayoutParams(dp(20f), dp(20f), Gravity.TOP or Gravity.END),
        )

        root.setOnTouchListener(DragTouchListener(params, onTap = ::onBubbleTapped))

        windowManager.addView(root, params)
        bubbleView = root
        bubbleCircle = circle
        bubbleIcon = icon
        bubbleCount = count
        bubbleBadge = badge
        bubbleParams = params
        refreshUnreadBadge()
    }

    private fun onBubbleTapped() {
        // The tap should be felt on the bubble itself: a quick dip and back.
        bubbleCircle?.animate()
            ?.scaleX(0.86f)?.scaleY(0.86f)
            ?.setDuration(80)
            ?.withEndAction {
                bubbleCircle?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
            }
            ?.start()

        val active = autoCapture
        if (active != null) {
            // Tap while auto-capturing = stop and stitch what we have.
            active.stop()
            return
        }
        if (menuController.isShowing) {
            menuController.dismiss()
            return
        }
        val params = bubbleParams ?: return
        val bubbleSize = bubbleView?.width?.takeIf { it > 0 } ?: dp(BUBBLE_WINDOW_DP)
        val screenWidth = resources.displayMetrics.widthPixels
        menuController.show(
            anchor = MenuAnchor(
                bubbleX = params.x,
                bubbleY = params.y,
                bubbleSize = bubbleSize,
                dockedRight = params.x + bubbleSize / 2 > screenWidth / 2,
                screenWidth = screenWidth,
                screenHeight = resources.displayMetrics.heightPixels,
            ),
            initialPlatform = currentPlatform(),
        )
    }

    /**
     * The platform under the bubble right now: the foreground app when it is X
     * or LinkedIn, otherwise whatever was chosen or used last. The menu shows
     * this and lets one tap override it.
     */
    private fun currentPlatform(): Platform =
        Platform.fromPackage(CaptureService.lastForegroundPackage) ?: platformStore.last

    private fun startSingleCapture(platform: Platform) {
        platformStore.last = platform
        takeScreenshotThen { bitmap ->
            // PanelController owns the bitmap from here (thumbnail, then recycle).
            panelController.analyze(bitmap, platform)
        }
    }

    /**
     * Opens the compose sheet. If a generation finished while the sheet was
     * closed, that result is shown instead of taking a new capture, so the work
     * is never stranded.
     */
    private fun startRepostCapture(platform: Platform) {
        platformStore.last = platform
        // Only an unseen hand-off pre-empts a capture. Older drafts are offered
        // inside the composer instead, so Repost always means "caption what I am
        // looking at now".
        if (repostController.hasUnseenResult) {
            repostController.showHeldResult()
            return
        }
        takeScreenshotThen { bitmap -> repostController.start(bitmap, platform) }
    }

    private fun startAutoCapture(platform: Platform) {
        platformStore.last = platform
        holdPlatform = platform
        val captureService = CaptureService.instance
        if (captureService == null) {
            promptEnableCaptureService()
            return
        }

        autoCapture = AutoScrollCapture(
            service = captureService,
            statusBarPx = statusBarHeightPx(),
            screenHeightPx = resources.displayMetrics.heightPixels,
            hideBubble = { onHidden ->
                capturing = true
                applyBubbleVisibility()
                bubbleView?.postDelayed(onHidden, 150) ?: onHidden()
            },
            showBubble = {
                capturing = false
                applyBubbleVisibility()
            },
            onProgress = { frames ->
                holdFrames = frames
                refreshBubbleBadge()
            },
            onFinished = { stitched, frames ->
                autoCapture = null
                holdFrames = null
                refreshBubbleBadge()
                if (stitched == null) {
                    Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show()
                } else {
                    // Milestone 3: slice the stitched image into readable segments
                    // before sending — very tall images get downscaled by the API.
                    panelController.analyze(stitched, holdPlatform)
                }
            },
        ).also { it.start() }
    }

    private fun promptEnableCaptureService() {
        Toast.makeText(
            this,
            "Enable Feed Runner in Accessibility settings first",
            Toast.LENGTH_LONG,
        ).show()
        startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun statusBarHeightPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(28f)
    }

    /** Hides the bubble, captures the screen, restores the bubble, and delivers the bitmap. */
    private fun takeScreenshotThen(onBitmap: (Bitmap) -> Unit) {
        val captureService = CaptureService.instance
        if (captureService == null) {
            promptEnableCaptureService()
            return
        }

        val bubble = bubbleView ?: return
        bubble.visibility = View.INVISIBLE
        bubble.postDelayed({
            captureService.capture { bitmap, errorCode ->
                bubble.visibility = View.VISIBLE
                if (bitmap == null) {
                    val hint = if (errorCode == 3) "tapped too fast, wait a second" else "code $errorCode"
                    Toast.makeText(this, "Capture failed ($hint)", Toast.LENGTH_SHORT).show()
                } else {
                    onBitmap(bitmap)
                }
            }
        }, 150)
    }

    /**
     * Spark icon when idle, a coral count while a Hold is capturing, and an amber
     * count while analyses run in the background. Capturing wins when both are
     * true, because that one is about to end.
     */
    /** Counts results saved since the last one you opened. */
    private fun refreshUnreadBadge() {
        if (bubbleBadge == null) return
        val watermark = readState.lastViewedAt
        // The count needs the results file parsed; off the main thread, because
        // this runs right after taps and analysis updates, where a stutter shows.
        badgeWorker.execute {
            val unread = resultStore.loadAll().count { it.savedAtMillis > watermark }
            mainHandler.post {
                val badge = bubbleBadge ?: return@post
                if (unread == 0) {
                    badge.visibility = View.GONE
                } else {
                    badge.text = if (unread > 9) "9+" else unread.toString()
                    badge.visibility = View.VISIBLE
                }
            }
        }
    }

    private val badgeWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun refreshBubbleBadge() {
        val circle = bubbleCircle ?: return
        val frames = holdFrames
        val badge = frames ?: pendingAnalyses.takeIf { it > 0 }

        if (badge == null) {
            circle.setBackgroundResource(com.yash.feedrunner.R.drawable.bubble_bg)
            bubbleIcon?.visibility = View.VISIBLE
            bubbleCount?.visibility = View.GONE
            return
        }

        circle.setBackgroundResource(
            if (frames != null) {
                com.yash.feedrunner.R.drawable.bubble_bg_recording
            } else {
                com.yash.feedrunner.R.drawable.bubble_bg_working
            },
        )
        bubbleIcon?.visibility = View.GONE
        bubbleCount?.apply {
            text = badge.toString()
            visibility = View.VISIBLE
        }
    }



    /**
     * Tap opens the action menu; dragging moves the bubble and snaps it to the
     * nearest edge. Touch slop keeps a sloppy tap from being read as a drag.
     */
    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams,
        private val onTap: () -> Unit,
    ) : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(this@BubbleService).scaledTouchSlop
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        windowManager.updateViewLayout(v, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) snapToEdge(v) else onTap()
                    return true
                }
            }
            return false
        }

        private fun snapToEdge(v: View) {
            val screenWidth = resources.displayMetrics.widthPixels
            val margin = dp(4f)
            params.x = if (params.x + v.width / 2 < screenWidth / 2) {
                margin
            } else {
                screenWidth - v.width - margin
            }
            windowManager.updateViewLayout(v, params)
        }
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bubble",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("Feed Runner active")
            .setContentText("Bubble is floating over your screen")
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics,
    ).toInt()

    companion object {
        const val ACTION_STOP = "com.yash.feedrunner.STOP_BUBBLE"
        private const val ACTION_OWN_UI = "com.yash.feedrunner.OWN_UI"
        private const val EXTRA_VISIBLE = "visible"
        private const val CHANNEL_ID = "bubble"
        private const val NOTIFICATION_ID = 1

        /** Visible circle, and the larger window that leaves room for its shadow. */
        private const val BUBBLE_CIRCLE_DP = 52f
        private const val BUBBLE_WINDOW_DP = 68f

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BubbleService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BubbleService::class.java).setAction(ACTION_STOP),
            )
        }

        /**
         * Whether the bubble is up. Compose state rather than a plain flag so the
         * setup screen can say so without polling.
         */
        val running = mutableStateOf(false)

        /**
         * How many of our own screens are on top. Counted rather than a flag so
         * moving between the setup screen and Ideas, where the next screen resumes
         * before the last one pauses, does not flash the bubble in between.
         *
         * Lives in the same process as the service, so if the process is restarted
         * both this and the service start from nothing and the bubble is visible.
         */
        private var ownScreens = 0

        /** Called by our own screens as they come and go. The bubble is for other apps. */
        fun setOwnUiVisible(context: Context, visible: Boolean) {
            ownScreens = (ownScreens + if (visible) 1 else -1).coerceAtLeast(0)
            // Nothing to tell when the bubble is off. It reads the count itself when
            // it starts, so starting it from our own screen does not put it on top
            // of that screen.
            if (!running.value) return
            context.startService(
                Intent(context, BubbleService::class.java)
                    .setAction(ACTION_OWN_UI)
                    .putExtra(EXTRA_VISIBLE, ownScreens > 0),
            )
        }
    }
}
