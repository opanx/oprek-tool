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
fun FridaHookScreen(navController: NavController) {
    val context = LocalContext.current
    var libName by remember { mutableStateOf("libil2cpp.so") }
    var funcName by remember { mutableStateOf("") }
    var hookType by remember { mutableStateOf("interceptor") }
    var result by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🪝 Frida Hook Generator", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Config", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = libName, onValueChange = { libName = it }, label = { Text("Library name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = darkTextFieldColors())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = funcName, onValueChange = { funcName = it }, label = { Text("Function name / offset (hex)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = darkTextFieldColors())
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("interceptor" to "Interceptor", "nativeReplace" to "NativeReplace", "nativePrint" to "NativePrint").forEach { (key, label) ->
                            FilterChip(selected = hookType == key, onClick = { hookType = key }, label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f)))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val script = when (hookType) {
                    "interceptor" -> """
// Auto-generated Frida Interceptor hook
// Target: $libName -> $funcName
Java.perform(function() {
    var module = Module.findBaseAddress('$libName');
    if (!module) { console.log('Module not found'); return; }
    var offset = module.add(ptr('$funcName'));
    Interceptor.attach(offset, {
        onEnter: function(args) {
            console.log('[$funcName] called');
            console.log('  arg0: ' + args[0]);
            console.log('  arg1: ' + args[1]);
            console.log('  arg2: ' + args[2]);
        },
        onLeave: function(retval) {
            console.log('[$funcName] return: ' + retval);
            retval.replace(ptr(0x1)); // patch return value
        }
    });
    console.log('Hook installed at ' + offset);
});
                    """.trimIndent()
                    "nativeReplace" -> """
// Auto-generated NativeFunction replace
Java.perform(function() {
    var module = Module.findBaseAddress('$libName');
    var offset = module.add(ptr('$funcName'));
    var newFunc = new NativeFunction(offset, 'int', ['pointer', 'pointer', 'pointer']);
    Interceptor.replace(offset, new NativeCallback(function(arg0, arg1, arg2) {
        console.log('Replaced call: ' + arg0 + ', ' + arg1);
        return 1; // force return value
    }, 'int', ['pointer', 'pointer', 'pointer']));
});
                    """.trimIndent()
                    else -> """
// Auto-generated NativeFunction print
Java.perform(function() {
    var module = Module.findBaseAddress('$libName');
    var offset = module.add(ptr('$funcName'));
    var fn = new NativeFunction(offset, 'int', ['pointer']);
    console.log('Result: ' + fn(ptr(0)));
});
                    """.trimIndent()
                }
                result = script
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(12.dp)) {
                Text("Generate Script", fontWeight = FontWeight.Bold)
            }
            if (result.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Frida Script", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("frida", result))
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
                content = { result },
                filename = "frida_hook.js",
                subfolder = "hooks"
            )

        }
    }
}
