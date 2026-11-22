package ro.penalizari

import android.content.Context

/**
 * Regulile de punctaj. Le poți schimba din ecranul „Setări”.
 *
 * - freeMinutes:     minute gratuite pe zi, pentru FIECARE aplicație urmărită
 * - minutesPerPoint: la câte minute peste limită primești 1 punct de penalizare
 * - nightDouble:     minutele de noapte (23:00–06:00) se numără dublu
 */
data class Rules(
    val freeMinutes: Int = 30,
    val minutesPerPoint: Int = 5,
    val nightDouble: Boolean = true,
)

/** Timpul petrecut într-o aplicație într-o zi. */
class Usage {
    var normalMs = 0L
    var nightMs = 0L

    val totalMinutes: Long get() = (normalMs + nightMs) / 60_000
    val nightMinutes: Long get() = nightMs / 60_000

    /** Minute „ponderate”: noaptea poate conta dublu. */
    fun weightedMinutes(r: Rules): Double =
        (normalMs + nightMs * (if (r.nightDouble) 2 else 1)) / 60_000.0

    /** Punctele de penalizare pentru ziua respectivă. */
    fun points(r: Rules): Int {
        val over = weightedMinutes(r) - r.freeMinutes
        if (over <= 0) return 0
        return (over / r.minutesPerPoint.coerceAtLeast(1)).toInt()
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("setari", Context.MODE_PRIVATE)

    fun tracked(c: Context): Set<String> =
        sp(c).getStringSet("tracked", emptySet())?.toSet() ?: emptySet()

    fun setTracked(c: Context, pkgs: Set<String>) =
        sp(c).edit().putStringSet("tracked", pkgs).apply()

    fun rules(c: Context): Rules = sp(c).let {
        Rules(
            freeMinutes = it.getInt("free", 30),
            minutesPerPoint = it.getInt("perPoint", 5),
            nightDouble = it.getBoolean("night", true),
        )
    }

    fun saveRules(c: Context, r: Rules) = sp(c).edit()
        .putInt("free", r.freeMinutes)
        .putInt("perPoint", r.minutesPerPoint)
        .putBoolean("night", r.nightDouble)
        .apply()
}
