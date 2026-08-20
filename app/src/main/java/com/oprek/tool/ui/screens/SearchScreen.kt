package com.oprek.tool.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.MainViewModel
import com.oprek.tool.ui.theme.*
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController, vm: MainViewModel, initialQuery: String = "") {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<Long>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Search input
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Search Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Hex: 7F 45 4C 46\nText: hello world") },
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (query.isBlank()) return@Button
                            searching = true
                            results = emptyList()
                            // Simple search - in production this would use the VM
                            searching = false
                            statusMsg = "Use Hex Viewer search for binary patterns"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Search")
                    }
                }
            }

            if (statusMsg.isNotEmpty()) {
                Text(statusMsg, fontSize = 12.sp, color = AccentOrange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            // Tips
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("💡 Quick Tips", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))

                    Tip("ELF header: 7F 45 4C 46")
                    Tip("APK/ZIP: 50 4B 03 04")
                    Tip("DEX: CA FE BA BE")
                    Tip("Shell script: 23 21")
                    Tip("Use Hex Viewer for byte-level search + patch")
                }
            }
        }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "Search complete" },
                filename = "search_results.txt",
                subfolder = "search"
            )

    }
}

@Composable
fun Tip(text: String) {
    Text("• $text", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextSecondary,
        modifier = Modifier.padding(vertical = 2.dp))
}
