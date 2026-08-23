package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.graphics.Color
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineHookScreen(navController: NavController) {
    val context = LocalContext.current
    var funcName by remember { mutableStateOf("my_func") }
    var result by remember { mutableStateOf("") }
    var hookType by remember { mutableStateOf("ldpreload") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🪝 Hook Generator", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(value = funcName, onValueChange = { funcName = it }, label = { Text("Function name") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkTextFieldColors())
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ldpreload" to "LD_PRELOAD", "trampoline" to "Trampoline").forEach { (k, l) ->
                            FilterChip(selected = hookType == k, onClick = { hookType = k }, label = { Text(l, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f)))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                result = when (hookType) {
                    "ldpreload" -> """
// hook_${funcName}.c - LD_PRELOAD wrapper
// Compile: gcc -shared -fPIC -o hook.so hook_${funcName}.c -ldl
#include <stdio.h>
#include <dlfcn.h>

// Original function pointer
typedef int (*orig_${funcName}_t)(/* params */);

int ${funcName}(/* params */) {
    // Get original function
    orig_${funcName}_t orig = (orig_${funcName}_t)dlsym(RTLD_NEXT, "${funcName}");

    printf("[HOOK] ${funcName} called!\n");

    // Call original
    int result = orig(/* forward params */);

    printf("[HOOK] ${funcName} returned: %d\n", result);

    // Modify return value
    return 1; // or result
}
                    """.trimIndent()
                    else -> """
// Trampoline hook for ARM64
// Place at target function address
.global hook_${funcName}
hook_${funcName}:
    // Save registers
    stp x29, x30, [sp, #-16]!
    mov x29, sp

    // Your hook code here
    // e.g., log arguments, modify behavior

    // Restore and return
    ldp x29, x30, [sp], #16
    ret

// Jump to original function (patch target address)
.global original_${funcName}
original_${funcName}:
    // Address of original function + 4 (skip trampoline)
    .quad 0x0000000000000000
                    """.trimIndent()
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(12.dp)) {
                Text("Generate", fontWeight = FontWeight.Bold)
            }
            if (result.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Generated Code", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("code", result))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp), tint = AccentGreen) }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(result, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "Hook script generated" },
                filename = "inline_hook.c",
                subfolder = "hooks"
            )

        }
    }
}
