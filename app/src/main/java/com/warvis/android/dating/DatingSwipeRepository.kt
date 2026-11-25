package com.warvis.android.dating

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

internal data class DatingSwipeSnapshot(
    val day: LocalDate,
    val hingeCount: Int = 0,
    val tinderCount: Int = 0,
    val bumbleCount: Int = 0,
) {
    val totalCount: Int
        get() = hingeCount + tinderCount + bumbleCount

    fun countFor(app: DatingApp): Int =
        when (app) {
            DatingApp.Hinge -> hingeCount
            DatingApp.Tinder -> tinderCount
            DatingApp.Bumble -> bumbleCount
        }

    companion object {
        fun empty(day: LocalDate): DatingSwipeSnapshot = DatingSwipeSnapshot(day)
    }
}

internal interface DatingSwipePersistence {
    fun read(): String?
    fun commit(raw: String): Boolean
}

internal class DatingSwipeRepository(
    private val persistence: DatingSwipePersistence,
    private val localDayProvider: () -> LocalDate = { LocalDate.now() },
) {
    constructor(context: Context) : this(
        SharedPreferencesDatingSwipePersistence(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
    )

    fun readToday(): DatingSwipeSnapshot = synchronized(MONITOR) {
        val today = localDayProvider()
        val state = readState(today)
        if (state.day == today) state.toSnapshot() else DatingSwipeSnapshot.empty(today)
    }

    fun increment(app: DatingApp): Boolean = synchronized(MONITOR) {
        val today = localDayProvider()
        val current = readState(today)
        val base = current.takeIf { it.day == today } ?: DatingSwipeStoredState(today)
        persist(base.increment(app))
    }

    private fun readState(today: LocalDate): DatingSwipeStoredState {
        val raw = try {
            persistence.read()
        } catch (_: RuntimeException) {
            return DatingSwipeStoredState(today)
        } ?: return DatingSwipeStoredState(today)

        return DatingSwipeStateCodec.decode(raw) ?: DatingSwipeStoredState(today).also {
            persist(it)
        }
    }

    private fun persist(state: DatingSwipeStoredState): Boolean =
        try {
            persistence.commit(DatingSwipeStateCodec.encode(state))
        } catch (_: RuntimeException) {
            false
        }

    private class SharedPreferencesDatingSwipePersistence(
        private val preferences: SharedPreferences,
    ) : DatingSwipePersistence {
        override fun read(): String? = preferences.getString(KEY_STATE, null)

        override fun commit(raw: String): Boolean =
            preferences.edit().putString(KEY_STATE, raw).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "dating_swipes"
        const val KEY_STATE = "state"
        val MONITOR = Any()
    }
}

internal data class DatingSwipeStoredState(
    val day: LocalDate,
    val hingeCount: Int = 0,
    val tinderCount: Int = 0,
    val bumbleCount: Int = 0,
) {
    fun increment(app: DatingApp): DatingSwipeStoredState =
        when (app) {
            DatingApp.Hinge -> copy(hingeCount = (hingeCount + 1).coerceAtMost(MAX_DAILY_COUNT))
            DatingApp.Tinder -> copy(tinderCount = (tinderCount + 1).coerceAtMost(MAX_DAILY_COUNT))
            DatingApp.Bumble -> copy(bumbleCount = (bumbleCount + 1).coerceAtMost(MAX_DAILY_COUNT))
        }

    fun toSnapshot(): DatingSwipeSnapshot =
        DatingSwipeSnapshot(
            day = day,
            hingeCount = hingeCount,
            tinderCount = tinderCount,
            bumbleCount = bumbleCount,
        )
}

internal object DatingSwipeStateCodec {
    private const val SCHEMA_VERSION = "1"

    fun encode(state: DatingSwipeStoredState): String =
        listOf(
            SCHEMA_VERSION,
            state.day.toString(),
            state.hingeCount,
            state.tinderCount,
            state.bumbleCount,
        ).joinToString("\t")

    fun decode(raw: String): DatingSwipeStoredState? {
        val parts = raw.split('\t')
        if (parts.size != 5 || parts[0] != SCHEMA_VERSION) return null

        val day = try {
            LocalDate.parse(parts[1])
        } catch (_: RuntimeException) {
            return null
        }
        val parsedCounts = parts.drop(2).map { it.toIntOrNull() }
        if (parsedCounts.any { it == null }) return null
        val counts = parsedCounts.map { requireNotNull(it) }
        if (counts.any { it !in 0..MAX_DAILY_COUNT }) return null

        return DatingSwipeStoredState(
            day = day,
            hingeCount = counts[0],
            tinderCount = counts[1],
            bumbleCount = counts[2],
        )
    }
}

private const val MAX_DAILY_COUNT = 1_000_000
