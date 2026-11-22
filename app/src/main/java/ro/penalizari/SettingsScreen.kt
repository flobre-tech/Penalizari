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
fun SettingsScreen(
    apps: List<AppInfo>,
    tracked: Set<String>,
    rules: Rules,
    onTracked: (Set<String>) -> Unit,
    onRules: (Rules) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Aplicațiile bifate apar primele; căutarea ignoră literele mari/mici.
    val shown = remember(apps, tracked, query) {
        apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            .sortedByDescending { it.pkg in tracked }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { ParentReportSection() }
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Caută aplicația (ex: tik, you, insta)") },
                singleLine = true,
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("✕") } },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            if (shown.isEmpty()) Text("Nicio aplicație găsită.", Modifier.padding(8.dp))
        }
        items(shown, key = { it.pkg }) { app ->
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

