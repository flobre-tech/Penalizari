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

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { App(tick) }
            }
        }
    }

    // La fiecare revenire în aplicație recalculăm (și verificăm permisiunea).
    override fun onResume() {
        super.onResume()
        tick++
    }
}

@Composable
fun App(tick: Int) {
    val ctx = LocalContext.current
    val hasPerm = remember(tick) { UsageRepo.hasPermission(ctx) }
    var tab by remember { mutableIntStateOf(0) }
    var tracked by remember { mutableStateOf(Prefs.tracked(ctx)) }
    var rules by remember { mutableStateOf(Prefs.rules(ctx)) }
    val apps = remember { UsageRepo.launchableApps(ctx) }
    val labels = remember(apps) { apps.associate { it.pkg to it.label } }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        if (!hasPerm) {
            PermissionCard()
        } else {
            TabRow(selectedTabIndex = tab) {
                listOf("Azi", "Săptămâna", "Setări").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> TodayScreen(tracked, rules, labels, tick)
                1 -> WeekScreen(tracked, rules, tick)
                else -> SettingsScreen(
                    apps, tracked, rules,
                    onTracked = { tracked = it; Prefs.setTracked(ctx, it) },
                    onRules = { rules = it; Prefs.saveRules(ctx, it) },
                )
            }
        }
    }
}

@Composable
fun PermissionCard() {
    val ctx = LocalContext.current
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Penalizări", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Ca să măsoare timpul petrecut în aplicații, trebuie să-i dai permisiunea " +
                "„Acces la utilizare”. Caută „Penalizări” în listă și activează-o, apoi revino aici."
        )
        Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
            Text("Deschide setările")
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, step: Int, min: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) { Text("−") }
        Text("$value", Modifier.width(48.dp), fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = { onChange(value + step) }) { Text("+") }
    }
}

@Composable
fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(text) }
}
