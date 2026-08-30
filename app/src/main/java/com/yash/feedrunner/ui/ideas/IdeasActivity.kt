package com.yash.feedrunner.ui.ideas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.yash.feedrunner.bubble.BubbleService
import com.yash.feedrunner.data.IdeaBankApi
import com.yash.feedrunner.data.IdeaBankRepository
import com.yash.feedrunner.ui.Platform
import com.yash.feedrunner.ui.SeedLane
import com.yash.feedrunner.ui.SeedStatus
import com.yash.feedrunner.ui.StoredSeed
import com.yash.feedrunner.ui.Streak
import com.yash.feedrunner.ui.theme.FeedRunnerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A stable identity for list keys: a queued seed has no server id yet. */
internal val StoredSeed.key: String
    get() = remoteId ?: "pending-$clientSeedId"

/** Everything the Ideas screen renders. */
data class IdeasUiState(
    val seeds: List<StoredSeed> = emptyList(),
    /** Seed whose ideation thread is open. Null means the list is showing. */
    val openSeedKey: String? = null,
    val filter: SeedStatus? = null,
    /**
     * Which half of the bank is showing. The engine files seeds several times a
     * day, so without this the harvested pile buries everything that came out
     * of Yash's own replying within a week.
     */
    val lane: SeedLane = SeedLane.ALL,
    /** Theme tag to narrow by, applied on top of the status filter. */
    val tagFilter: String? = null,
    /** Platform filter. Only offered once the bank holds both platforms. */
    val platformFilter: Platform? = null,
    val loading: Boolean = false,
    val generating: Boolean = false,
    val message: String? = null,
    val pendingCount: Int = 0,
    val baseUrl: String = "",
    /** Set while a generation for the open thread is in flight. */
    val generatingSeedKey: String? = null,
    /** Failure for the open thread, shown above its composer. */
    val threadError: String? = null,
    /** Null until the first load answers either way. */
    val serverReachable: Boolean? = null,
    val pendingDelete: StoredSeed? = null,
    /** Lane totals as the server counts them; drive the tab labels. */
    val harvestedCount: Int = 0,
    val mineCount: Int = 0,
    /** Always present: cached locally, so a dead backend cannot hide the card. */
    val streak: Streak = Streak(),
    /** Mirrors BubbleService.running for the header's bubble switch. */
    val bubbleRunning: Boolean = false,
) {
    val backendConfigured: Boolean get() = baseUrl.isNotEmpty()

    /** Seeds in the chosen lane, before the finer filters. */
    private val inLane: List<StoredSeed> get() = seeds.filter { lane.accepts(it.source) }

    /**
     * Tags across the current lane, most common first.
     *
     * Scoped to the lane rather than the whole bank on purpose: harvested seeds
     * carry their category as the first tag, so an unscoped row offered
     * "shitpost" and "war_story" as filters while you were looking at seeds
     * that can never match them.
     */
    val availableTags: List<String>
        get() = inLane
            .flatMap { it.seed.themeTags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(MAX_TAG_CHIPS)

    /**
     * How many seeds each lane holds, as the server counts them.
     *
     * From the server rather than from [seeds], because a lane is fetched on
     * its own: counting the loaded list would report the lane you are looking
     * at as the whole bank and the other one as empty.
     */
    val laneCounts: Map<SeedLane, Int>
        get() = mapOf(
            SeedLane.ALL to (harvestedCount + mineCount),
            SeedLane.HARVESTED to harvestedCount,
            SeedLane.MINE to mineCount,
        )

    /** The tab row only earns its space once both lanes have something in them. */
    val showLanes: Boolean get() = harvestedCount > 0 && mineCount > 0

    /** The platforms actually in the bank; the filter row earns its place at two. */
    val presentPlatforms: List<Platform>
        get() = Platform.entries.filter { p -> seeds.any { it.platform == p } }

    /** The status filter is applied server-side; the rest are local. */
    val visibleSeeds: List<StoredSeed>
        get() {
            var visible = inLane
            platformFilter?.let { p -> visible = visible.filter { it.platform == p } }
            tagFilter?.let { tag -> visible = visible.filter { tag in it.seed.themeTags } }
            return visible
        }

    /** True when something the user chose is hiding seeds that exist. */
    val anyFilterActive: Boolean
        get() = filter != null || tagFilter != null ||
            platformFilter != null || lane != SeedLane.ALL

    /** The seed being worked on, if its thread is open. */
    val openSeed: StoredSeed? get() = seeds.firstOrNull { it.key == openSeedKey }
}

/** Callbacks the screen invokes, kept in one place so the composable stays dumb. */
data class IdeasActions(
    val onRefresh: () -> Unit,
    val onFilterChange: (SeedStatus?) -> Unit,
    val onLaneChange: (SeedLane) -> Unit,
    val onTagFilterChange: (String?) -> Unit,
    val onPlatformFilterChange: (Platform?) -> Unit,
    val onClearFilters: () -> Unit,
    val onOpenSeed: (StoredSeed) -> Unit,
    val onSetStatus: (StoredSeed, SeedStatus) -> Unit,
    val onAddManual: (String) -> Unit,
    val onSetBaseUrl: (String) -> Unit,
    val onCopy: (String) -> Unit,
    /** Opens a harvested seed's original post in the browser or the X app. */
    val onOpenLink: (String) -> Unit,
    val onAskDelete: (StoredSeed) -> Unit,
    val onToggleBubble: () -> Unit,
    val onOpenSetup: () -> Unit,
)

/**
 * Browses banked seeds and turns them into post ideas.
 *
 * A plain activity rather than part of the overlay: this is something you sit down
 * with, not something you reach for mid-scroll.
 */
class IdeasActivity : ComponentActivity() {

    private lateinit var repository: IdeaBankRepository
    private var state by mutableStateOf(IdeasUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The front door: no account, no app. One-time — the session token
        // never expires, so this fires only before first sign-in or after a
        // deliberate sign-out.
        if (!com.yash.feedrunner.data.BackendConfig(this).isLoggedIn) {
            startActivity(
                android.content.Intent(this, com.yash.feedrunner.AuthActivity::class.java),
            )
            finish()
            return
        }
        // The wash runs behind the status bar; screens pad their own edges.
        enableEdgeToEdge()
        repository = IdeaBankRepository(this)
        state = state.copy(
            baseUrl = repository.backendConfig.baseUrl,
            streak = repository.cachedStreak(),
        )

        setContent {
            FeedRunnerTheme {
                // Only the keyboard inset is handled here; status and navigation
                // bars are the screens' own job, so their headers can run edge
                // to edge behind the status bar.
                Surface(modifier = Modifier.imePadding()) {
                    state.pendingDelete?.let { seed ->
                        ConfirmDeleteSeedDialog(
                            seed = seed,
                            onConfirm = { delete(seed) },
                            onCancel = { state = state.copy(pendingDelete = null) },
                        )
                    }

                    val open = state.openSeed
                    if (open != null) {
                        SeedThreadScreen(
                            seed = open,
                            generating = state.generatingSeedKey == open.key,
                            error = state.threadError,
                            onBack = { state = state.copy(openSeedKey = null, threadError = null) },
                            onGenerate = { instruction -> generate(open, instruction) },
                            onDeleteIdea = { idea -> deleteIdea(open, idea) },
                            onCopy = ::copy,
                            onOpenLink = ::openLink,
                            onSetStatus = { status -> setStatus(open, status) },
                            onDeleteSeed = { state = state.copy(pendingDelete = open) },
                        )
                    } else {
                        IdeasScreen(
                            // Read here so the header's dot follows the service
                            // as it starts and stops.
                            state = state.copy(bubbleRunning = BubbleService.running.value),
                            actions = IdeasActions(
                                onRefresh = ::refresh,
                                onFilterChange = ::changeFilter,
                                onLaneChange = ::changeLane,
                                onTagFilterChange = { state = state.copy(tagFilter = it) },
                                onPlatformFilterChange = {
                                    state = state.copy(platformFilter = it)
                                },
                                onClearFilters = {
                                    state = state.copy(
                                        tagFilter = null,
                                        platformFilter = null,
                                        lane = SeedLane.ALL,
                                        filter = null,
                                    )
                                    refresh()
                                },
                                onOpenSeed = ::openSeed,
                                onSetStatus = ::setStatus,
                                onAddManual = ::addManual,
                                onSetBaseUrl = ::setBaseUrl,
                                onCopy = ::copy,
                                onOpenLink = ::openLink,
                                onAskDelete = { state = state.copy(pendingDelete = it) },
                                onToggleBubble = ::toggleBubble,
                                onOpenSetup = ::openSetup,
                            ),
                        )
                    }
                }
            }
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        BubbleService.setOwnUiVisible(this, true)
        // Seeds are banked by the bubble service while this screen is closed.
        if (state.seeds.isNotEmpty()) refresh()
    }

    override fun onPause() {
        super.onPause()
        BubbleService.setOwnUiVisible(this, false)
    }

    private fun refresh() {
        state = state.copy(loading = true, message = null)
        val filter = state.filter
        val lane = state.lane

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.loadSeeds(filter, lane) }
            val pending = withContext(Dispatchers.IO) { repository.pendingCount }
            val streak = withContext(Dispatchers.IO) { repository.streak() }

            state = outcome.fold(
                onSuccess = { page ->
                    state.copy(
                        seeds = page.seeds,
                        harvestedCount = page.harvestedCount,
                        mineCount = page.mineCount,
                        loading = false,
                        pendingCount = pending,
                        serverReachable = true,
                        streak = streak,
                    )
                },
                onFailure = { error ->
                    // Falling back to the last list plus the queue rather than an
                    // empty screen: those seeds exist, they just are not reachable.
                    val queued = withContext(Dispatchers.IO) {
                        repository.queuedSeeds(filter, lane)
                    }
                    state.copy(
                        seeds = queued,
                        loading = false,
                        pendingCount = pending,
                        serverReachable = false,
                        // Counts are deliberately left as they were: they came
                        // from the server and a stale total beats flashing the
                        // tabs to zero every time the backend naps.
                        streak = streak,
                        message = error.message ?: "Could not reach the backend",
                    )
                },
            )
        }
    }

    private fun changeFilter(filter: SeedStatus?) {
        state = state.copy(filter = filter)
        refresh()
    }

    /** The lane is a server-side filter, so switching tab refetches. */
    private fun changeLane(lane: SeedLane) {
        // A tag chosen in one lane usually does not exist in the other, which
        // would land you on an empty list you did not ask for.
        state = state.copy(lane = lane, tagFilter = null)
        refresh()
    }

    /** Opens a seed's ideation thread. Needs a server id to generate against. */
    private fun openSeed(seed: StoredSeed) {
        if (seed.remoteId == null) {
            toast("That seed has not synced yet")
            return
        }
        state = state.copy(openSeedKey = seed.key, threadError = null)
    }

    private fun setStatus(seed: StoredSeed, status: SeedStatus) {
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.setStatus(seed, status) }
            outcome.fold(
                onSuccess = { updated ->
                    state = state.copy(seeds = state.seeds.replacing(updated))
                    // A status change can move a seed out of the active filter.
                    if (state.filter != null && state.openSeedKey == null) refresh()
                },
                onFailure = { toast(it.message ?: "Could not update") },
            )
        }
    }

    private fun delete(seed: StoredSeed) {
        state = state.copy(pendingDelete = null)
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.delete(seed) }
            outcome.fold(
                onSuccess = {
                    state = state.copy(
                        seeds = state.seeds.filterNot { it.key == seed.key },
                        openSeedKey = state.openSeedKey.takeIf { it != seed.key },
                    )
                },
                onFailure = { toast(it.message ?: "Could not delete") },
            )
        }
    }

    /**
     * Generates for one seed. An empty instruction is a plain generate; anything
     * typed steers this round and is stored as a turn in the thread.
     */
    private fun generate(seed: StoredSeed, instruction: String) {
        if (state.generatingSeedKey != null) return
        state = state.copy(generatingSeedKey = seed.key, threadError = null)

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                repository.generateForSeed(seed, instruction)
            }
            state = outcome.fold(
                onSuccess = {
                    state.copy(seeds = state.seeds.replacing(it), generatingSeedKey = null)
                },
                onFailure = {
                    state.copy(
                        generatingSeedKey = null,
                        threadError = it.message ?: "Generation failed",
                    )
                },
            )
        }
    }

    /** Removes one generated post. The server keeps the tally of what was cleared. */
    private fun deleteIdea(seed: StoredSeed, idea: com.yash.feedrunner.ui.SeedIdea) {
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.deleteIdea(seed, idea.id) }
            outcome.fold(
                onSuccess = { state = state.copy(seeds = state.seeds.replacing(it)) },
                onFailure = { toast(it.message ?: "Could not delete") },
            )
        }
    }

    /** Swaps in an updated seed, matched on whichever id it has. */
    private fun List<StoredSeed>.replacing(updated: StoredSeed): List<StoredSeed> = map { seed ->
        if (seed.clientSeedId == updated.clientSeedId ||
            (seed.remoteId != null && seed.remoteId == updated.remoteId)
        ) {
            updated
        } else {
            seed
        }
    }

    /**
     * Saves a hand-typed idea and opens its thread, so "add my own idea" lands you
     * straight in the place where you work on it rather than back in the list.
     */
    private fun addManual(note: String) {
        // A typed idea lands in the "from me" lane, so adding one while looking
        // at the harvested lane would file it somewhere you cannot see. Move to
        // a lane that contains it rather than appearing to have lost it.
        val lane = if (state.lane == SeedLane.HARVESTED) SeedLane.MINE else state.lane
        val filter = state.filter

        lifecycleScope.launch {
            val clientSeedId = withContext(Dispatchers.IO) { repository.addManual(note) }
            val outcome = withContext(Dispatchers.IO) { repository.loadSeeds(filter, lane) }
            val page = outcome.getOrElse {
                IdeaBankApi.SeedPage(
                    seeds = withContext(Dispatchers.IO) { repository.queuedSeeds(filter, lane) },
                    harvestedCount = state.harvestedCount,
                    mineCount = state.mineCount,
                )
            }
            val added = page.seeds.firstOrNull { it.clientSeedId == clientSeedId }
            state = state.copy(
                seeds = page.seeds,
                lane = lane,
                harvestedCount = page.harvestedCount,
                mineCount = page.mineCount,
                // Only synced seeds can generate, so a queued one stays in the list.
                openSeedKey = added?.takeIf { it.remoteId != null }?.key,
            )
        }
    }

    private fun setBaseUrl(url: String) {
        repository.backendConfig.baseUrl = url
        state = state.copy(baseUrl = repository.backendConfig.baseUrl, serverReachable = null)
        refresh()
    }

    /**
     * The header's bubble switch. When setup is incomplete the switch cannot
     * work, so it takes you to the screen that can fix it instead of failing.
     */
    private fun toggleBubble() {
        if (BubbleService.running.value) {
            BubbleService.stop(this)
            return
        }
        if (!setupComplete()) {
            toast("Finish setup first")
            openSetup()
            return
        }
        BubbleService.start(this)
    }

    private fun openSetup() {
        startActivity(android.content.Intent(this, com.yash.feedrunner.MainActivity::class.java))
    }

    private fun setupComplete(): Boolean {
        if (!android.provider.Settings.canDrawOverlays(this)) return false
        val expected = android.content.ComponentName(
            this,
            com.yash.feedrunner.capture.CaptureService::class.java,
        ).flattenToString()
        val enabled = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun copy(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("idea", text))
    }

    /**
     * Opens a harvested seed's original post. Android hands x.com links to the
     * X app when it is installed, which is where you want to be to quote one.
     *
     * A seed can outlive the post it came from, so a link that resolves to
     * nothing is a normal outcome rather than a bug; the only thing worth
     * handling is there being no browser at all.
     */
    private fun openLink(url: String) {
        if (url.isBlank()) return
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure { toast("Nothing here can open that link") }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

/** Enough to be useful without the row becoming its own scrolling problem. */
private const val MAX_TAG_CHIPS = 8
