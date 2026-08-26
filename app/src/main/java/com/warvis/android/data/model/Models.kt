package com.warvis.android.data.model

data class WarvisState(
    val tasks: List<WarvisTask>,
    val knowledgeRings: List<KnowledgeRing>,
    val careerStages: List<CareerStage>,
) {
    val currentStage: CareerStage?
        get() = careerStages.firstOrNull { it.isActive }

    val focusTask: WarvisTask?
        get() = tasks
            .filterNot { it.isCompleted }
            .sortedWith(
                compareBy<WarvisTask> { if (it.category == TaskCategory.COMPLIANCE) 0 else 1 }
                    .thenBy { it.weekNumber }
                    .thenBy { it.priority.sortOrder }
                    .thenBy { it.order },
            )
            .firstOrNull()

    val complianceProgress: ProgressSummary
        get() = progressForTasks(tasks.filter { it.weekNumber == 0 })

    val sprintProgress: ProgressSummary
        get() = progressForTasks(tasks.filter { it.weekNumber > 0 })

    val suggestedFocusRing: KnowledgeRing?
        get() = knowledgeRings.minWithOrNull(compareBy<KnowledgeRing> { it.score }.thenBy { it.ringNumber })

    fun progressForWeek(weekNumber: Int): ProgressSummary =
        progressForTasks(tasks.filter { it.weekNumber == weekNumber })

    private fun progressForTasks(items: List<WarvisTask>): ProgressSummary {
        val completed = items.count { it.isCompleted }
        return ProgressSummary(completed = completed, total = items.size)
    }
}

data class WarvisTask(
    val id: String,
    val weekNumber: Int,
    val weekTitle: String,
    val order: Int,
    val title: String,
    val description: String,
    val category: TaskCategory,
    val priority: TaskPriority,
    val estimatedMinutes: Int?,
    val sourceLabel: String,
    val requiresCompliance: Boolean,
    val isCompleted: Boolean = false,
)

data class KnowledgeRing(
    val id: String,
    val ringNumber: Int,
    val title: String,
    val gapDescription: String,
    val score: Int,
    val lastUpdatedLabel: String,
)

data class CareerStage(
    val id: String,
    val stageNumber: Int,
    val title: String,
    val timeline: String,
    val description: String,
    val isActive: Boolean,
    val isNonSkippable: Boolean = false,
)

data class ProgressSummary(
    val completed: Int,
    val total: Int,
) {
    val fraction: Float
        get() = if (total == 0) 0f else completed.toFloat() / total.toFloat()

    val percent: Int
        get() = (fraction * 100).toInt()

    val label: String
        get() = "$completed/$total"
}

enum class TaskCategory(val label: String) {
    COMPLIANCE("Compliance"),
    LINKEDIN("LinkedIn"),
    EXPERT_NETWORKS("Expert networks"),
    PE_KNOWLEDGE("PE knowledge"),
    TDD_INFRASTRUCTURE("TDD infrastructure"),
    OUTREACH("Outreach"),
    ARTICLE("Article"),
    FINANCIAL_SETUP("Financial setup"),
    REVIEW("Review"),
}

enum class TaskPriority(val sortOrder: Int, val label: String) {
    CRITICAL(0, "Critical"),
    HIGH(1, "High"),
    MEDIUM(2, "Medium"),
    LOW(3, "Low"),
}
