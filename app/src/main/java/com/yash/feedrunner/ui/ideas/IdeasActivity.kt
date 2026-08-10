package com.yash.feedrunner.ui.ideas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.yash.feedrunner.data.IdeaBankRepository
import com.yash.feedrunner.ui.ChatMessage
import com.yash.feedrunner.ui.ChatRole
import com.yash.feedrunner.ui.PostIdea
import com.yash.feedrunner.ui.SeedStatus
import com.yash.feedrunner.ui.StoredSeed
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
    val selectedIds: Set<String> = emptySet(),
    val ideas: List<PostIdea> = emptyList(),
    val filter: SeedStatus? = null,
    /** Theme tag to narrow by, applied on top of the status filter. */
    val tagFilter: String? = null,
    val loading: Boolean = false,
    val generating: Boolean = false,
    val message: String? = null,
    val pendingCount: Int = 0,
    val baseUrl: String = "",
    /** Card showing its full detail. Null when everything is collapsed. */
    val expandedSeedKey: String? = null,
    /** Card with its conversation open. Always also expanded. */
    val chatSeedKey: String? = null,
    val chatPendingId: String? = null,
    /** Failed chat turn, scoped to the seed it belongs to. */
    val chatError: String? = null,
    val chatErrorId: String? = null,
    /** Null until the first load answers either way. */
    val serverReachable: Boolean? = null,
    val pendingDelete: StoredSeed? = null,
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

    /** The status filter is applied server-side; the tag filter is local. */
    val visibleSeeds: List<StoredSeed>
        get() = tagFilter?.let { tag -> seeds.filter { tag in it.seed.themeTags } } ?: seeds
}

