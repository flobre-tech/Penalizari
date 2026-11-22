package ro.penalizari

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Raportul trimis părintelui prin ntfy.sh (aplicația gratuită „ntfy”).
 * Se trimite când apar puncte noi și o dată seara, după ora 21.
 */
object Report {
    private const val WORK = "raport-parinte"
    private fun sp(c: Context) = c.getSharedPreferences("raport", Context.MODE_PRIVATE)

    /** Codul unic pe care părintele îl adaugă în aplicația ntfy. */
    fun topic(c: Context): String {
        sp(c).getString("topic", null)?.let { return it }
        val t = "penalizari-" + UUID.randomUUID().toString().replace("-", "").take(12)
        sp(c).edit().putString("topic", t).apply()
        return t
    }

    fun enabled(c: Context) = sp(c).getBoolean("on", false)

    fun setEnabled(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("on", on).apply()
        if (on) schedule(c) else WorkManager.getInstance(c).cancelUniqueWork(WORK)
    }

    fun schedule(c: Context) {
        val req = PeriodicWorkRequestBuilder<ReportWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Totalul de puncte de azi și textul raportului. */
    fun today(c: Context): Pair<Int, String> {
        val rules = Prefs.rules(c)
        val usage = UsageRepo.usageForDay(c, UsageRepo.startOfDay(0), UsageRepo.startOfDay(-1), Prefs.tracked(c))
        val labels = UsageRepo.launchableApps(c).associate { it.pkg to it.label }
        val total = usage.values.sumOf { it.points(rules) }
        val lines = usage.entries
            .filter { it.value.totalMinutes > 0 }
            .sortedByDescending { it.value.totalMinutes }
            .map { (pkg, u) -> "${labels[pkg] ?: pkg}: ${u.totalMinutes} min (+${u.points(rules)})" }
        val text = "Azi: $total puncte\n" + lines.joinToString("\n").ifEmpty { "Nicio aplicație urmărită folosită azi." }
        return total to text
    }

    /** Trimite un mesaj. Rulează doar pe un fir de fundal. */
    fun send(c: Context, title: String, text: String): Boolean {
        val conn = URL("https://ntfy.sh/" + topic(c)).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Title", title) // doar litere fără diacritice
            conn.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }

    /** Apelat din oră în oră: trimite dacă au apărut puncte noi sau e seara. */
    fun checkAndSend(c: Context) {
        if (!enabled(c) || !UsageRepo.hasPermission(c)) return
        val (points, text) = today(c)
        val day = UsageRepo.startOfDay(0)
        val p = sp(c)
        val lastPoints = if (p.getLong("day", 0) == day) p.getInt("points", 0) else 0
        val evening = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 21 && p.getLong("eveningDay", 0) != day

        when {
            points > lastPoints -> if (send(c, "Penalizari: $points puncte azi", text)) {
                p.edit().putLong("day", day).putInt("points", points).apply()
            }
            evening -> if (send(c, "Penalizari - raport de seara", text)) {
                p.edit().putLong("eveningDay", day).apply()
            }
        }
    }
}

class ReportWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result = try {
        Report.checkAndSend(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
