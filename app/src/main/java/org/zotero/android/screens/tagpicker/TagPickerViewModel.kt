package org.zotero.android.screens.tagpicker

import dagger.hilt.android.lifecycle.HiltViewModel
import org.greenrobot.eventbus.EventBus
import org.zotero.android.architecture.BaseViewModel2
import org.zotero.android.architecture.ScreenArguments
import org.zotero.android.architecture.ViewEffect
import org.zotero.android.architecture.ViewState
import org.zotero.android.database.DbWrapper
import org.zotero.android.database.requests.ReadTagsDbRequest
import org.zotero.android.ktx.index
import org.zotero.android.screens.tagpicker.data.TagPickerResult
import org.zotero.android.sync.LibraryIdentifier
import org.zotero.android.sync.Tag
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
internal class TagPickerViewModel @Inject constructor(
    private val dbWrapper: DbWrapper,
) : BaseViewModel2<TagPickerViewState, TagPickerViewEffect>(TagPickerViewState()) {

    fun init() = initOnce {
        val args = ScreenArguments.tagPickerArgs
        updateState {
            copy(libraryId = args.libraryId, selectedTags = args.selectedTags, tags = args.tags)
        }
        if (viewState.tags.isEmpty()) {
            load()
        }
    }

    fun selectOrDeselect(name: String) {
        if (viewState.selectedTags.contains(name)) {
            deselect(name)
        } else {
            select(name)
        }
    }

    private fun select(name: String) {
        updateState {
            copy(selectedTags = selectedTags + name)
        }
    }

    private fun deselect(name: String) {
        updateState {
            copy(selectedTags = selectedTags - name)
        }
    }

    private fun add(name: String) {
        val snapshot = viewState.snapshot ?: return
        val tag = Tag(name = name, color = "")
        updateState {
            copy(tags = snapshot)
        }

        val index = viewState.tags.index(
            tag,
            sortedBy = { first, second ->
                first.name.compareTo(
                    second.name,
                    ignoreCase = true
                ) == 1
            })

        val updatedTags = viewState.tags.toMutableList()
        updatedTags.add(index, tag)
        val updatedSelectedTags = viewState.selectedTags.toMutableSet()
        updatedSelectedTags.add(name)

        updateState {
            copy(
                tags = updatedTags,
                selectedTags = updatedSelectedTags,
                snapshot = null,
                searchTerm = "",
                addedTagName = name,
                showAddTagButton = false
            )
        }
    }


    private fun search(term: String) {
        if (!term.isEmpty()) {
            if (viewState.snapshot == null) {
                updateState {
                    copy(snapshot = viewState.tags)
                }
            }
            var updatedTags = viewState.snapshot ?: viewState.tags
            updatedTags = updatedTags.filter { it.name.contains(term, ignoreCase = true) }
            updateState {
                copy(
                    searchTerm = term,
                    tags = updatedTags,
                    showAddTagButton = viewState.tags.isEmpty() || viewState.tags.firstOrNull { it.name == term } == null)
            }
        } else {
            val snapshot = viewState.snapshot ?: return
            updateState {
                copy(
                    tags = snapshot,
                    snapshot = null,
                    searchTerm = "",
                    showAddTagButton = false
                )
            }
        }
    }

    private fun load() {
        try {
            val request = ReadTagsDbRequest(libraryId = viewState.libraryId!!)
            val tags = dbWrapper.realmDbStorage.perform(request = request).toMutableList()
            sortByColors(tags = tags)
            updateState {
                copy(tags = tags)
            }
        } catch (e: Exception) {
            Timber.e(e, "TagPickerStore: can't load tag")
        }
    }

    private fun sortByColors(tags: MutableList<Tag>) {
        val coloredIndices = mutableListOf<Int>()
        for ((index, tag) in tags.withIndex()) {
            if (!tag.color.isEmpty()) {
                coloredIndices.add(index)
            }
        }

        val coloredTags = mutableListOf<Tag>()
        for (idx in coloredIndices.reversed()) {
            coloredTags.add(tags.removeAt(idx))
        }
        tags.addAll(0, coloredTags)
    }

    fun onSave() {
        val allTags = viewState.snapshot ?: viewState.tags
        val tags = viewState.selectedTags.mapNotNull { id ->
            allTags.firstOrNull { it.id == id }
        }.sortedBy { it.name }
        EventBus.getDefault().post(TagPickerResult(tags))
        triggerEffect(TagPickerViewEffect.OnBack)
    }

}

internal data class TagPickerViewState(
    val libraryId: LibraryIdentifier? = null,
    val tags: List<Tag> = emptyList(),
    val snapshot: List<Tag>? = null,
    var selectedTags: Set<String> = emptySet(),
    var searchTerm: String = "",
    var showAddTagButton: Boolean = false,
    var addedTagName: String? = null
) : ViewState

internal sealed class TagPickerViewEffect : ViewEffect {
    object OnBack : TagPickerViewEffect()
}