/** Callbacks the screen invokes, kept in one place so the composable stays dumb. */
data class IdeasActions(
    val onRefresh: () -> Unit,
    val onFilterChange: (SeedStatus?) -> Unit,
    val onTagFilterChange: (String?) -> Unit,
    val onClearFilters: () -> Unit,
    val onToggleSelect: (StoredSeed) -> Unit,
    val onClearSelection: () -> Unit,
    val onToggleExpand: (StoredSeed) -> Unit,
    val onSetStatus: (StoredSeed, SeedStatus) -> Unit,
    val onGenerate: (String) -> Unit,
    val onAddManual: (String) -> Unit,
    val onSetBaseUrl: (String) -> Unit,
    val onCopy: (String) -> Unit,
    val onClearIdeas: () -> Unit,
    val onToggleChat: (StoredSeed) -> Unit,
    val onSendChat: (StoredSeed, String) -> Unit,
    val onRetryChat: (StoredSeed) -> Unit,
    val onAskDelete: (StoredSeed) -> Unit,
    val onConfirmDelete: (StoredSeed) -> Unit,
    val onCancelDelete: () -> Unit,
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

    /** Kept so a failed chat turn can be retried without retyping it. */
    private var lastChatMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = IdeaBankRepository(this)
        state = state.copy(baseUrl = repository.backendConfig.baseUrl)

        setContent {
            FeedRunnerTheme {
                Surface {
                    IdeasScreen(
                        state = state,
                        actions = IdeasActions(
                            onRefresh = ::refresh,
                            onFilterChange = ::changeFilter,
                            onTagFilterChange = { state = state.copy(tagFilter = it) },
                            onClearFilters = {
                                state = state.copy(tagFilter = null)
                                changeFilter(null)
                            },
                            onToggleSelect = ::toggleSelect,
                            onClearSelection = { state = state.copy(selectedIds = emptySet()) },
                            onToggleExpand = ::toggleExpand,
                            onSetStatus = ::setStatus,
                            onGenerate = ::generate,
                            onAddManual = ::addManual,
                            onSetBaseUrl = ::setBaseUrl,
                            onCopy = ::copy,
                            onClearIdeas = { state = state.copy(ideas = emptyList()) },
                            onToggleChat = ::toggleChat,
                            onSendChat = ::sendChat,
                            onRetryChat = ::retryChat,
                            onAskDelete = { state = state.copy(pendingDelete = it) },
                            onConfirmDelete = ::delete,
                            onCancelDelete = { state = state.copy(pendingDelete = null) },
                        ),
                    )
                }
            }
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        // Seeds are banked by the bubble service while this screen is closed.
        if (state.seeds.isNotEmpty()) refresh()
    }

    private fun refresh() {
        state = state.copy(loading = true, message = null)
        val filter = state.filter

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.loadSeeds(filter) }
            val pending = withContext(Dispatchers.IO) { repository.pendingCount }

            state = outcome.fold(
                onSuccess = { seeds ->
                    state.copy(
                        seeds = seeds,
                        loading = false,
                        pendingCount = pending,
                        serverReachable = true,
                    )
                },
                onFailure = { error ->
                    // Falling back to the queue rather than an empty screen: those
                    // seeds exist, they just have not reached the server.
                    val queued = withContext(Dispatchers.IO) { repository.queuedSeeds() }
                    state.copy(
                        seeds = queued,
                        loading = false,
                        pendingCount = pending,
                        serverReachable = false,
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

    private fun toggleSelect(seed: StoredSeed) {
        val id = seed.remoteId
        if (id == null) {
            toast("That seed has not synced yet")
            return
        }
        val selected = state.selectedIds.toMutableSet()
        if (!selected.add(id)) selected.remove(id)
        state = state.copy(selectedIds = selected)
    }

    /** Collapsing a card also closes its conversation; nothing stays open offscreen. */
    private fun toggleExpand(seed: StoredSeed) {
        val opening = seed.key != state.expandedSeedKey
        state = state.copy(
            expandedSeedKey = seed.key.takeIf { opening },
            chatSeedKey = state.chatSeedKey.takeIf { opening && it == seed.key },
        )
    }

    private fun setStatus(seed: StoredSeed, status: SeedStatus) {
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.setStatus(seed, status) }
            outcome.fold(
                onSuccess = { updated ->
                    state = state.copy(seeds = state.seeds.replacing(updated))
                    // A status change can move a seed out of the active filter.
                    if (state.filter != null) refresh()
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
                        selectedIds = state.selectedIds - seed.remoteId.orEmpty(),
                        expandedSeedKey = state.expandedSeedKey.takeIf { it != seed.key },
                        chatSeedKey = state.chatSeedKey.takeIf { it != seed.key },
                    )
                },
                onFailure = { toast(it.message ?: "Could not delete") },
            )
        }
    }

    private fun toggleChat(seed: StoredSeed) {
        if (seed.remoteId == null) {
            toast("That seed has not synced yet")
            return
        }
        state = state.copy(chatSeedKey = seed.key.takeIf { it != state.chatSeedKey })
    }

    private fun sendChat(seed: StoredSeed, message: String) {
        if (message.isBlank() || state.chatPendingId != null) return

        // Show the typed turn straight away; the server appends both turns once
        // the model answers, and the response replaces this optimistic copy.
        lastChatMessage = message
        val optimistic = seed.copy(chat = seed.chat + ChatMessage(ChatRole.USER, message))
        state = state.copy(
            seeds = state.seeds.replacing(optimistic),
            chatPendingId = seed.remoteId,
            chatError = null,
            chatErrorId = null,
        )

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.chat(seed, message) }
            state = outcome.fold(
                onSuccess = { state.copy(seeds = state.seeds.replacing(it), chatPendingId = null) },
                onFailure = { error ->
                    // Drop the optimistic turn, it never reached the server, and
                    // show the failure on the card rather than as a toast.
                    state.copy(
                        seeds = state.seeds.replacing(seed),
                        chatPendingId = null,
                        chatError = error.message ?: "Chat failed",
                        chatErrorId = seed.remoteId,
                    )
                },
            )
        }
    }

    private fun retryChat(seed: StoredSeed) {
        val message = lastChatMessage ?: return
        state = state.copy(chatError = null, chatErrorId = null)
        sendChat(seed, message)
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

    private fun generate(steer: String) {
        val chosen = state.seeds.filter { it.remoteId in state.selectedIds }
        if (chosen.isEmpty()) return

        state = state.copy(generating = true, message = null)
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.generateIdeas(chosen, steer) }
            state = outcome.fold(
                onSuccess = { state.copy(ideas = it, generating = false) },
                onFailure = {
                    state.copy(generating = false, message = it.message ?: "Generation failed")
                },
            )
        }
    }

    private fun addManual(note: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { repository.addManual(note) }
            refresh()
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
