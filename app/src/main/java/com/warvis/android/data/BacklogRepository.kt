package com.warvis.android.data

import android.content.Context
import android.content.SharedPreferences
import com.warvis.android.data.model.BacklogBucket
import com.warvis.android.data.model.BacklogBucketId
import com.warvis.android.data.model.BacklogItem
import com.warvis.android.data.model.BacklogState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class BacklogRepository(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<BacklogState> = _state.asStateFlow()

    fun addItem(bucketId: BacklogBucketId, title: String) {
        val item = BacklogItem(
            id = UUID.randomUUID().toString(),
            bucketId = bucketId,
            title = title.trim(),
        )
        _state.update { current ->
            val next = current.copy(
                buckets = current.buckets.map { bucket ->
                    if (bucket.id == bucketId) bucket.copy(items = bucket.items + item)
                    else bucket
                },
            )
            persistAll(next)
            next
        }
    }

    fun editItem(item: BacklogItem, newTitle: String) {
        _state.update { current ->
            val next = current.copy(
                buckets = current.buckets.map { bucket ->
                    if (bucket.id == item.bucketId) {
                        bucket.copy(items = bucket.items.map { existing ->
                            if (existing.id == item.id) existing.copy(title = newTitle.trim())
                            else existing
                        })
                    } else bucket
                },
            )
            persistAll(next)
            next
        }
    }

    fun deleteItem(item: BacklogItem) {
        _state.update { current ->
            val next = current.copy(
                buckets = current.buckets.map { bucket ->
                    if (bucket.id == item.bucketId) {
                        bucket.copy(items = bucket.items.filterNot { it.id == item.id })
                    } else bucket
                },
            )
            preferences.edit().remove(itemTitleKey(item.id)).apply()
            persistIds(next)
            next
        }
    }

    /**
     * Picks a random activity from the current rotation bucket, advances the rotation, and
     * returns the item. Falls back to the next non-empty bucket if the current one is empty.
     * Returns null if all buckets are empty.
     *
     * Safe to call from any context including an AccessibilityService.
     */
    fun pickSuggestion(): BacklogItem? {
        val current = _state.value
        val bucketCount = BacklogBucketId.entries.size
        repeat(bucketCount) { offset ->
            val index = (current.currentBucketIndex + offset) % bucketCount
            val bucket = current.buckets[index]
            if (bucket.items.isNotEmpty()) {
                val nextIndex = (index + 1) % bucketCount
                preferences.edit().putInt(KEY_ROTATION_INDEX, nextIndex).apply()
                _state.update { it.copy(currentBucketIndex = nextIndex) }
                return bucket.items.random()
            }
        }
        return null
    }

    private fun loadState(): BacklogState {
        val rotationIndex = preferences.getInt(KEY_ROTATION_INDEX, 0)
            .coerceIn(0, BacklogBucketId.entries.lastIndex)
        val buckets = BacklogBucketId.entries.map { bucketId ->
            val itemIds = preferences.getStringSet(itemIdsKey(bucketId), emptySet()).orEmpty()
            val items = itemIds.mapNotNull { id ->
                val title = preferences.getString(itemTitleKey(id), null) ?: return@mapNotNull null
                BacklogItem(id = id, bucketId = bucketId, title = title)
            }
            BacklogBucket(id = bucketId, items = items)
        }
        return BacklogState(buckets = buckets, currentBucketIndex = rotationIndex)
    }

    private fun persistAll(state: BacklogState) {
        val editor = preferences.edit()
        editor.putInt(KEY_ROTATION_INDEX, state.currentBucketIndex)
        state.buckets.forEach { bucket ->
            editor.putStringSet(itemIdsKey(bucket.id), bucket.items.map { it.id }.toSet())
            bucket.items.forEach { item ->
                editor.putString(itemTitleKey(item.id), item.title)
            }
        }
        editor.apply()
    }

    private fun persistIds(state: BacklogState) {
        val editor = preferences.edit()
        state.buckets.forEach { bucket ->
            editor.putStringSet(itemIdsKey(bucket.id), bucket.items.map { it.id }.toSet())
        }
        editor.apply()
    }

    private fun itemIdsKey(bucketId: BacklogBucketId) = "backlog_ids_${bucketId.name.lowercase()}"
    private fun itemTitleKey(id: String) = "backlog_title_$id"

    private companion object {
        const val PREFERENCES_NAME = "warvis_backlog"
        const val KEY_ROTATION_INDEX = "rotation_index"
    }
}
