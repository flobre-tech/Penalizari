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
fun TodayScreen(tracked: Set<String>, rules: Rules, labels: Map<String, String>, tick: Int) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val usage by produceState<Map<String, Usage>?>(null, tracked, rules, tick, refresh) {
        value = withContext(Dispatchers.Default) {
            UsageRepo.usageForDay(ctx, UsageRepo.startOfDay(0), UsageRepo.startOfDay(-1), tracked)
        }
    }

    if (tracked.isEmpty()) {
        Hint("Alege din „Setări” aplicațiile pe care vrei să le urmărești.")
        return
    }
    val data = usage ?: run { Hint("Se calculează…"); return }
    val total = data.values.sumOf { it.points(rules) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Puncte de penalizare azi")
                Text("$total", fontSize = 56.sp, fontWeight = FontWeight.Bold,
                    color = if (total == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text("Limită: ${rules.freeMinutes} min/aplicație · 1 punct la ${rules.minutesPerPoint} min în plus",
                    fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(data.entries.sortedByDescending { it.value.totalMinutes }.toList()) { (pkg, u) ->
                val p = u.points(rules)
                ListItem(
                    headlineContent = { Text(labels[pkg] ?: pkg) },
                    supportingContent = {
                        val night = if (u.nightMinutes > 0) " (${u.nightMinutes} noaptea)" else ""
                        Text("${u.totalMinutes} min$night")
                    },
                    trailingContent = {
                        Text(if (p == 0) "0" else "+$p", fontWeight = FontWeight.Bold,
                            color = if (p == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                    },
                )
                HorizontalDivider()
            }
        }
        OutlinedButton(onClick = { refresh++ }, Modifier.fillMaxWidth()) { Text("Actualizează") }
    }
}

