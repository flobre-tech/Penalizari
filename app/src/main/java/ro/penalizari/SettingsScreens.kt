package ro.penalizari

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeekScreen(tracked: Set<String>, rules: Rules, tick: Int) {
    val ctx = LocalContext.current
    val fmt = remember { SimpleDateFormat("EEEE, dd.MM", Locale("ro")) }
    val days by produceState<List<Pair<Long, Int>>?>(null, tracked, rules, tick) {
        value = withContext(Dispatchers.Default) {
            (0..6).map { d ->
                val start = UsageRepo.startOfDay(d)
                val pts = UsageRepo.usageForDay(ctx, start, UsageRepo.startOfDay(d - 1), tracked)
                    .values.sumOf { it.points(rules) }
                start to pts
            }
        }
    }
    if (tracked.isEmpty()) { Hint("Alege din „Setări” aplicațiile urmărite."); return }
    val list = days ?: run { Hint("Se calculează…"); return }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Total ultimele 7 zile: ${list.sumOf { it.second }} puncte",
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        list.forEach { (day, pts) ->
            ListItem(
                headlineContent = { Text(fmt.format(Date(day)).replaceFirstChar { it.uppercase() }) },
                trailingContent = { Text("$pts", fontWeight = FontWeight.Bold) },
            )
            HorizontalDivider()
        }
        Spacer(Modifier.height(8.dp))
        Text("Notă: Android păstrează istoricul detaliat doar câteva zile, deci zilele mai vechi pot fi incomplete.",
            fontSize = 12.sp)
    }
}

@Composable
fun SettingsScreen(
    apps: List<AppInfo>,
    tracked: Set<String>,
    rules: Rules,
    onTracked: (Set<String>) -> Unit,
    onRules: (Rules) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("Reguli", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
            Stepper("Minute gratuite pe zi (per aplicație)", rules.freeMinutes, step = 5, min = 0) {
                onRules(rules.copy(freeMinutes = it))
            }
            Stepper("Minute pentru 1 punct de penalizare", rules.minutesPerPoint, step = 1, min = 1) {
                onRules(rules.copy(minutesPerPoint = it))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Noaptea (23–06) contează dublu", Modifier.weight(1f))
                Switch(checked = rules.nightDouble, onCheckedChange = { onRules(rules.copy(nightDouble = it)) })
            }
            HorizontalDivider()
            Text("Aplicații urmărite (${tracked.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        }
        items(apps, key = { it.pkg }) { app ->
            val on = app.pkg in tracked
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onTracked(if (on) tracked - app.pkg else tracked + app.pkg) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = on, onCheckedChange = { onTracked(if (it) tracked + app.pkg else tracked - app.pkg) })
                Text(app.label)
            }
        }
    }
}

