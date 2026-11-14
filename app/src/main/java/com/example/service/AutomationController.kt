package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.database.ScreenEarnerDatabase
import com.example.data.database.ScreenEarnerRepository
import com.example.data.engine.AutomationEngine
import com.example.data.engine.CurrencyManager
import com.example.data.engine.GameDetection
import com.example.data.engine.PatternDetectionEngine
import com.example.data.engine.RibbonHistoryCollector
import com.example.data.engine.RiskManager
import com.example.data.engine.StrategyEngine
import com.example.data.model.CollectionSource
import com.example.data.model.CustomPatternStrategy
import com.example.data.model.FloatingShortcut
import com.example.data.model.HistoryCollectionStatus
import com.example.data.model.NotificationSettings
import com.example.data.model.RiskLimits
import com.example.data.model.StrategyConfig
import com.example.data.model.StrategyNotificationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AutomationController - Singleton managing the automation engine, Room repository sync,
 * 30m ribbon history collection, pattern detection evaluation, and background notification dispatch
 * so overlays and strategy alerts continue operating even when MainActivity is minimized or closed.
 */
object AutomationController {
    private const val TAG = "AutomationController"

    var repository: ScreenEarnerRepository? = null
    var engine: AutomationEngine? = null
    var appContextRef: Context? = null
        private set

    val patternDetectionEngine = PatternDetectionEngine()
    var notificationManager: StrategyNotificationManager? = null
        private set

    private val controllerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val evaluationMutex = Mutex()
    private var observerJob: Job? = null
    private var tickerJob: Job? = null

    private val _latestConfig = MutableStateFlow(StrategyConfig())
    val latestConfig: StateFlow<StrategyConfig> = _latestConfig.asStateFlow()

    private val _latestLimits = MutableStateFlow(RiskLimits())
    val latestLimits: StateFlow<RiskLimits> = _latestLimits.asStateFlow()

    private val _latestShortcuts = MutableStateFlow<List<FloatingShortcut>>(emptyList())
    val latestShortcuts: StateFlow<List<FloatingShortcut>> = _latestShortcuts.asStateFlow()

    private val _latestCustomStrategies = MutableStateFlow<List<CustomPatternStrategy>>(emptyList())
    val latestCustomStrategies: StateFlow<List<CustomPatternStrategy>> = _latestCustomStrategies.asStateFlow()

    private val _latestNotificationSettings = MutableStateFlow(NotificationSettings())
    val latestNotificationSettings: StateFlow<NotificationSettings> = _latestNotificationSettings.asStateFlow()

    private var lastAutoProfileId: String? = null
    private var lastHistoryUnavailableNoticeMs: Long = 0L

    fun ensureInitialized(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        if (repository == null) {
            val dao = ScreenEarnerDatabase.getDatabase(appContext).dao()
            val repo = ScreenEarnerRepository(dao)
            notificationManager = StrategyNotificationManager(appContext, repo)
            initialize(repo)
            BatteryOptimizationWorker.schedule(appContext)
        } else if (notificationManager == null) {
            notificationManager = StrategyNotificationManager(appContext, repository!!)
        }
    }

    fun initialize(repo: ScreenEarnerRepository, context: Context? = null) {
        if (context != null) {
            appContextRef = context.applicationContext
        }
        if (notificationManager == null) {
            notificationManager = StrategyNotificationManager(appContextRef, repo)
        }
        if (engine == null) {
            repository = repo
            engine = AutomationEngine(
                repo,
                StrategyEngine(),
                RiskManager(),
                GameDetection(),
                controllerScope
            )
            startBackgroundLoops(repo)
        }
    }

