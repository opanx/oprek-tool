package com.oprek.tool.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.engine.SessionManager
import com.oprek.tool.engine.SessionState
import com.oprek.tool.engine.BookmarkEntry
import com.oprek.tool.engine.SavedPatch
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(SessionState()) }
    var saved by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { session = SessionManager.loadSession(context) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Session Manager", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current Session", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("File: ${session.fileName.ifEmpty { "None" }}", color = TextSecondary, fontSize = 12.sp)
                    Text("Path: ${session.filePath.ifEmpty { "N/A" }}", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("Bookmarks: ${session.bookmarks.size}", color = TextSecondary, fontSize = 12.sp)
                    Text("Patches: ${session.patches.size}", color = TextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        SessionManager.saveSession(context, session)
                        withContext(Dispatchers.Main) { saved = true; status = "Session saved!" }
                    }
                }, Modifier.weight(1f).padding(end = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Save") }
                Button(onClick = {
                    session = SessionManager.loadSession(context)
                    status = "Session loaded!"
                }, Modifier.weight(1f).padding(horizontal = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) { Text("Load") }
                Button(onClick = {
                    SessionManager.clearSession(context)
                    session = SessionState(); status = "Session cleared!"
                }, Modifier.weight(1f).padding(start = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) { Text("Clear") }
            }
            if (status.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(status, color = AccentGreen, fontSize = 12.sp) }
            Spacer(Modifier.height(12.dp))
            // Notes
            OutlinedTextField(value = session.notes, onValueChange = { session = session.copy(notes = it) },
                label = { Text("Notes") }, modifier = Modifier.fillMaxWidth().weight(1f),
                colors = darkTextFieldColors())
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "Session info" },
                filename = "session.txt",
                subfolder = "session"
            )

        }
    }
}
