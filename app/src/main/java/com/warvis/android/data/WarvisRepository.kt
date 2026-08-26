package com.warvis.android.data

import android.content.Context
import android.content.SharedPreferences
import com.warvis.android.data.model.WarvisState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class WarvisRepository(
    context: Context,
    initialState: WarvisState = SeedData.initialState(),
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadState(initialState))
    val state: StateFlow<WarvisState> = _state.asStateFlow()

    fun toggleTask(taskId: String) {
        _state.update { current ->
            val next = current.copy(
                tasks = current.tasks.map { task ->
                    if (task.id == taskId) task.copy(isCompleted = !task.isCompleted) else task
                },
            )
            persist(next)
            next
        }
    }

    fun updateKnowledgeScore(ringId: String, score: Int) {
        val boundedScore = score.coerceIn(1, 5)
        val label = LocalDate.now().toString()
        _state.update { current ->
            val next = current.copy(
                knowledgeRings = current.knowledgeRings.map { ring ->
                    if (ring.id == ringId) {
                        ring.copy(score = boundedScore, lastUpdatedLabel = label)
                    } else {
                        ring
                    }
                },
            )
            persist(next)
            next
        }
    }

    fun advanceStage() {
        _state.update { current ->
            val activeIndex = current.careerStages.indexOfFirst { it.isActive }
            if (activeIndex == -1 || activeIndex == current.careerStages.lastIndex) {
                current
            } else {
                val next = current.copy(
                    careerStages = current.careerStages.mapIndexed { index, stage ->
                        stage.copy(isActive = index == activeIndex + 1)
                    },
                )
                persist(next)
                next
            }
        }
    }

    private fun loadState(seed: WarvisState): WarvisState {
        val completedTaskIds = preferences.getStringSet(KEY_COMPLETED_TASK_IDS, emptySet()).orEmpty()
        val activeStageId = preferences.getString(KEY_ACTIVE_STAGE_ID, seed.currentStage?.id)

        return seed.copy(
            tasks = seed.tasks.map { task ->
                task.copy(isCompleted = completedTaskIds.contains(task.id))
            },
            knowledgeRings = seed.knowledgeRings.map { ring ->
                ring.copy(
                    score = preferences.getInt(scoreKey(ring.id), ring.score),
                    lastUpdatedLabel = preferences.getString(updatedKey(ring.id), ring.lastUpdatedLabel)
                        ?: ring.lastUpdatedLabel,
                )
            },
            careerStages = seed.careerStages.map { stage ->
                stage.copy(isActive = stage.id == activeStageId)
            },
        )
    }

    private fun persist(state: WarvisState) {
        val completedTaskIds = state.tasks
            .filter { it.isCompleted }
            .map { it.id }
            .toSet()

        preferences.edit()
            .putStringSet(KEY_COMPLETED_TASK_IDS, completedTaskIds)
            .putString(KEY_ACTIVE_STAGE_ID, state.currentStage?.id)
            .also { editor ->
                state.knowledgeRings.forEach { ring ->
                    editor.putInt(scoreKey(ring.id), ring.score)
                    editor.putString(updatedKey(ring.id), ring.lastUpdatedLabel)
                }
            }
            .apply()
    }

    private fun scoreKey(ringId: String): String = "knowledge_score_$ringId"

    private fun updatedKey(ringId: String): String = "knowledge_updated_$ringId"

    private companion object {
        const val PREFERENCES_NAME = "warvis_state"
        const val KEY_COMPLETED_TASK_IDS = "completed_task_ids"
        const val KEY_ACTIVE_STAGE_ID = "active_stage_id"
    }
}
