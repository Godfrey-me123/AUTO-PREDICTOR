package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.data.engine.ActionType
import com.example.data.engine.AutomationActionEvent
import com.example.data.engine.AutomationState
import com.example.data.engine.CurrencyManager
import com.example.data.engine.GameDetection
import com.example.data.engine.MultiplierHistoryAnalyzer
import com.example.data.engine.OperatingScheduleEvaluator
import com.example.data.engine.StrategyEngine
import com.example.data.engine.SupportedCurrency
import com.example.data.model.FloatingShortcut
import com.example.data.model.GameState
import com.example.data.model.MultiplierRecord
import com.example.data.model.RiskLimits
import com.example.data.model.SessionStats
import com.example.data.model.ShortcutType
import com.example.data.model.SiteButtonState
import com.example.data.model.StrategyConfig
import com.example.ui.theme.ActionBetGreen
import com.example.ui.theme.ActionCashOutAmber
import com.example.ui.theme.ActionStartEmerald
import com.example.ui.theme.ActionStopRed
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.ScreenEarnerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

open class ScreenEarnerOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "ScreenEarnerOverlay"
        const val CHANNEL_ID = "ScreenEarnerOverlayChannel"
        const val NOTIFICATION_ID = 1001
        const val PREFS_NAME = "screen_earner_prefs"
        const val PREF_BUBBLE_OPACITY = "bubble_opacity"
        const val PREF_BUBBLE_SIZE_DP = "bubble_size_dp"
        const val PREF_HAPTICS_ENABLED = "haptics_enabled"

        var isRunning = false
            private set

        private val _isScreenHeldState = MutableStateFlow(false)
        val isScreenHeldState: StateFlow<Boolean> = _isScreenHeldState

        private val _isCpuActiveState = MutableStateFlow(false)
        val isCpuActiveState: StateFlow<Boolean> = _isCpuActiveState

        private val _awakeErrorState = MutableStateFlow<String?>(null)
        val awakeErrorState: StateFlow<String?> = _awakeErrorState
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var sessionHudView: View? = null
    private var sessionHudParams: WindowManager.LayoutParams? = null

    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var handSettingsView: View? = null
    private var handSettingsParams: WindowManager.LayoutParams? = null
    private var calibrationCatcherView: View? = null

    private data class FloatingHandEntry(
        val shortcutId: Long,
        val type: ShortcutType,
        val view: View,
        val params: WindowManager.LayoutParams
    )

    private val independentHands = mutableListOf<FloatingHandEntry>()
    private var isPanelVisible = false
    private var statusBannerMessage = MutableStateFlow<String?>(null)
    private var wakeLock: PowerManager.WakeLock? = null
    private var isKeepAwakeEngaged = false
    private var lastToastTicker: String = ""
    private var lastToastTimestampMs: Long = 0L

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Display over other apps permission (canDrawOverlays) is missing. Stopping service.")
            Toast.makeText(this, "Display over other apps permission is required.", Toast.LENGTH_LONG).show()
            isRunning = false
            stopSelf()
            return
        }

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        AutomationController.ensureInitialized(applicationContext)
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        AccessibilityController.gestureOverlayPassThroughListener = { isDispatching ->
            setOverlaysPassThrough(isDispatching)
        }
        setupFloatingBubbleOverlay()
        setupSessionStatsHudOverlay()
        observeKeepAwakeAndDiagnostics()
    }

    private fun observeKeepAwakeAndDiagnostics() {
        val engine = AutomationController.engine

        // SECTION 12 (A & C): Screen Awake is directly bound to user preference (keepScreenAwake)
        // regardless of whether automation is running, paused, or idle, so display is held while
        // observing Chrome / BetPawa.
        serviceScope.launch {
            AutomationController.latestConfig
                .map { it.keepScreenAwake }
                .distinctUntilChanged()
                .collect { shouldHoldScreen ->
                    isKeepAwakeEngaged = shouldHoldScreen
                    applyScreenAwakeFlag(shouldHoldScreen)
                }
        }

        // SECTION 12 (B): CPU WakeLock is held strictly when automation is actively running
        // with the display off/on, and released immediately when stopped, paused, or destroyed.
        serviceScope.launch {
            if (engine != null) {
                combine(
                    AutomationController.latestConfig,
                    engine.automationState,
                    engine.isStandbyOutsideSchedule
                ) { _, autoState, standby ->
                    autoState == AutomationState.RUNNING && !standby
                }.distinctUntilChanged().collect { shouldHoldCpu ->
                    applyCpuWakeLock(shouldHoldCpu)
                }
            }
        }

        serviceScope.launch {
            engine?.agentStatusTicker?.collect { ticker ->
                val cfg = AutomationController.latestConfig.value
                val now = System.currentTimeMillis()
                if (cfg.diagnosticToastsEnabled &&
                    ticker.isNotBlank() &&
                    ticker != lastToastTicker &&
                    now - lastToastTimestampMs >= 2200L
                ) {
                    lastToastTicker = ticker
                    lastToastTimestampMs = now
                    try {
                        Toast.makeText(this@ScreenEarnerOverlayService, ticker, Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    private fun applyScreenAwakeFlag(keepOn: Boolean) {
        val wm = windowManager ?: run {
            _isScreenHeldState.value = false
            if (keepOn) _awakeErrorState.value = "WindowManager not ready"
            return
        }

        var anyUpdated = false
        fun applyToView(view: View?, params: WindowManager.LayoutParams?): Boolean {
            if (view == null || params == null) return false
            val updated = if (keepOn) {
                params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            } else {
                params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
            }
            if (params.flags != updated) {
                params.flags = updated
            }
            if (view.isAttachedToWindow) {
                return try {
                    wm.updateViewLayout(view, params)
                    true
                } catch (t: Throwable) {
                    Log.e(TAG, "Error updating FLAG_KEEP_SCREEN_ON on overlay", t)
                    false
                }
            }
            return false
        }

        try {
            if (applyToView(bubbleView, bubbleParams)) anyUpdated = true
            if (applyToView(sessionHudView, sessionHudParams)) anyUpdated = true
            if (applyToView(panelView, panelParams)) anyUpdated = true
            if (applyToView(handSettingsView, handSettingsParams)) anyUpdated = true
            independentHands.forEach { hand ->
                if (applyToView(hand.view, hand.params)) anyUpdated = true
            }

            if (keepOn) {
                if (anyUpdated || bubbleView?.isAttachedToWindow == true || bubbleView != null) {
                    _isScreenHeldState.value = true
                    _awakeErrorState.value = null
                } else {
                    _isScreenHeldState.value = false
                    _awakeErrorState.value = "No attached overlay window"
                }
            } else {
                _isScreenHeldState.value = false
                _awakeErrorState.value = null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed applying keep screen awake flag", t)
            _isScreenHeldState.value = false
            _awakeErrorState.value = "Awake error: ${t.localizedMessage ?: "Unknown"}"
        }
    }

    @Suppress("WakelockTimeout")
    private fun applyCpuWakeLock(shouldHoldCpu: Boolean) {
        try {
            if (shouldHoldCpu) {
                if (wakeLock == null) {
                    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScreenEarner:CpuWakeLock")?.apply {
                        setReferenceCounted(false)
                    }
                }
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(4 * 60 * 60 * 1000L)
                }
                _isCpuActiveState.value = wakeLock?.isHeld == true
            } else {
                releaseWakeLockSafely()
                _isCpuActiveState.value = false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed updating CPU wake lock", t)
            _isCpuActiveState.value = false
        }
    }

    private fun releaseWakeLockSafely() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed releasing wake lock", t)
        }
        _isCpuActiveState.value = false
    }

    private fun updateWindowKeepScreenOnFlags(keepOn: Boolean) {
        applyScreenAwakeFlag(keepOn)
    }

    /**
     * Dynamically toggles FLAG_NOT_TOUCHABLE on all active overlay windows while
     * AccessibilityController dispatches a synthetic tap gesture, preventing floating
     * BET / CASH OUT hands or menus from intercepting their own taps.
     */
    private fun setOverlaysPassThrough(passThrough: Boolean) {
        val wm = windowManager ?: return
        fun updateViewPassThrough(view: View?, params: WindowManager.LayoutParams?) {
            if (view == null || params == null) return
            if (!view.isAttachedToWindow) return
            val updatedFlags = if (passThrough) {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            if (params.flags != updatedFlags) {
                params.flags = updatedFlags
                try {
                    wm.updateViewLayout(view, params)
                } catch (_: Throwable) {
                }
            }
        }

        updateViewPassThrough(bubbleView, bubbleParams)
        updateViewPassThrough(sessionHudView, sessionHudParams)
        updateViewPassThrough(panelView, panelParams)
        updateViewPassThrough(handSettingsView, handSettingsParams)
        independentHands.forEach { hand ->
            updateViewPassThrough(hand.view, hand.params)
        }
    }

    /**
     * FrameLayout container that intercepts drag gestures anywhere outside designated scrollable
     * zones (`excludedScrollRect`) while passing normal clicks and list scrolling through cleanly.
     */
    private inner class DraggableOverlayLayout(
        context: Context,
        private val layoutParams: WindowManager.LayoutParams
    ) : FrameLayout(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var downInsideScrollZone = false
        private var isDragging = false

        var excludedScrollRect: RectF? = null

        init {
            setViewTreeLifecycleOwner(this@ScreenEarnerOverlayService)
            setViewTreeViewModelStoreOwner(this@ScreenEarnerOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ScreenEarnerOverlayService)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = ev.rawX
                    initialTouchY = ev.rawY
                    isDragging = false
                    downInsideScrollZone = excludedScrollRect?.contains(ev.x, ev.y) == true
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (downInsideScrollZone) return false
                    val dx = kotlin.math.abs(ev.rawX - initialTouchX)
                    val dy = kotlin.math.abs(ev.rawY - initialTouchY)
                    if (dx > touchSlop || dy > touchSlop) {
                        isDragging = true
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    downInsideScrollZone = false
                }
            }
            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (isDragging || kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                        isDragging = true
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        try {
                            windowManager?.updateViewLayout(this, layoutParams)
                        } catch (_: Throwable) {
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    downInsideScrollZone = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    private fun overlayLayoutType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Earner Floating Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs the floating automation SE icon over external apps."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Earner Bubble Active")
            .setContentText("Non-intrusive SE icon floating over Chrome/BetPawa.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Creates the small circular floating SE bubble over real apps.
     */
    private fun setupFloatingBubbleOverlay() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bubbleDp = prefs.getFloat(PREF_BUBBLE_SIZE_DP, 56f).coerceIn(40f, 80f)
        val bubbleOpacity = prefs.getFloat(PREF_BUBBLE_OPACITY, 0.95f).coerceIn(0.3f, 1.0f)

        val containerDp = (bubbleDp + 20f).toInt()
        val bubbleSizePx = (containerDp * resources.displayMetrics.density).toInt()

        bubbleParams = WindowManager.LayoutParams(
            bubbleSizePx,
            bubbleSizePx,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (isKeepAwakeEngaged || AutomationController.latestConfig.value.keepScreenAwake) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 250
        }

        val container = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(this@ScreenEarnerOverlayService)
            setViewTreeViewModelStoreOwner(this@ScreenEarnerOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ScreenEarnerOverlayService)
        }

        val engine = AutomationController.engine
        val automationFlow = engine?.automationState ?: MutableStateFlow(AutomationState.IDLE)

        val composeView = ComposeView(this).apply {
            setContent {
                ScreenEarnerTheme {
                    val automationState by automationFlow.collectAsState()
                    FloatingMainBubbleComposable(
                        coreSizeDp = bubbleDp,
                        opacity = bubbleOpacity,
                        isRunningActive = automationState == AutomationState.RUNNING,
                        actionEvents = StrategyEngine.actionEvent,
                        onTriggerHaptic = { triggerHapticVibration() }
                    )
                }
            }
        }

        container.addView(composeView)
        bubbleView = container

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams?.x ?: 0
                    initialY = bubbleParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = kotlin.math.abs(event.rawX - initialTouchX)
                    val diffY = kotlin.math.abs(event.rawY - initialTouchY)
                    if (diffX > touchSlop || diffY > touchSlop) {
                        isMoved = true
                        bubbleParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                        bubbleParams?.y = initialY + (event.rawY - initialTouchY).toInt()
                        bubbleParams?.let { params ->
                            try {
                                windowManager?.updateViewLayout(bubbleView, params)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed updating bubble position", e)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoved) {
                        toggleCompactControlPanel()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(bubbleView, bubbleParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding bubble view", e)
        }
    }

    /**
     * Compact, non-intrusive floating Session Stats HUD overlay displaying real-time
     * win/loss count, total profit, crash history round counter, and Session Limit halts.
     */
    private fun setupSessionStatsHudOverlay() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hudOpacity = prefs.getFloat(PREF_BUBBLE_OPACITY, 0.95f).coerceIn(0.45f, 1.0f)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (isKeepAwakeEngaged || AutomationController.latestConfig.value.keepScreenAwake) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (24 * resources.displayMetrics.density).toInt()
            y = (36 * resources.displayMetrics.density).toInt()
        }
        sessionHudParams = params

        val container = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(this@ScreenEarnerOverlayService)
            setViewTreeViewModelStoreOwner(this@ScreenEarnerOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ScreenEarnerOverlayService)
        }

        val engine = AutomationController.engine
        val repo = AutomationController.repository
        val statsFlow = engine?.sessionStats ?: MutableStateFlow(SessionStats())
        val automationFlow = engine?.automationState ?: MutableStateFlow(AutomationState.IDLE)
        val recordsFlow = repo?.multiplierRecords ?: MutableStateFlow(emptyList<MultiplierRecord>())

        val composeView = ComposeView(this).apply {
            setContent {
                ScreenEarnerTheme {
                    val stats by statsFlow.collectAsState()
                    val autoState by automationFlow.collectAsState()
                    val records by recordsFlow.collectAsState(initial = emptyList())
                    val sessionCrashRounds = remember(records, stats.sessionStartMs, stats.observedCrashCount) {
                        val dbCount = if (stats.sessionStartMs > 0L) {
                            records.count { it.crashTimestamp >= stats.sessionStartMs }
                        } else {
                            records.size
                        }
                        maxOf(stats.observedCrashCount, dbCount)
                    }
                    FloatingSessionStatsHudComposable(
                        stats = stats,
                        observedRounds = sessionCrashRounds,
                        automationState = autoState,
                        opacity = hudOpacity
                    )
                }
            }
        }
        container.addView(composeView)
        sessionHudView = container

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragged = false

        container.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = kotlin.math.abs(event.rawX - initialTouchX)
                    val diffY = kotlin.math.abs(event.rawY - initialTouchY)
                    if (diffX > touchSlop || dyThresholdExceeded(diffY, touchSlop)) {
                        isDragged = true
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager?.updateViewLayout(v, params)
                        } catch (_: Throwable) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragged) {
                        toggleCompactControlPanel()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(container, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding session HUD view", e)
        }
    }

    private fun dyThresholdExceeded(dy: Float, touchSlop: Int): Boolean = dy > touchSlop

    private fun toggleCompactControlPanel() {
        if (isPanelVisible) {
            removeCompactControlPanel()
        } else {
            showCompactControlPanel()
        }
    }

    /**
     * Compact expanded SE control panel showing:
     * - Live multiplier + game phase (BETTING_OPEN / IN_FLIGHT / CRASHED)
     * - Strategy trigger telemetry (<2.00x streak, >10.00x wait, >13.00x count, session countdown)
     * - Scrollable timestamped Crash History
     * - ADD BET, ADD CASH OUT, functional START/STOP, SETTINGS, CLOSE, EXIT SERVICE
     */
    private fun showCompactControlPanel() {
        if (isPanelVisible) return

        val dm = resources.displayMetrics
        val panelWidthPx = (274f * dm.density).toInt()
        val maxStartX = (dm.widthPixels - panelWidthPx).coerceAtLeast(16)
        val maxStartY = (dm.heightPixels - (320f * dm.density).toInt()).coerceAtLeast(40)
        val bx = bubbleParams?.x ?: 40
        val by = bubbleParams?.y ?: 250

        val basePanelFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (isKeepAwakeEngaged || AutomationController.latestConfig.value.keepScreenAwake) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            basePanelFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bx + 65).coerceIn(0, maxStartX)
            y = (by - 10).coerceIn(0, maxStartY)
        }
        panelParams = params

        val container = DraggableOverlayLayout(this, params)

        val engine = AutomationController.engine
        val repo = AutomationController.repository
        val gameStateFlow = engine?.gameDetection?.gameState ?: MutableStateFlow(GameState())
        val automationStateFlow = engine?.automationState ?: MutableStateFlow(AutomationState.IDLE)
        val tickerFlow = engine?.agentStatusTicker ?: MutableStateFlow("Waiting for BetPawa / SportyBet...")
        val standbyFlow = engine?.isStandbyOutsideSchedule ?: MutableStateFlow(false)
        val recordsFlow = repo?.multiplierRecords ?: MutableStateFlow(emptyList<MultiplierRecord>())

        val composeView = ComposeView(this).apply {
            setContent {
                ScreenEarnerTheme {
                    val detectionState by ScreenEarnerAccessibilityService.detectionState.collectAsState()
                    val gameState by gameStateFlow.collectAsState()
                    val automationState by automationStateFlow.collectAsState()
                    val agentTicker by tickerFlow.collectAsState()
                    val isStandby by standbyFlow.collectAsState()
                    val activeConfig by AutomationController.latestConfig.collectAsState()
                    val activeCurrency by CurrencyManager.activeCurrency.collectAsState()
                    val crashRecords by recordsFlow.collectAsState(initial = emptyList())
                    val bannerMsg by statusBannerMessage.collectAsState()
                    val isScreenHeld by isScreenHeldState.collectAsState()
                    val isCpuActive by isCpuActiveState.collectAsState()
                    val awakeError by awakeErrorState.collectAsState()

                    val isRunningAuto = automationState == AutomationState.RUNNING

                    Card(
                        modifier = Modifier
                            .width(274.dp)
                            .shadow(14.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B2E)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DragIndicator,
                                        contentDescription = "Drag Utility Panel",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                if (isRunningAuto) ActionBetGreen else Color(0xFF64748B),
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SCREEN EARNER UTILITY",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                IconButton(
                                    onClick = { removeCompactControlPanel() },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Menu", tint = Color.LightGray)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Live Multiplier, Site & Real Button State Readout Card
                            Surface(
                                color = Color(0xFF2D2942),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val liveMult = if (gameState.currentMultiplier > 0.0) {
                                            gameState.currentMultiplier
                                        } else {
                                            detectionState.detectedMultiplier ?: 0.0
                                        }
                                        Text(
                                            text = if (gameState.isDetected && liveMult > 0.0) {
                                                "${String.format(Locale.US, "%.2f", liveMult)}x"
                                            } else {
                                                "--.--x"
                                            },
                                            color = when (gameState.status) {
                                                "IN_FLIGHT" -> ActionBetGreen
                                                "CRASHED" -> Color(0xFFEF4444)
                                                "BETTING_OPEN" -> ActionCashOutAmber
                                                else -> Color(0xFF38BDF8)
                                            },
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            val btnBadgeColor = when (gameState.siteButtonState) {
                                                SiteButtonState.IDLE_READY_TO_BET -> ActionBetGreen
                                                SiteButtonState.BET_QUEUED -> ActionStopRed
                                                SiteButtonState.CASH_OUT_AVAILABLE -> ActionCashOutAmber
                                                SiteButtonState.CRASHED -> Color(0xFFEF4444)
                                                SiteButtonState.UNKNOWN -> Color.Gray
                                            }
                                            Surface(
                                                color = Color(0xFF0F172A),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = gameState.siteButtonState.displayLabel,
                                                    color = btnBadgeColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Surface(
                                                color = Color(0xFF0F172A),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (gameState.isDetected) gameState.status else "WAITING",
                                                    color = if (gameState.isDetected) ActionStartEmerald else Color.Gray,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = if (gameState.isDetected) gameState.platformName else "Open BetPawa / SportyBet / Chrome",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    gameState.detectedUrl?.takeIf { it.isNotBlank() }?.let { siteUrl ->
                                        Text(
                                            text = siteUrl,
                                            color = Color(0xFF38BDF8),
                                            fontSize = 8.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (gameState.recentRibbonHistory.isNotEmpty()) {
                                        Text(
                                            text = "Site Ribbon: ${gameState.recentRibbonHistory.take(5).joinToString("  ") { String.format(Locale.US, "%.2fx", it) }}",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 8.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    gameState.detectedBalance?.let { bal ->
                                        Text(
                                            text = "Balance: ${CurrencyManager.formatAmount(bal, activeCurrency)}",
                                            color = ActionStartEmerald,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    // Strategy trigger telemetry counters
                                    Text(
                                        text = "<2.00x: ${gameState.consecutiveLowCount} • >10x wait: ${gameState.postHighWaitCount} • >13x/h: ${gameState.highMultiplierLossesInHour}",
                                        color = Color(0xFFA5B4FC),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    if (gameState.waitingRuleName != null && gameState.activeWaitRemainingMs > 0L) {
                                        val secs = (gameState.activeWaitRemainingMs / 1000L).coerceAtLeast(0L)
                                        Text(
                                            text = "Wait: ${gameState.waitingRuleName} (${secs / 60}m ${secs % 60}s)",
                                            color = ActionCashOutAmber,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (gameState.activeTrigger != null) {
                                        val guardSecs = (gameState.sessionTimeoutRemainingMs / 1000L).coerceAtLeast(0L)
                                        Text(
                                            text = "${gameState.activeTrigger} (${guardSecs / 60}:${String.format(Locale.US, "%02d", guardSecs % 60)} guard)",
                                            color = ActionStartEmerald,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Live Step-by-Step Diagnostic Agent Ticker + Never-Sleep / Schedule pills
                            Spacer(modifier = Modifier.height(5.dp))
                            Surface(
                                color = if (isStandby) Color(0xFF451A03) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = agentTicker,
                                        color = if (isStandby) ActionCashOutAmber else Color(0xFF7DD3FC),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val awakeStatusLabel = when {
                                        awakeError != null -> "AWAKE ERROR"
                                        isScreenHeld -> "AWAKE: ON — SCREEN HELD"
                                        activeConfig.keepScreenAwake -> "AWAKE: ON — SCREEN HELD"
                                        else -> "AWAKE: OFF"
                                    }
                                    val awakeColor = when {
                                        awakeError != null -> Color(0xFFEF4444)
                                        isScreenHeld || activeConfig.keepScreenAwake -> ActionStartEmerald
                                        else -> null
                                    }
                                    val cpuStatusLabel = if (isCpuActive) "CPU: ACTIVE" else "CPU: RELEASED"
                                    val cpuColor = if (isCpuActive) PrimaryIndigo else Color(0xFF334155)

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            MiniStepperChip(
                                                label = awakeStatusLabel,
                                                selected = activeConfig.keepScreenAwake,
                                                overrideColor = awakeColor
                                            ) {
                                                val updated = activeConfig.copy(keepScreenAwake = !activeConfig.keepScreenAwake)
                                                serviceScope.launch {
                                                    runCatching { repo?.saveStrategyConfig(updated) }
                                                }
                                            }
                                            MiniStepperChip(
                                                label = cpuStatusLabel,
                                                selected = isCpuActive,
                                                overrideColor = cpuColor
                                            ) {
                                                // Informational indicator
                                            }
                                        }

                                        val schedLabel = if (activeConfig.scheduleEnabled) {
                                            OperatingScheduleEvaluator.formatWindow(
                                                activeConfig.scheduleStartHour,
                                                activeConfig.scheduleStartMinute,
                                                activeConfig.scheduleEndHour,
                                                activeConfig.scheduleEndMinute
                                            )
                                        } else {
                                            "Sched: 24/7"
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            MiniStepperChip(
                                                label = schedLabel,
                                                selected = activeConfig.scheduleEnabled
                                            ) {
                                                val updated = activeConfig.copy(scheduleEnabled = !activeConfig.scheduleEnabled)
                                                serviceScope.launch {
                                                    runCatching { repo?.saveStrategyConfig(updated) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Scrollable Timestamped Crash History
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = Color(0xFF141122),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text(
                                        text = "CRASH HISTORY (${crashRecords.size})",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (crashRecords.isEmpty()) {
                                        Text(
                                            text = "Waiting for crashed rounds...",
                                            color = Color(0xFF64748B),
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 136.dp)
                                                .onGloballyPositioned { coords ->
                                                    val b = coords.boundsInWindow()
                                                    container.excludedScrollRect = RectF(b.left, b.top, b.right, b.bottom)
                                                },
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            items(crashRecords.take(30), key = { "${it.id}_${it.crashTimestamp}" }) { rec ->
                                                val multColor = when {
                                                    rec.multiplier >= 10.0 -> ActionCashOutAmber
                                                    rec.multiplier >= 2.0 -> ActionStartEmerald
                                                    else -> Color(0xFFF87171)
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "${String.format(Locale.US, "%.2f", rec.multiplier)}x",
                                                        color = multColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = MultiplierHistoryAnalyzer.formatTimestamp(rec.crashTimestamp),
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val activeLimits by AutomationController.latestLimits.collectAsState()
                            val sessionStats by (engine?.sessionStats ?: MutableStateFlow(SessionStats())).collectAsState()
                            val activeBanner = bannerMsg ?: sessionStats.limitHaltReason

                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val pTxt = if (activeLimits.stopOnProfitEnabled) {
                                        "+${CurrencyManager.formatCompact(activeLimits.profitTarget, activeCurrency)}"
                                    } else "OFF"
                                    val lTxt = if (activeLimits.stopOnLossEnabled) {
                                        "-${CurrencyManager.formatCompact(activeLimits.maxSessionLoss, activeCurrency)}"
                                    } else "OFF"
                                    Text(
                                        text = "Session Limits: $pTxt / $lTxt",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Reset",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                engine?.resetSessionStats()
                                                statusBannerMessage.value = null
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (!activeBanner.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0xFF7F1D1D),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = activeBanner,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action Menu Buttons
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OverlayMenuButton(
                                        label = "ADD BET",
                                        icon = Icons.Default.AddCircle,
                                        color = ActionBetGreen,
                                        onClick = { spawnPersistentHand(ShortcutType.BET, ActionBetGreen) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OverlayMenuButton(
                                        label = "ADD CASH OUT",
                                        icon = Icons.Default.Wallet,
                                        color = ActionCashOutAmber,
                                        onClick = { spawnPersistentHand(ShortcutType.CASH_OUT, ActionCashOutAmber) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OverlayMenuButton(
                                        label = if (isRunningAuto) "STOP" else "START",
                                        icon = if (isRunningAuto) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        color = if (isRunningAuto) ActionStopRed else ActionStartEmerald,
                                        onClick = { handleOverlayStartStop() },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OverlayMenuButton(
                                        label = "SETTINGS",
                                        icon = Icons.Default.Settings,
                                        color = PrimaryIndigo,
                                        onClick = {
                                            removeCompactControlPanel()
                                            openMainActivity(MainActivity.TAB_SETTINGS)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OverlayMenuButton(
                                        label = "CLOSE MENU",
                                        icon = Icons.Default.Close,
                                        color = Color(0xFF475569),
                                        onClick = { removeCompactControlPanel() },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OverlayMenuButton(
                                        label = "EXIT SERVICE",
                                        icon = Icons.Default.ExitToApp,
                                        color = Color(0xFF991B1B),
                                        onClick = {
                                            AutomationController.engine?.quitAutomation()
                                            stopSelf()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        container.addView(composeView)
        panelView = container

        try {
            windowManager?.addView(panelView, params)
            isPanelVisible = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding compact control panel", e)
        }
    }

    private fun handleOverlayStartStop() {
        val engine = AutomationController.engine ?: return
        if (engine.automationState.value == AutomationState.RUNNING) {
            engine.quitAutomation()
            statusBannerMessage.value = null
            triggerHapticVibration()
            return
        }

        val config = AutomationController.latestConfig.value
        val limits = AutomationController.latestLimits.value
        val result = engine.startAutomationWithValidation(
            config = config,
            limits = limits,
            isAccessibilityConnected = ScreenEarnerAccessibilityService.isServiceConnected()
        )
        triggerHapticVibration()
        if (!result.started) {
            statusBannerMessage.value = result.message
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        } else {
            statusBannerMessage.value = null
            Toast.makeText(this, "Screen Earner Automation ACTIVE", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeCompactControlPanel() {
        if (!isPanelVisible || panelView == null) return
        try {
            windowManager?.removeView(panelView)
        } catch (e: Exception) {
            Log.e(TAG, "Failed removing compact control panel", e)
        }
        panelView = null
        statusBannerMessage.value = null
        isPanelVisible = false
    }

    /**
     * Spawns a draggable floating BET or CASH OUT hand backed by a Room FloatingShortcut record.
     * Single-tapping the hand opens its compact floating Settings Card right next to it.
     */
    private fun spawnPersistentHand(type: ShortcutType, color: Color) {
        val repo = AutomationController.repository
        val isBet = type == ShortcutType.BET
        val existingCount = independentHands.count { it.type == type } + 1
        val label = if (isBet) "BET $existingCount" else "CASH OUT $existingCount"
        val defaultBg = if (isBet) "#16A34A" else "#F59E0B"
        val detectedPlatform = AutomationController.engine?.gameDetection?.gameState?.value?.platformName.orEmpty()
        val siteKey = GameDetection.resolveSiteKey(detectedPlatform).ifBlank { "BetPawa" }

        serviceScope.launch {
            try {
                val existingShortcuts = AutomationController.latestShortcuts.value
                val matchingUnattached = existingShortcuts.firstOrNull { shortcut ->
                    shortcut.type == type &&
                        shortcut.targetPlatform.equals(siteKey, ignoreCase = true) &&
                        independentHands.none { it.shortcutId == shortcut.id }
                } ?: existingShortcuts.firstOrNull { shortcut ->
                    shortcut.type == type && independentHands.none { it.shortcutId == shortcut.id }
                }

                val defaultVisualX = 150f + (independentHands.size * 65f)
                val defaultVisualY = 450f
                val shortcutId = matchingUnattached?.id ?: repo?.insertShortcut(
                    FloatingShortcut(
                        label = label,
                        type = type,
                        panelId = existingCount,
                        visualX = defaultVisualX,
                        visualY = defaultVisualY,
                        backgroundColorHex = defaultBg,
                        targetPlatform = siteKey,
                        targetX = 0f,
                        targetY = 0f,
                        isCalibrated = false
                    )
                ) ?: System.currentTimeMillis()

                attachHandWindow(
                    shortcutId = shortcutId,
                    type = type,
                    color = color,
                    initialVisualX = matchingUnattached?.visualX ?: defaultVisualX,
                    initialVisualY = matchingUnattached?.visualY ?: defaultVisualY
                )
            } catch (e: Exception) {
                Log.e(TAG, "Save failed spawning persistent hand", e)
                Toast.makeText(
                    this@ScreenEarnerOverlayService,
                    "Failed to save settings, please check input",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun attachHandWindow(
        shortcutId: Long,
        type: ShortcutType,
        color: Color,
        initialVisualX: Float? = null,
        initialVisualY: Float? = null
    ) {
        val btnSizePx = (72 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            btnSizePx,
            btnSizePx,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (isKeepAwakeEngaged || AutomationController.latestConfig.value.keepScreenAwake) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (initialVisualX?.toInt() ?: (150 + (independentHands.size * 65))).coerceAtLeast(0)
            y = (initialVisualY?.toInt() ?: 450).coerceAtLeast(0)
        }

        val container = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(this@ScreenEarnerOverlayService)
            setViewTreeViewModelStoreOwner(this@ScreenEarnerOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ScreenEarnerOverlayService)
        }

        val displayLabel = if (type == ShortcutType.BET) "BET" else "CASH OUT"

        val composeView = ComposeView(this).apply {
            setContent {
                ScreenEarnerTheme {
                    FloatingTaskRobotButton(
                        label = displayLabel,
                        baseColor = color,
                        actionEvents = StrategyEngine.actionEvent,
                        onTriggerHaptic = { triggerHapticVibration() }
                    )
                }
            }
        }

        container.addView(composeView)

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragged = false

        val entry = FloatingHandEntry(shortcutId, type, container, params)

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = kotlin.math.abs(event.rawX - initialTouchX)
                    val diffY = kotlin.math.abs(event.rawY - initialTouchY)
                    if (diffX > touchSlop || diffY > touchSlop) {
                        isDragged = true
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager?.updateViewLayout(v, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed updating hand position", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragged) {
                        // Single-tap opens the compact Hand Settings Card immediately!
                        showHandSettingsCard(entry)
                    } else {
                        val newX = params.x.toFloat()
                        val newY = params.y.toFloat()
                        if (newX.isFinite() && newY.isFinite()) {
                            serviceScope.launch {
                                try {
                                    val existing = AutomationController.latestShortcuts.value
                                        .firstOrNull { it.id == entry.shortcutId }
                                    if (existing != null) {
                                        AutomationController.repository?.updateShortcut(
                                            existing.copy(visualX = newX, visualY = newY)
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Save failed persisting hand position", e)
                                }
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }

        independentHands.add(entry)
        try {
            windowManager?.addView(container, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed attaching hand window", e)
        }
    }

    /**
     * Compact floating settings card opened on SINGLE-TAP of any BET or CASH OUT hand.
     * Freely draggable anywhere on screen. Supports site profile (BetPawa/SportyBet),
     * Calibrate Target ("Set Screen Target"), stake/multiplier behavior,
     * Test Tap ("Test Hand Now"), Save/Close, and Remove Button.
     */
    private fun showHandSettingsCard(entry: FloatingHandEntry) {
        removeHandSettingsCard()

        val dm = resources.displayMetrics
        val cardWidthPx = (264f * dm.density).toInt()
        val maxStartX = (dm.widthPixels - cardWidthPx).coerceAtLeast(16)
        val maxStartY = (dm.heightPixels - (320f * dm.density).toInt()).coerceAtLeast(40)

        val cardParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (entry.params.x + 60).coerceIn(0, maxStartX)
            y = (entry.params.y - 40).coerceIn(0, maxStartY)
        }
        handSettingsParams = cardParams

        val container = DraggableOverlayLayout(this, cardParams)

        fun setCardFocusable(focusable: Boolean) {
            val updated = if (focusable) {
                cardParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                cardParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            if (cardParams.flags != updated) {
                cardParams.flags = updated
                try {
                    windowManager?.updateViewLayout(container, cardParams)
                } catch (_: Throwable) {
                }
            }
        }

        val isBet = entry.type == ShortcutType.BET

        val composeView = ComposeView(this).apply {
            setContent {
                ScreenEarnerTheme {
                    val shortcutsList by AutomationController.latestShortcuts.collectAsState()
                    val activeCurrency by CurrencyManager.activeCurrency.collectAsState()
                    val activeLimits by AutomationController.latestLimits.collectAsState()
                    val shortcut = shortcutsList.firstOrNull { it.id == entry.shortcutId }
                        ?: FloatingShortcut(id = entry.shortcutId, label = if (isBet) "BET" else "CASH OUT", type = entry.type)

                    var stakeValue by remember(shortcut.stakeAmount) { mutableStateOf(shortcut.stakeAmount) }
                    var stakeText by remember(shortcut.stakeAmount) {
                        mutableStateOf(
                            if (shortcut.stakeAmount % 1.0 == 0.0) shortcut.stakeAmount.toLong().toString()
                            else String.format(Locale.US, "%.2f", shortcut.stakeAmount)
                        )
                    }
                    var multValue by remember(shortcut.targetMultiplier) { mutableStateOf(shortcut.targetMultiplier) }
                    var panelIdValue by remember(shortcut.panelId) { mutableStateOf(shortcut.panelId) }
                    var platformValue by remember(shortcut.targetPlatform) { mutableStateOf(shortcut.targetPlatform) }
                    var testFeedback by remember { mutableStateOf<String?>(null) }

                    Card(
                        modifier = Modifier
                            .width(264.dp)
                            .shadow(14.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B2E)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DragIndicator,
                                        contentDescription = "Drag Hand Settings",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isBet) "BET HAND SETTINGS" else "CASH OUT HAND SETTINGS",
                                        color = if (isBet) ActionBetGreen else ActionCashOutAmber,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = { removeHandSettingsCard() },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                                }
                            }

                            // Pre-assigned role + site assignment
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBet) "Role: ACTION_BET" else "Role: CASH_OUT",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MiniStepperChip(
                                            label = "BetPawa",
                                            selected = platformValue.equals("BetPawa", ignoreCase = true)
                                        ) { platformValue = "BetPawa" }
                                        MiniStepperChip(
                                            label = "SportyBet",
                                            selected = platformValue.equals("SportyBet", ignoreCase = true)
                                        ) { platformValue = "SportyBet" }
                                    }
                                }
                            }

                            // 1. Target coordinates & Set Screen Target
                            Surface(
                                color = Color(0xFF2D2942),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = if (shortcut.isCalibrated && shortcut.targetX > 0f) {
                                            "Screen Target: (${shortcut.targetX.toInt()}px, ${shortcut.targetY.toInt()}px)"
                                        } else {
                                            "Screen Target: NOT SET"
                                        },
                                        color = if (shortcut.isCalibrated) ActionStartEmerald else Color(0xFFFBBF24),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            removeHandSettingsCard()
                                            startTapTargetCalibration(entry)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Icon(Icons.Default.MyLocation, contentDescription = "Calibrate", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("SET SCREEN TARGET", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // 2. Execution Behavior (Stake/Panel for BET or Target Mult for CASH OUT)
                            Surface(
                                color = Color(0xFF2D2942),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isBet) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Stake (${activeCurrency.symbol})",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            BasicTextField(
                                                value = stakeText,
                                                onValueChange = { raw ->
                                                    val filtered = raw.filter { it.isDigit() || it == '.' }
                                                    stakeText = filtered
                                                    filtered.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }?.let {
                                                        stakeValue = it
                                                    }
                                                },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                cursorBrush = SolidColor(ActionStartEmerald),
                                                modifier = Modifier
                                                    .width(96.dp)
                                                    .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                                    .border(1.dp, PrimaryIndigo, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                                    .onFocusChanged { focusState ->
                                                        setCardFocusable(focusState.isFocused)
                                                    }
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = CurrencyManager.formatAmount(stakeValue, activeCurrency),
                                                color = ActionStartEmerald,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                MiniStepperChip("-50") {
                                                    stakeValue = (stakeValue - 50.0).coerceAtLeast(1.0)
                                                    stakeText = stakeValue.toLong().toString()
                                                }
                                                MiniStepperChip("+50") {
                                                    stakeValue += 50.0
                                                    stakeText = stakeValue.toLong().toString()
                                                }
                                                MiniStepperChip("2x") {
                                                    stakeValue *= 2.0
                                                    stakeText = stakeValue.toLong().toString()
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Max Cap: ${CurrencyManager.formatCompact(activeLimits.maxSingleStake, activeCurrency)}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 9.sp
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                MiniStepperChip("P1", selected = panelIdValue == 1) { panelIdValue = 1 }
                                                MiniStepperChip("P2", selected = panelIdValue == 2) { panelIdValue = 2 }
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Auto CashOut: ${String.format(Locale.US, "%.2f", multValue)}x", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                MiniStepperChip("-0.25") { multValue = (multValue - 0.25).coerceAtLeast(1.10) }
                                                MiniStepperChip("+0.25") { multValue += 0.25 }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Test Hand Now via real AccessibilityService.dispatchGesture
                            OutlinedButton(
                                onClick = {
                                    val service = ScreenEarnerAccessibilityService.instance
                                        ?: AccessibilityController.controllerInstance
                                    if (service == null) {
                                        testFeedback = "Enable Accessibility Service first!"
                                    } else if (!shortcut.isCalibrated ||
                                        !shortcut.targetX.isFinite() ||
                                        !shortcut.targetY.isFinite() ||
                                        shortcut.targetX <= 0f ||
                                        shortcut.targetY <= 0f
                                    ) {
                                        testFeedback = "Tap SET SCREEN TARGET first!"
                                    } else {
                                        service.dispatchTouchGesture(shortcut.targetX, shortcut.targetY) { ok ->
                                            testFeedback = if (ok) {
                                                "Test tap dispatched at (${shortcut.targetX.toInt()}, ${shortcut.targetY.toInt()})"
                                            } else {
                                                "Test gesture cancelled"
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Icon(Icons.Default.TouchApp, contentDescription = "Test Tap", tint = ActionStartEmerald, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TEST HAND NOW", color = ActionStartEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            testFeedback?.let { msg ->
                                Text(msg, color = Color(0xFF38BDF8), fontSize = 10.sp)
                            }

                            HorizontalDivider(color = Color(0xFF334155))

                            // 4. Save & Close / Remove Button (closes ONLY the hand settings panel)
                            var isSavingCard by remember { mutableStateOf(false) }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    enabled = !isSavingCard,
                                    onClick = {
                                        if (isSavingCard) return@Button
                                        isSavingCard = true
                                        serviceScope.launch {
                                            try {
                                                val parsedStake = stakeText.trim().toDoubleOrNull()
                                                val resolvedStake = parsedStake ?: stakeValue
                                                val stakeValid = resolvedStake.isFinite() && resolvedStake > 0.0
                                                val multValid = multValue.isFinite() && multValue >= 1.01
                                                val coordsValid = shortcut.targetX.isFinite() &&
                                                    shortcut.targetY.isFinite() &&
                                                    shortcut.targetX >= 0f &&
                                                    shortcut.targetY >= 0f
                                                val safePlatform = platformValue.trim().ifBlank { "BetPawa" }

                                                if (!stakeValid || !multValid || !coordsValid) {
                                                    Toast.makeText(
                                                        this@ScreenEarnerOverlayService,
                                                        "Failed to save settings, please check input",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    isSavingCard = false
                                                    return@launch
                                                }

                                                setCardFocusable(false)
                                                AutomationController.ensureInitialized(applicationContext)
                                                AutomationController.repository?.updateShortcut(
                                                    shortcut.copy(
                                                        stakeAmount = resolvedStake,
                                                        targetMultiplier = multValue,
                                                        panelId = panelIdValue.coerceIn(1, 2),
                                                        targetPlatform = safePlatform
                                                    )
                                                )
                                                removeHandSettingsCard()
                                                Toast.makeText(
                                                    this@ScreenEarnerOverlayService,
                                                    "Hand settings saved (${CurrencyManager.formatAmount(resolvedStake, activeCurrency)})",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Save failed in hand settings card", e)
                                                isSavingCard = false
                                                Toast.makeText(
                                                    this@ScreenEarnerOverlayService,
                                                    "Failed to save settings, please check input",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ActionStartEmerald),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Text("SAVE & CLOSE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        removeHandSettingsCard()
                                        removeHandWindow(entry)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ActionStopRed),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("REMOVE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        container.addView(composeView)
        handSettingsView = container
        try {
            windowManager?.addView(container, cardParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed showing hand settings card", e)
        }
    }

    private fun removeHandSettingsCard() {
        handSettingsView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed removing hand settings card", e)
            }
        }
        handSettingsView = null
        handSettingsParams = null
    }

    private fun removeHandWindow(entry: FloatingHandEntry) {
        try {
            windowManager?.removeView(entry.view)
        } catch (e: Exception) {
            Log.e(TAG, "Failed removing hand window", e)
        }
        independentHands.remove(entry)
        serviceScope.launch {
            try {
                AutomationController.repository?.deleteShortcut(entry.shortcutId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed deleting hand shortcut", e)
            }
        }
    }

    /**
     * Full-screen transparent touch-catcher so user can tap directly on the real Bet or Cash Out
     * button in Chrome / BetPawa / SportyBet underneath, saving exact screen (targetX, targetY).
     */
    private fun startTapTargetCalibration(entry: FloatingHandEntry) {
        removeCalibrationCatcher()

        val catcherParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        val container = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(this@ScreenEarnerOverlayService)
            setViewTreeViewModelStoreOwner(this@ScreenEarnerOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ScreenEarnerOverlayService)
        }

        val roleTitle = if (entry.type == ShortcutType.BET) "BET" else "CASH OUT"
        val composeView = ComposeView(this).apply {
            setContent {
                ScreenEarnerTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x28000000))
                    ) {
                        Surface(
                            color = Color(0xEE1E1B2E),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 48.dp)
                                .border(1.5.dp, ActionStartEmerald, RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Calibrate", tint = ActionStartEmerald)
                                Text(
                                    text = "Tap directly on the real $roleTitle button underneath",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        container.addView(composeView)

        container.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val tx = event.rawX
                val ty = event.rawY
                removeCalibrationCatcher()
                if (!tx.isFinite() || !ty.isFinite() || tx < 0f || ty < 0f) {
                    Toast.makeText(
                        this@ScreenEarnerOverlayService,
                        "Failed to save settings, please check input",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnTouchListener true
                }
                serviceScope.launch {
                    try {
                        val existing = AutomationController.latestShortcuts.value
                            .firstOrNull { it.id == entry.shortcutId }
                        if (existing != null) {
                            AutomationController.repository?.updateShortcut(
                                existing.copy(targetX = tx, targetY = ty, isCalibrated = true)
                            )
                        }
                        triggerHapticVibration()
                        Toast.makeText(
                            this@ScreenEarnerOverlayService,
                            "$roleTitle target set at (${tx.toInt()}, ${ty.toInt()})",
                            Toast.LENGTH_SHORT
                        ).show()
                        showHandSettingsCard(entry)
                    } catch (e: Exception) {
                        Log.e(TAG, "Save failed during target calibration", e)
                        Toast.makeText(
                            this@ScreenEarnerOverlayService,
                            "Failed to save settings, please check input",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                true
            } else {
                true
            }
        }

        calibrationCatcherView = container
        try {
            windowManager?.addView(container, catcherParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed attaching calibration catcher", e)
        }
    }

    private fun removeCalibrationCatcher() {
        calibrationCatcherView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed removing calibration catcher", e)
            }
        }
        calibrationCatcherView = null
    }

    private fun openMainActivity(tabIndex: Int = MainActivity.TAB_STRATEGY) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, tabIndex)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed opening MainActivity", e)
            Toast.makeText(this, "Unable to open settings screen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerHapticVibration() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(PREF_HAPTICS_ENABLED, true)) return

            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Haptic vibration failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLockSafely()
        AccessibilityController.gestureOverlayPassThroughListener = null
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        isRunning = false
        removeCompactControlPanel()
        removeHandSettingsCard()
        removeCalibrationCatcher()
        if (sessionHudView != null) {
            try {
                windowManager?.removeView(sessionHudView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed removing session HUD", e)
            }
            sessionHudView = null
        }
        if (bubbleView != null) {
            try {
                windowManager?.removeView(bubbleView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed removing bubble view", e)
            }
        }
        independentHands.forEach {
            try {
                windowManager?.removeView(it.view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed removing hand view", e)
            }
        }
        independentHands.clear()
    }
}

/**
 * Compact, translucent, non-intrusive floating Session Stats pill showing live Win/Loss
 * tally, signed Total Profit, crash rounds count, and any active Session Limit halt state.
 */
@Composable
fun FloatingSessionStatsHudComposable(
    stats: SessionStats,
    observedRounds: Int,
    automationState: AutomationState,
    opacity: Float = 0.92f
) {
    val activeCurrency by CurrencyManager.activeCurrency.collectAsState()
    val isHaltedByLimit = !stats.limitHaltReason.isNullOrBlank()
    val profitColor = when {
        stats.totalProfit > 0.004 -> ActionBetGreen
        stats.totalProfit < -0.004 -> Color(0xFFF87171)
        else -> Color.White
    }
    val borderColor = when {
        isHaltedByLimit -> ActionStopRed
        automationState == AutomationState.RUNNING && stats.totalProfit >= 0.0 -> ActionBetGreen.copy(alpha = 0.85f)
        automationState == AutomationState.RUNNING -> ActionCashOutAmber.copy(alpha = 0.85f)
        else -> Color(0xFF475569)
    }
    val statusDotColor = when {
        isHaltedByLimit -> ActionStopRed
        automationState == AutomationState.RUNNING -> ActionBetGreen
        automationState == AutomationState.PAUSED -> ActionCashOutAmber
        else -> Color(0xFF94A3B8)
    }

    Surface(
        color = Color(0xD90F172A),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .alpha(opacity)
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .border(1.2.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(statusDotColor, CircleShape)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${stats.winCount}W",
                        color = ActionBetGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "/",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${stats.lossCount}L",
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(12.dp)
                        .background(Color(0xFF334155))
                )
                Text(
                    text = CurrencyManager.formatSignedAmount(stats.totalProfit, activeCurrency),
                    color = profitColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "R:$observedRounds",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isHaltedByLimit) {
                Text(
                    text = "HALTED: ${stats.limitHaltReason}",
                    color = Color(0xFFFCA5A5),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MiniStepperChip(
    label: String,
    selected: Boolean = true,
    overrideColor: Color? = null,
    onClick: () -> Unit
) {
    val bgColor = overrideColor ?: if (selected) PrimaryIndigo else Color(0xFF334155)
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun OverlayMenuButton(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(13.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Main floating "SE" circular bubble with active running indicator and subtle ripple wave
 * when StrategyEngine triggers an automatic bet or cashout.
 */
@Composable
fun FloatingMainBubbleComposable(
    coreSizeDp: Float = 56f,
    opacity: Float = 0.95f,
    isRunningActive: Boolean = false,
    actionEvents: StateFlow<AutomationActionEvent?>,
    onTriggerHaptic: () -> Unit
) {
    val actionEvent by actionEvents.collectAsState()

    val rippleProgress = remember { Animatable(0f) }
    val bubbleScale = remember { Animatable(1f) }
    var activeRippleColor by remember { mutableStateOf(Color.Transparent) }
    var badgeText by remember { mutableStateOf<String?>(null) }
    var lastHandledEventId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(actionEvent?.eventId) {
        val event = actionEvent
        if (event != null && event.eventId != lastHandledEventId) {
            lastHandledEventId = event.eventId
            onTriggerHaptic()

            val isBet = event.actionType == ActionType.BET
            activeRippleColor = if (isBet) Color(0xFF22C55E) else Color(0xFFF59E0B)
            badgeText = if (isBet) "BET" else "CASH"

            launch {
                rippleProgress.snapTo(0f)
                rippleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
                )
            }

            launch {
                bubbleScale.animateTo(1.14f, animationSpec = tween(130, easing = FastOutSlowInEasing))
                bubbleScale.animateTo(0.96f, animationSpec = tween(120, easing = FastOutSlowInEasing))
                bubbleScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }

            delay(1500)
            badgeText = null
        }
    }

    val containerDp = (coreSizeDp + 20f).dp

    Box(
        modifier = Modifier
            .size(containerDp)
            .alpha(opacity),
        contentAlignment = Alignment.Center
    ) {
        // Expanding Ripple Ring on SE bubble
        if (rippleProgress.value > 0f && rippleProgress.value < 1f) {
            val scale = 1.0f + (rippleProgress.value * 0.35f)
            val alpha = ((1f - rippleProgress.value) * 0.85f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size(coreSizeDp.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .border(
                        width = 2.5.dp,
                        color = activeRippleColor.copy(alpha = alpha),
                        shape = CircleShape
                    )
            )
        }

        // Core SE Bubble with green active border when running
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = bubbleScale.value
                    scaleY = bubbleScale.value
                }
                .size(coreSizeDp.dp)
                .shadow(10.dp, CircleShape)
                .background(PrimaryIndigo, CircleShape)
                .border(
                    width = if (isRunningActive) 3.dp else 2.dp,
                    color = if (isRunningActive) ActionBetGreen else Color.White.copy(alpha = 0.9f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SE",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )
        }

        // Active Running Green Dot
        if (isRunningActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(12.dp)
                    .background(ActionBetGreen, CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }

        // Mini status badge during auto action
        badgeText?.let { badge ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .background(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(1.dp, activeRippleColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badge,
                    color = activeRippleColor,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Independent Floating Task Robot button (Bet / Cashout) with subtle ripple wave,
 * luminous glow halo, bounce, and instant action badge when StrategyEngine triggers an action.
 */
@Composable
fun FloatingTaskRobotButton(
    label: String,
    baseColor: Color,
    actionEvents: StateFlow<AutomationActionEvent?>,
    onTriggerHaptic: () -> Unit
) {
    val actionEvent by actionEvents.collectAsState()
    val isBet = label == "BET"
    val targetAction = if (isBet) ActionType.BET else ActionType.CASH_OUT

    val rippleProgress1 = remember { Animatable(0f) }
    val rippleProgress2 = remember { Animatable(0f) }
    val buttonScale = remember { Animatable(1f) }
    val glowAlpha = remember { Animatable(0f) }

    var badgeText by remember { mutableStateOf<String?>(null) }
    var lastHandledEventId by remember { mutableStateOf<Long?>(null) }

    val accentColor = if (isBet) Color(0xFF22C55E) else Color(0xFFF59E0B)
    val glowColor = if (isBet) Color(0xFF86EFAC) else Color(0xFFFDE047)

    LaunchedEffect(actionEvent?.eventId) {
        val event = actionEvent
        if (event != null && event.actionType == targetAction && event.eventId != lastHandledEventId) {
            lastHandledEventId = event.eventId
            onTriggerHaptic()

            badgeText = if (isBet) {
                if (event.amountOrMultiplier > 0) "+BET ${CurrencyManager.formatCompact(event.amountOrMultiplier)}" else "+AUTO BET"
            } else {
                if (event.amountOrMultiplier > 0) "+CASH ${String.format(Locale.US, "%.2f", event.amountOrMultiplier)}x" else "+AUTO CASH"
            }

            launch {
                rippleProgress1.snapTo(0f)
                rippleProgress1.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
                )
            }

            launch {
                delay(150)
                rippleProgress2.snapTo(0f)
                rippleProgress2.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
                )
            }

            launch {
                buttonScale.animateTo(1.16f, animationSpec = tween(140, easing = FastOutSlowInEasing))
                buttonScale.animateTo(0.94f, animationSpec = tween(120, easing = FastOutSlowInEasing))
                buttonScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }

            launch {
                glowAlpha.snapTo(1f)
                glowAlpha.animateTo(0f, animationSpec = tween(700, easing = FastOutSlowInEasing))
            }

            delay(1500)
            badgeText = null
        }
    }

    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        if (rippleProgress1.value > 0f && rippleProgress1.value < 1f) {
            val scale = 1.0f + (rippleProgress1.value * 0.42f)
            val alpha = ((1f - rippleProgress1.value) * 0.85f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .border(
                        width = 2.5.dp,
                        color = accentColor.copy(alpha = alpha),
                        shape = CircleShape
                    )
            )
        }

        if (rippleProgress2.value > 0f && rippleProgress2.value < 1f) {
            val scale = 1.0f + (rippleProgress2.value * 0.35f)
            val alpha = ((1f - rippleProgress2.value) * 0.65f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .border(
                        width = 1.8.dp,
                        color = glowColor.copy(alpha = alpha),
                        shape = CircleShape
                    )
            )
        }

        if (glowAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = glowColor.copy(alpha = glowAlpha.value * 0.4f),
                        shape = CircleShape
                    )
            )
        }

        val isGlowing = glowAlpha.value > 0.15f
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = buttonScale.value
                    scaleY = buttonScale.value
                }
                .size(50.dp)
                .shadow(elevation = if (isGlowing) 12.dp else 8.dp, shape = CircleShape)
                .background(baseColor, CircleShape)
                .border(
                    width = if (isGlowing) 2.5.dp else 2.dp,
                    color = if (isGlowing) glowColor else Color.White,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isBet) "BET" else "CASH",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        badgeText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 1.dp)
                    .background(
                        color = Color(0xFF0F172A).copy(alpha = 0.95f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, accentColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = text,
                    color = accentColor,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
