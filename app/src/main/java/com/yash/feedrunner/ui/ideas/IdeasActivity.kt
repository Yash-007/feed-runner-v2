package com.yash.feedrunner.ui.ideas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.yash.feedrunner.bubble.BubbleService
import com.yash.feedrunner.data.IdeaBankRepository
import com.yash.feedrunner.ui.Platform
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
    /** Always present: cached locally, so a dead backend cannot hide the card. */
    val streak: Streak = Streak(),
) {
    val backendConfigured: Boolean get() = baseUrl.isNotEmpty()

    /** Tags across everything loaded, most common first, so the row is useful. */
    val availableTags: List<String>
        get() = seeds
            .flatMap { it.seed.themeTags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(MAX_TAG_CHIPS)

    /** True once seeds from both networks exist, which is when a filter earns a row. */
    val hasBothPlatforms: Boolean
        get() = seeds.any { it.platform == Platform.LINKEDIN } &&
            seeds.any { it.platform == Platform.X }

    /** The status filter is applied server-side; tag and platform are local. */
    val visibleSeeds: List<StoredSeed>
        get() {
            var visible = seeds
            platformFilter?.let { p -> visible = visible.filter { it.platform == p } }
            tagFilter?.let { tag -> visible = visible.filter { tag in it.seed.themeTags } }
            return visible
        }

    /** The seed being worked on, if its thread is open. */
    val openSeed: StoredSeed? get() = seeds.firstOrNull { it.key == openSeedKey }
}

/** Callbacks the screen invokes, kept in one place so the composable stays dumb. */
data class IdeasActions(
    val onRefresh: () -> Unit,
    val onFilterChange: (SeedStatus?) -> Unit,
    val onTagFilterChange: (String?) -> Unit,
    val onPlatformFilterChange: (Platform?) -> Unit,
    val onClearFilters: () -> Unit,
    val onOpenSeed: (StoredSeed) -> Unit,
    val onSetStatus: (StoredSeed, SeedStatus) -> Unit,
    val onAddManual: (String) -> Unit,
    val onSetBaseUrl: (String) -> Unit,
    val onCopy: (String) -> Unit,
    val onAskDelete: (StoredSeed) -> Unit,
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
        repository = IdeaBankRepository(this)
        state = state.copy(
            baseUrl = repository.backendConfig.baseUrl,
            streak = repository.cachedStreak(),
        )

        setContent {
            FeedRunnerTheme {
                // targetSdk 35 draws edge to edge, so without this the composer sits
                // under the navigation bar and the header under the status bar.
                // safeDrawing covers the keyboard too, which is what a chat wants.
                Surface(modifier = Modifier.safeDrawingPadding()) {
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
                            onSetStatus = { status -> setStatus(open, status) },
                            onDeleteSeed = { state = state.copy(pendingDelete = open) },
                        )
                    } else {
                        IdeasScreen(
                            state = state,
                            actions = IdeasActions(
                                onRefresh = ::refresh,
                                onFilterChange = ::changeFilter,
                                onTagFilterChange = { state = state.copy(tagFilter = it) },
                                onPlatformFilterChange = {
                                    state = state.copy(platformFilter = it)
                                },
                                onClearFilters = {
                                    state = state.copy(tagFilter = null, platformFilter = null)
                                    changeFilter(null)
                                },
                                onOpenSeed = ::openSeed,
                                onSetStatus = ::setStatus,
                                onAddManual = ::addManual,
                                onSetBaseUrl = ::setBaseUrl,
                                onCopy = ::copy,
                                onAskDelete = { state = state.copy(pendingDelete = it) },
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

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.loadSeeds(filter) }
            val pending = withContext(Dispatchers.IO) { repository.pendingCount }
            val streak = withContext(Dispatchers.IO) { repository.streak() }

            state = outcome.fold(
                onSuccess = { seeds ->
                    state.copy(
                        seeds = seeds,
                        loading = false,
                        pendingCount = pending,
                        serverReachable = true,
                        streak = streak,
                    )
                },
                onFailure = { error ->
                    // Falling back to the last list plus the queue rather than an
                    // empty screen: those seeds exist, they just are not reachable.
                    val queued = withContext(Dispatchers.IO) { repository.queuedSeeds(filter) }
                    state.copy(
                        seeds = queued,
                        loading = false,
                        pendingCount = pending,
                        serverReachable = false,
                        // Cached, so the streak survives the backend being away.
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
        lifecycleScope.launch {
            val clientSeedId = withContext(Dispatchers.IO) { repository.addManual(note) }
            val outcome = withContext(Dispatchers.IO) { repository.loadSeeds(state.filter) }
            val seeds = outcome.getOrElse {
                withContext(Dispatchers.IO) { repository.queuedSeeds() }
            }
            val added = seeds.firstOrNull { it.clientSeedId == clientSeedId }
            state = state.copy(
                seeds = seeds,
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

    private fun copy(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("idea", text))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

/** Enough to be useful without the row becoming its own scrolling problem. */
private const val MAX_TAG_CHIPS = 8
