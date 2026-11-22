package ro.penalizari

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.util.Calendar

data class AppInfo(val pkg: String, val label: String)

object UsageRepo {
    private const val HOUR = 3_600_000L

    // Tipuri de evenimente Android (valori fixe din UsageEvents.Event)
    private const val RESUMED = 1            // aplicația a venit în prim-plan
    private const val PAUSED = 2             // aplicația a ieșit din prim-plan
    private const val SCREEN_OFF = 16        // ecranul s-a stins

    fun hasPermission(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Miezul nopții de acum [daysAgo] zile (0 = azi, -1 = mâine). */
    fun startOfDay(daysAgo: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    /** Timpul petrecut în fiecare aplicație din [packages] în ziua [dayStart, dayEnd). */
    fun usageForDay(c: Context, dayStart: Long, dayEnd: Long, packages: Set<String>): Map<String, Usage> {
        val result = packages.associateWith { Usage() }
        val end = minOf(dayEnd, System.currentTimeMillis())
        if (packages.isEmpty() || end <= dayStart) return result

        val usm = c.getSystemService(UsageStatsManager::class.java)
        // Citim puțin și dinainte de miezul nopții, ca să prindem aplicațiile deja deschise.
        val events = usm.queryEvents(dayStart - 3 * HOUR, end)
        val e = UsageEvents.Event()
        val activeScreens = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()

        fun close(pkg: String, t: Long) {
            since.remove(pkg)?.let { add(result.getValue(pkg), it, t, dayStart) }
            activeScreens[pkg]?.clear()
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == SCREEN_OFF) {
                since.keys.toList().forEach { close(it, e.timeStamp) }
                continue
            }
            val pkg = e.packageName ?: continue
            if (pkg !in packages) continue
            val screen = e.className ?: ""
            when (e.eventType) {
                RESUMED -> {
                    val set = activeScreens.getOrPut(pkg) { mutableSetOf() }
                    if (set.isEmpty()) since[pkg] = e.timeStamp
                    set.add(screen)
                }
                PAUSED -> {
                    val set = activeScreens[pkg] ?: continue
                    if (set.remove(screen) && set.isEmpty()) close(pkg, e.timeStamp)
                }
            }
        }
        // Ce e încă deschis se numără până la final (sau până acum).
        since.keys.toList().forEach { close(it, end) }
        return result
    }

    /** Adaugă intervalul [s, t), separând partea de noapte (00–06 și 23–24). */
    private fun add(u: Usage, s0: Long, t: Long, dayStart: Long) {
        val s = maxOf(s0, dayStart)
        if (t <= s) return
        val night = overlap(s, t, dayStart, dayStart + 6 * HOUR) +
                overlap(s, t, dayStart + 23 * HOUR, dayStart + 24 * HOUR)
        u.nightMs += night
        u.normalMs += (t - s) - night
    }

    private fun overlap(a: Long, b: Long, c: Long, d: Long) = maxOf(0L, minOf(b, d) - maxOf(a, c))

    /** Aplicațiile care apar în meniul telefonului. */
    fun launchableApps(c: Context): List<AppInfo> {
        val pm = c.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != c.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
}
