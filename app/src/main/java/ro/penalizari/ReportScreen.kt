package ro.penalizari

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Secțiunea „Raport către părinte” din Setări. */
@Composable
fun ParentReportSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(Report.enabled(ctx)) }
    var status by remember { mutableStateOf("") }
    val topic = remember { Report.topic(ctx) }

    LaunchedEffect(on) { if (on) Report.schedule(ctx) }

    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Raport către părinte", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Trimite raportul", Modifier.weight(1f))
            Switch(checked = on, onCheckedChange = { on = it; Report.setEnabled(ctx, it) })
        }
        Text("Cod:", fontSize = 13.sp)
        SelectionContainer { Text(topic, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        Text(
            "Pe telefonul părintelui: instalează aplicația „ntfy”, apasă + și scrie codul de mai sus. " +
                "Raportul vine când apar puncte noi și seara după ora 21.",
            fontSize = 13.sp,
        )
        OutlinedButton(onClick = {
            status = "Se trimite…"
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { Report.send(ctx, "Penalizari - test", Report.today(ctx).second) }.getOrDefault(false)
                }
                status = if (ok) "Trimis! Verifică telefonul părintelui." else "Nu s-a putut trimite. Verifică internetul."
            }
        }) { Text("Trimite un test acum") }
        if (status.isNotEmpty()) Text(status, fontSize = 13.sp)
        HorizontalDivider()
    }
}
