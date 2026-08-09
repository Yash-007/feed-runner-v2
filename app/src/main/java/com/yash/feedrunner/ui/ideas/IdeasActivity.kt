package com.yash.feedrunner.ui.ideas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.yash.feedrunner.data.IdeaBankRepository
import com.yash.feedrunner.ui.PostIdea
import com.yash.feedrunner.ui.SeedStatus
import com.yash.feedrunner.ui.StoredSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the Ideas screen renders. */
data class IdeasUiState(
    val seeds: List<StoredSeed> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val ideas: List<PostIdea> = emptyList(),
    val filter: SeedStatus? = null,
    val loading: Boolean = false,
    val generating: Boolean = false,
    val message: String? = null,
    val pendingCount: Int = 0,
    val baseUrl: String = "",
) {
    val backendConfigured: Boolean get() = baseUrl.isNotEmpty()
}

/** Callbacks the screen invokes, kept in one place so the composable stays dumb. */
data class IdeasActions(
    val onRefresh: () -> Unit,
    val onFilterChange: (SeedStatus?) -> Unit,
    val onToggleSelect: (StoredSeed) -> Unit,
    val onSetStatus: (StoredSeed, SeedStatus) -> Unit,
    val onGenerate: (String) -> Unit,
    val onAddManual: (String) -> Unit,
    val onSetBaseUrl: (String) -> Unit,
    val onCopy: (String) -> Unit,
    val onClearIdeas: () -> Unit,
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
        state = state.copy(baseUrl = repository.backendConfig.baseUrl)

        setContent {
            MaterialTheme {
                Surface {
                    IdeasScreen(
                        state = state,
                        actions = IdeasActions(
                            onRefresh = ::refresh,
                            onFilterChange = ::changeFilter,
                            onToggleSelect = ::toggleSelect,
                            onSetStatus = ::setStatus,
                            onGenerate = ::generate,
                            onAddManual = ::addManual,
                            onSetBaseUrl = ::setBaseUrl,
                            onCopy = ::copy,
                            onClearIdeas = { state = state.copy(ideas = emptyList()) },
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
                    state.copy(seeds = seeds, loading = false, pendingCount = pending)
                },
                onFailure = { error ->
                    // Falling back to the queue rather than an empty screen: those
                    // seeds exist, they just have not reached the server.
                    val queued = withContext(Dispatchers.IO) { repository.queuedSeeds() }
                    state.copy(
                        seeds = queued,
                        loading = false,
                        pendingCount = pending,
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

    private fun setStatus(seed: StoredSeed, status: SeedStatus) {
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.setStatus(seed, status) }
            outcome.fold(
                onSuccess = { updated ->
                    state = state.copy(
                        seeds = state.seeds.map {
                            if (it.clientSeedId == seed.clientSeedId ||
                                it.remoteId == seed.remoteId
                            ) {
                                updated
                            } else {
                                it
                            }
                        },
                    )
                    // A status change can move a seed out of the active filter.
                    if (state.filter != null) refresh()
                },
                onFailure = { toast(it.message ?: "Could not update") },
            )
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
        state = state.copy(baseUrl = repository.backendConfig.baseUrl)
        refresh()
    }

    private fun copy(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("idea", text))
        toast("copied")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