    private fun startBackgroundLoops(repo: ScreenEarnerRepository) {
        if (observerJob != null) return

        controllerScope.launch {
            repo.seedDefaultsIfEmpty()
        }

        controllerScope.launch {
            repo.strategyConfig.collect { cfg ->
                if (cfg != null) {
                    _latestConfig.value = cfg
                    engine?.updateCachedConfig(cfg)
                    engine?.strategyEngine?.bindStrategyConfig(cfg)
                    engine?.gameDetection?.gameState?.value?.let { gs ->
                        CurrencyManager.updateFromConfigAndGameState(cfg, gs)
                    }
                }
            }
        }

        controllerScope.launch {
            repo.riskLimits.collect { lim ->
                if (lim != null) {
                    _latestLimits.value = lim
                    engine?.updateCachedLimits(lim)
                    engine?.enforceLimitsNow(lim)
                }
            }
        }

        controllerScope.launch {
            repo.customStrategies.collect { strategies ->
                _latestCustomStrategies.value = strategies
                evaluatePatternStrategies(repo)
            }
        }

        controllerScope.launch {
            repo.notificationSettings.collect { settings ->
                if (settings != null) {
                    _latestNotificationSettings.value = settings
                    appContextRef?.let { ctx ->
                        AndroidSystemNotificationDispatcher.ensureStrategyChannel(ctx, settings)
                    }
                }
            }
        }

        controllerScope.launch {
            AccessibilityController.diagnosticMessage.collect { msg ->
                if (!msg.isNullOrBlank()) {
                    engine?.publishStatusTicker(msg)
                }
            }
        }

        controllerScope.launch {
            repo.multiplierRecords.collect { records ->
                val currentEngine = engine ?: return@collect
                currentEngine.syncSessionWithCrashHistory(records, _latestLimits.value)
                val cutoff30m = System.currentTimeMillis() - RibbonHistoryCollector.ROLLING_WINDOW_MS
                val windowRecords = records.filter { it.crashTimestamp >= cutoff30m }
                currentEngine.gameDetection.refreshHistoryMetadataFromRecords(windowRecords)
                evaluatePatternStrategies(repo)
            }
        }

        controllerScope.launch {
            repo.shortcuts.collect { list ->
                _latestShortcuts.value = list
                engine?.updateCalibratedShortcuts(list)
            }
        }

        observerJob = controllerScope.launch {
            ScreenEarnerAccessibilityService.detectionState.collect { detection ->
                try {
                    val currentEngine = engine ?: return@collect
                    val cfg = _latestConfig.value
                    val lim = _latestLimits.value

                    val targetProfileId = GameDetection.resolveProfileIdForPlatform(detection.platformName)
                    if (targetProfileId != null && targetProfileId != lastAutoProfileId) {
                        lastAutoProfileId = targetProfileId
                        currentEngine.updateActivePlatform(detection.platformName)
                        controllerScope.launch {
                            runCatching { repo.setActiveProfile(targetProfileId) }
                        }
                    } else if (detection.isGameDetected) {
                        currentEngine.updateActivePlatform(detection.platformName)
                    }

                    currentEngine.gameDetection.updateFromAccessibilityDetection(
                        detection = detection,
                        onRealRoundCompleted = { crashedMult ->
                            currentEngine.handleRoundCompleted(
                                multiplier = crashedMult,
                                config = cfg,
                                limits = lim,
                                platformName = currentEngine.gameDetection.gameState.value.platformName
                            )
                        },
                        onPhaseUpdate = { phase, liveMult ->
                            currentEngine.handlePhaseUpdate(
                                phase = phase,
                                liveMultiplier = liveMult,
                                config = cfg,
                                limits = lim,
                                siteButtonState = detection.uiTargets.siteButtonState
                            )
                        }
                    )

                    // Synchronize visible ribbon items into 30-minute history store with strict dedup
                    val newRibbonRecords = currentEngine.gameDetection.collectVisibleRibbonHistory(
                        detection = detection,
                        skipHeadIfJustSettled = true
                    )
                    if (newRibbonRecords.isNotEmpty()) {
                        repo.addMultiplierRecords(newRibbonRecords)
                    }

                    CurrencyManager.updateFromConfigAndGameState(cfg, currentEngine.gameDetection.gameState.value)
                    currentEngine.updateLiveDiagnostics(
                        targets = detection.uiTargets,
                        gameState = currentEngine.gameDetection.gameState.value,
                        config = cfg
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "Error processing accessibility detection event", t)
                }
            }
        }

        tickerJob = controllerScope.launch {
            while (isActive) {
                delay(1000L)
                val currentEngine = engine
                currentEngine?.tickTimers(_latestConfig.value)
                currentEngine?.gameDetection?.checkBalanceStaleness()
            }
        }
    }

