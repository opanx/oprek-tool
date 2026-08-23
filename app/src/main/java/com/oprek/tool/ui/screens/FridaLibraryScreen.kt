package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*

data class FridaScript(val name: String, val category: String, val desc: String, val code: String)

private val FRIDA_SCRIPTS = listOf(
    FridaScript("SSL Bypass", "Security", "Bypass SSL certificate pinning",
        """Java.perform(function(){
  var TrustManagerImpl = Java.use("com.android.org.conscrypt.TrustManagerImpl");
  TrustManagerImpl.verifyChain.implementation = function(){ return this.arguments[0]; };
});"""),
    FridaScript("Root Detect Bypass", "Security", "Bypass root detection checks",
        """Java.perform(function(){
  var Runtime = Java.use("java.lang.Runtime");
  Runtime.exec.overload("[Ljava.lang.String;").implementation = function(cmd){
    if(cmd[0] === "su") throw new Error("blocked");
    return this.exec(cmd);
  };
});"""),
    FridaScript("Frida Detect Bypass", "Security", "Hide Frida from detection",
        """Java.perform(function(){
  var File = Java.use("java.io.File");
  File.exists.implementation = function(){
    var name = this.getName();
    if(name.indexOf("frida") !== -1 || name.indexOf("xposed") !== -1) return false;
    return this.exists();
  };
});"""),
    FridaScript("Hook all Methods", "Hooking", "Hook all methods of a class",
        """Java.perform(function(){
  var cls = Java.use("TARGET_CLASS");
  cls.class.getDeclaredMethods().forEach(function(m){
    var name = m.getName();
    cls[name].implementation = function(){
      console.log("[*] " + name + " called");
      return this[name].apply(this, arguments);
    };
  });
});"""),
    FridaScript("Hook Native Functions", "Hooking", "Hook native lib functions withInterceptor",
        """Interceptor.attach(Module.findExportByName("libnative.so", "target_func"), {
  onEnter: function(args){ console.log("arg0=" + args[0]); },
  onLeave: function(retval){ console.log("ret=" + retval); }
});"""),
    FridaScript("Dump SharedPreferences", "Data", "Read all SharedPreferences",
        """Java.perform(function(){
  var ctx = Java.use("android.app.ActivityThread").currentApplication();
  var map = ctx.getSharedPreferences("FILE_NAME", 0).getAll();
  console.log(JSON.stringify(map, null, 2));
});"""),
    FridaScript("Dump KeyStore", "Data", "List all keys in Android KeyStore",
        """Java.perform(function(){
  var ks = Java.use("java.security.KeyStore.getInstance("AndroidKeyStore")");
  ks.load(null);
  var enum = ks.aliases();
  while(enum.hasMoreElements()) console.log(enum.nextElement());
});"""),
    FridaScript("Bypass License Check", "Security", "Make license check always return true",
        """Java.perform(function(){
  var cls = Java.use("TARGET_CLASS");
  cls.isLicensed.implementation = function(){ return true; };
});"""),
    FridaScript("String Decryption", "Decryption", "Hook StringBuilder.toString to decrypt strings",
        """Java.perform(function(){
  var SB = Java.use("java.lang.StringBuilder");
  SB.toString.implementation = function(){
    var result = this.toString();
    if(result.length > 5 && result.length < 500) console.log("[STR] " + result);
    return result;
  };
});"""),
    FridaScript("Network Monitor", "Network", "Log all HTTP requests",
        """Java.perform(function(){
  var URL = Java.use("java.net.URL");
  URL.openConnection.implementation = function(){
    console.log("[HTTP] " + this.toString());
    return this.openConnection();
  };
});"""),
    FridaScript("SharedPreferences Write", "Data", "Write to SharedPreferences",
        """Java.perform(function(){
  var ctx = Java.use("android.app.ActivityThread").currentApplication();
  var editor = ctx.getSharedPreferences("FILE", 0).edit();
  editor.putString("key", "value");
  editor.apply();
});"""),
    FridaScript("Dynamic DEX Loading", "Hooking", "Dump loaded DEX files at runtime",
        """Java.perform(function(){
  var DexFile = Java.use("dalvik.system.DexFile");
  DexFile.loadDex.implementation = function(path){
    console.log("[DEX] " + path);
    return this.loadDex(path);
  };
});"""),
    FridaScript("Bypass Debugger Check", "Security", "Anti-anti-debug bypass",
        """Java.perform(function(){
  var Debug = Java.use("android.os.Debug");
  Debug.isDebuggerConnected.implementation = function(){ return false; };
});"""),
    FridaScript("Clipboard Monitor", "Data", "Monitor clipboard changes",
        """Java.perform(function(){
  var CM = Java.use("android.content.ClipboardManager");
  CM.setPrimaryClip.implementation = function(clip){
    console.log("[CLIPBOARD] " + clip);
    return this.setPrimaryClip(clip);
  };
});"""),
    FridaScript("Hook SQLite", "Data", "Monitor all SQLite queries",
        """Java.perform(function(){
  var DB = Java.use("android.database.sqlite.SQLiteDatabase");
  DB.rawQuery.overload("java.lang.String","[Ljava.lang.String;").implementation = function(sql, args){
    console.log("[SQL] " + sql);
    return this.rawQuery(sql, args);
  };
});"""),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridaLibraryScreen(navController: NavController) {
    val context = LocalContext.current
    var expandedIdx by remember { mutableIntStateOf(-1) }
    var filter by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All") + FRIDA_SCRIPTS.map { it.category }.distinct()
    val filtered = FRIDA_SCRIPTS.filter { (selectedCategory == "All" || it.category == selectedCategory) && (filter.isEmpty() || it.name.contains(filter, true) || it.desc.contains(filter, true)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📜 Frida Script Library", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(value = filter, onValueChange = { filter = it },
                placeholder = { Text("Search scripts...") }, modifier = Modifier.fillMaxWidth().padding(12.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen))

            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { cat ->
                    FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f)))
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered.size) { idx ->
                    val script = filtered[idx]
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${script.category.first()}", fontSize = 16.sp, color = AccentGreen)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(script.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                    Text(script.desc, fontSize = 11.sp, color = TextSecondary)
                                }
                                IconButton(onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText(script.name, script.code))
                                    Toast.makeText(context, "Copied: ${script.name}", Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Default.ContentCopy, "Copy", tint = AccentGreen) }
                            }
                            if (expandedIdx == idx) {
                                Spacer(Modifier.height(8.dp))
                                Text(script.code, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentCyan,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()))
                            }
                            TextButton(onClick = { expandedIdx = if (expandedIdx == idx) -1 else idx }) {
                                Text(if (expandedIdx == idx) "Hide Code ▲" else "Show Code ▼", fontSize = 11.sp, color = AccentPurple)
                            }
                        }
                    }
                }
            }
        }
    }
}