    suspend fun evaluatePatternStrategies(repo: ScreenEarnerRepository? = repository) {
        val activeRepo = repo ?: return
        evaluationMutex.withLock {
            try {
                val strategies = activeRepo.getCustomStrategiesOnce()
                val enabled = strategies.filter { it.isEnabled }
                if (enabled.isEmpty()) return

                val nowMs = System.currentTimeMillis()
                val chronological = activeRepo.getRecentChronologicalRecords(
                    windowMinutes = RibbonHistoryCollector.ROLLING_WINDOW_MINUTES,
                    nowMs = nowMs
                )
                if (chronological.isEmpty()) return

                val batch = patternDetectionEngine.evaluateAllStrategies(
                    strategies = strategies,
                    records = chronological,
                    nowMs = nowMs
                )

                val originalById = strategies.associateBy { it.id }
                batch.updatedStrategies.forEach { updated ->
                    val original = originalById[updated.id]
                    if (original != null && updated != original) {
                        activeRepo.updateCustomStrategy(updated)
                    }
                }

                val mgr = notificationManager
                val settings = activeRepo.getNotificationSettingsOnce()
                batch.events.forEach { event ->
                    mgr?.dispatchPatternAlert(event, settings, nowMs)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Pattern strategy evaluation failed", t)
            }
        }
    }

    fun triggerManualHistoryCollection(context: Context) {
        ensureInitialized(context)
        val repo = repository ?: return
        val currentEngine = engine ?: return
        val a11y = ScreenEarnerAccessibilityService.instance
        val nowMs = System.currentTimeMillis()

        if (a11y == null) {
            currentEngine.gameDetection.updateHistoryStatus(
                status = HistoryCollectionStatus.HISTORY_UNAVAILABLE,
                reason = "Accessibility Service is not connected; cannot read game ribbon.",
                nowMs = nowMs
            )
            controllerScope.launch {
                maybeDispatchHistoryUnavailableNotice(
                    repo = repo,
                    reason = "Accessibility Service is disconnected. Enable it to collect 30-minute ribbon history.",
                    nowMs = nowMs
                )
            }
            return
        }

        val platform = currentEngine.gameDetection.gameState.value.platformName
            .takeIf { it.isNotBlank() && it != "Waiting for Game..." }
            ?: "BetPawa Aviator"

        a11y.collectAccessibleRibbonHistory(
            onStatusUpdate = { status, reason ->
                controllerScope.launch {
                    val recordsIn30m = repo.getRecentChronologicalRecords(
                        windowMinutes = RibbonHistoryCollector.ROLLING_WINDOW_MINUTES,
                        nowMs = System.currentTimeMillis()
                    )
                    currentEngine.gameDetection.updateHistoryStatus(
                        status = status,
                        reason = reason,
                        recordsInWindow = recordsIn30m
                    )
                    if (status == HistoryCollectionStatus.HISTORY_UNAVAILABLE ||
                        status == HistoryCollectionStatus.HISTORY_ERROR
                    ) {
                        maybeDispatchHistoryUnavailableNotice(repo, reason)
                    }
                }
            },
            onBatchExtracted = { items, isExhausted, covers30Min, source ->
                controllerScope.launch {
                    val result = currentEngine.gameDetection.ribbonHistoryCollector.processExpandedHistoryBatch(
                        items = items,
                        platformName = platform,
                        isScrolledExhausted = isExhausted,
                        coversFull30Minutes = covers30Min,
                        source = source
                    )
                    if (result.newRecordsToPersist.isNotEmpty()) {
                        repo.addMultiplierRecords(result.newRecordsToPersist)
                    }
                    val recordsIn30m = repo.getRecentChronologicalRecords(
                        windowMinutes = RibbonHistoryCollector.ROLLING_WINDOW_MINUTES
                    )
                    currentEngine.gameDetection.updateHistoryStatus(
                        status = result.status,
                        reason = result.reason,
                        recordsInWindow = recordsIn30m
                    )
                    evaluatePatternStrategies(repo)
                }
            }
        )
    }

    private suspend fun maybeDispatchHistoryUnavailableNotice(
        repo: ScreenEarnerRepository,
        reason: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val enabledStrategies = repo.getCustomStrategiesOnce().filter { it.isEnabled }
        if (enabledStrategies.isEmpty()) return
        if (nowMs - lastHistoryUnavailableNoticeMs < 5 * 60_000L) return
        lastHistoryUnavailableNoticeMs = nowMs

        val settings = repo.getNotificationSettingsOnce()
        notificationManager?.dispatchSystemNotice(
            strategyName = enabledStrategies.first().name,
            type = StrategyNotificationType.HISTORY_UNAVAILABLE,
            title = "History Collection Unavailable",
            message = reason,
            occurrenceKey = "hist_unavail_${nowMs / (5 * 60_000L)}",
            settings = settings,
            nowMs = nowMs
        )
    }

    fun retryBalanceDetection() {
        engine?.gameDetection?.retryBalanceDetection()
    }
}
