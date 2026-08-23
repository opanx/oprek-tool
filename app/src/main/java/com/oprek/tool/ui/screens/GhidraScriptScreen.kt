package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhidraScriptScreen(navController: NavController) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedScript by remember { mutableStateOf("") }
    var scriptOutput by remember { mutableStateOf(listOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🐍 Script Generator", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (scriptOutput.isNotEmpty()) {
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("script", scriptOutput.joinToString("\n")))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(20.dp)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = DarkSurface) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Ghidra", fontSize = 11.sp) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("IDA Pro", fontSize = 11.sp) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("radare2", fontSize = 11.sp) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Frida", fontSize = 11.sp) })
            }
            Spacer(Modifier.height(8.dp))

            val scripts = when (selectedTab) { 0 -> ghidraScripts; 1 -> idaScripts; 2 -> r2Scripts; 3 -> fridaScripts; else -> ghidraScripts }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    scripts.forEach { (name, pair) ->
                        Card(
                            onClick = { selectedScript = name; scriptOutput = pair.second },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selectedScript == name) AccentCyan.copy(alpha = 0.15f) else DarkCard),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextPrimary)
                                    Text(pair.first, fontSize = 9.sp, color = Color.Gray)
                                }
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = Color.Gray)
                            }
                        }
                    }
                }
            }

            if (scriptOutput.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("📋 $selectedScript", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        LazyColumn {
                            items(scriptOutput) { line ->
                                val color = when { line.startsWith("#") || line.startsWith("//") -> Color.Gray; line.contains("def ") || line.contains("func ") -> AccentGreen; line.contains("print") || line.contains("log") -> AccentCyan; else -> TextPrimary }
                                Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color, lineHeight = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ghidraScripts = linkedMapOf(
    "Find Functions" to ("List all functions" to listOf("# Ghidra: List Functions", "from ghidra.program.model.listing import *", "fm = getCurrentProgram().getFunctionManager()", "for f in fm.getFunctions(True):", "    print(\"0x{} {} size={}\".format(f.getEntryPoint(), f.getName(), f.getBody().getNumAddresses()))")),
    "String XREF" to ("Find string references" to listOf("# Ghidra: String XREF", "target = askString(\"Search\", \"String:\")", "if target:", "    for ref in getReferencesTo(getSymbolTable().getSymbols(target)):", "        print(\"0x{} -> {}\".format(ref.getFromAddress(), target))")),
    "Patch NOP" to ("NOP instruction" to listOf("# Ghidra: Patch NOP", "addr = getCurrentLocation().getAddress()", "inst = getCurrentProgram().getListing().getInstructionAt(addr)", "if inst:", "    getCurrentProgram().getMemory().setBytes(addr, [0x1F,0x20,0x03,0xD5][:inst.getLength()])")),
    "Export CSV" to ("Export functions CSV" to listOf("# Ghidra: Export CSV", "import csv", "f = open(askFile(\"Save\",\".csv\").getAbsolutePath(),'w')", "w = csv.writer(f); w.writerow([\"Addr\",\"Name\",\"Size\"])", "for func in getCurrentProgram().getFunctionManager().getFunctions(True):", "    w.writerow([str(func.getEntryPoint()), func.getName(), func.getBody().getNumAddresses()])", "f.close()")),
    "Detect Packer" to ("Find packers in binary" to listOf("# Ghidra: Detect Packer", "packers = [\"UPX\",\"Themida\",\"VMProtect\",\"ASPack\",\"MPRESS\"]", "for p in packers:", "    addr = getCurrentProgram().getMemory().findBytes(getCurrentProgram().getMemory().getMinAddress(), getCurrentProgram().getMemory().getMaxAddress(), p, None, True, None)", "    if addr: print(\"[!] {} detected at 0x{}\".format(p, addr))"))
)

private val idaScripts = linkedMapOf(
    "Find Functions" to ("List all functions" to listOf("# IDA: List Functions", "import idaapi, idc", "ea = idc.get_next_func_addr(0)", "while ea != idc.BADADDR:", "    print(\"0x{:X} {} size={}\".format(ea, idc.get_func_name(ea), idc.get_func_attr(ea, idc.FUNCATTR_SIZE)))", "    ea = idc.get_next_func_addr(ea)")),
    "String XREF" to ("Find string references" to listOf("# IDA: String XREF", "import idautils, idc", "target = idaapi.ask_str(\"String:\", 0, \"Search\")", "if target:", "    for x in idautils.XrefsTo(idc.get_name_ea_simple(target)):", "        print(\"0x{:X} -> {}\".format(x.frm, target))")),
    "Patch NOP" to ("NOP at cursor" to listOf("# IDA: NOP Patch", "import idaapi, idc", "ea = idc.here()", "idaapi.patch_bytes(ea, b'\\x00' * idc.get_item_size(ea))", "print(\"Patched 0x{:X}\".format(ea))")),
    "Export Functions" to ("Export to CSV" to listOf("# IDA: Export Functions", "import idautils, idc, csv", "with open('functions.csv','w',newline='') as f:", "    w = csv.writer(f); w.writerow(['Addr','Name','Size'])", "    for func in idautils.Functions():", "        w.writerow(['0x{:X}'.format(func), idc.get_func_name(func), idc.get_func_attr(func, idc.FUNCATTR_SIZE)])")),
    "Anti-Debug Bypass" to ("Bypass common anti-debug" to listOf("# IDA: Anti-Debug Bypass", "import idaapi, idc", "# Patch ptrace", "ea = idc.get_name_ea_simple('ptrace')", "if ea != idc.BADADDR:", "    print(\"ptrace found at 0x{:X}\".format(ea))", "# Patch IsDebuggerPresent", "ea2 = idc.get_name_ea_simple('IsDebuggerPresent')", "if ea2 != idc.BADADDR:", "    print(\"IsDebuggerPresent at 0x{:X}\".format(ea2))"))
)

private val r2Scripts = linkedMapOf(
    "Full Analysis" to ("Complete r2 analysis" to listOf("#!/usr/bin/radare2", "aaa", "afl", "axt", "iz", "iS", "ie", "pd 50 @ entry0")),
    "Find Strings" to ("Search strings" to listOf("#!/usr/bin/radare2", "izz~http", "izz~password", "izz~key", "izz~token", "izz~api")),
    "Patch Binary" to ("Common patches" to listOf("#!/usr/bin/radare2", "wa nop", "wx 00", "wao xor", "wao rand")),
    "Export Functions" to ("Export function list" to listOf("#!/usr/bin/radare2", "aaa", "aflj > functions.json", "# Or: afl > functions.txt"))
)

private val fridaScripts = linkedMapOf(
    "Hook Java Method" to ("Intercept any Java method" to listOf("Java.perform(function(){", "    var cls = Java.use('com.target.Class');", "    cls.methodName.implementation = function(){", "        console.log('[*] Called');", "        return this.methodName();", "    };", "});")),
    "Hook Native" to ("Hook JNI function" to listOf("Interceptor.attach(Module.findExportByName(null, 'target_func'), {", "    onEnter: function(args){ console.log('[*] arg0=' + args[0]); },", "    onLeave: function(retval){ console.log('[*] ret=' + retval); }", "});")),
    "Bypass Root Check" to ("Hide root from app" to listOf("Java.perform(function(){", "    var File = Java.use('java.io.File');", "    File.exists.implementation = function(){", "        if(this.path.toString().indexOf('su')>-1) return false;", "        return this.exists();", "    };", "});")),
    "Dump SSL Keys" to ("Extract SSL keys" to listOf("Java.perform(function(){", "    var SSLContext = Java.use('javax.net.ssl.SSLContext');", "    SSLContext.init.overload('[Ljavax.net.ssl.KeyManager;','[Ljavax.net.ssl.TrustManager;','java.security.SecureRandom').implementation = function(k,t,s){", "        console.log('[*] SSL keys intercepted');", "        this.init(k,t,s);", "    };", "});")),
    "Dump Classes" to ("List loaded classes" to listOf("Java.perform(function(){", "    Java.enumerateLoadedClasses({", "        onMatch: function(name){ console.log(name); },", "        onComplete: function(){}", "    });", "});")),
    "Dump Fields" to ("List all fields in a class" to listOf("Java.perform(function(){", "    var cls = Java.use('com.target.Class');", "    var fields = cls.class.getDeclaredFields();", "    for(var i=0; i<fields.length; i++){", "        console.log('[Field] ' + fields[i].getName() + ' : ' + fields[i].getType());", "    }", "});")),
    "Hook Constructors" to ("Intercept class constructors" to listOf("Java.perform(function(){", "    var cls = Java.use('com.target.Class');", "    cls.\$init.implementation = function(){", "        console.log('[*] Constructor called with args: ' + JSON.stringify(Array.from(arguments)));", "        this.\$init.apply(this, arguments);", "    };", "});")),
    "Enum Methods" to ("List all methods in a class" to listOf("Java.perform(function(){", "    var cls = Java.use('com.target.Class');", "    var methods = cls.class.getDeclaredMethods();", "    for(var i=0; i<methods.length; i++){", "        console.log('[Method] ' + methods[i].toString());", "    }", "});")),
    "Bypass SSL Pinning" to ("Generic SSL bypass" to listOf("Java.perform(function(){", "    var TrustManager = Java.registerClass({", "        name: 'com.bypass.TrustManager',", "        implements: [Java.use('javax.net.ssl.X509TrustManager')],", "        methods: {", "            checkClientTrusted: function(c, a) {},", "            checkServerTrusted: function(c, a) {},", "            getAcceptedIssuers: function() { return []; }", "        }", "    });", "    var SSLContext = Java.use('javax.net.ssl.SSLContext');", "    SSLContext.init.overload('[Ljavax.net.ssl.KeyManager;','[Ljavax.net.ssl.TrustManager;','java.security.SecureRandom').implementation = function(k,t,s){", "        this.init(k, [TrustManager.\$new()], s);", "    };", "    console.log('[+] SSL bypass ready');", "});")),
    "Hook Return Values" to ("Modify method return values" to listOf("Java.perform(function(){", "    var cls = Java.use('com.target.Class');", "    cls.methodName.implementation = function(){", "        var result = this.methodName();", "        console.log('[*] Original: ' + result);", "        return 999999; // modified return", "    };", "});")),
    "Anti-Detection" to ("Hide Frida from app" to listOf("Java.perform(function(){", "    // Hide Frida server", "    var Thread = Java.use('java.lang.Thread');", "    Thread.activeCount.implementation = function(){ return 1; };", "    // Hide from /proc", "    var Runtime = Java.use('java.lang.Runtime');", "    Runtime.exec.overload('java.lang.String').implementation = function(cmd){", "        if(cmd.indexOf('frida')>-1 || cmd.indexOf('ls /proc')>-1) return null;", "        return this.exec(cmd);", "    };", "    console.log('[+] Anti-detection active');", "});")),
    "Memory Read/Write" to ("Read/write process memory" to listOf("Java.perform(function(){", "    var base = Module.findBaseAddress('libtarget.so');", "    console.log('[*] Base: ' + base);", "    // Read 16 bytes", "    var data = Memory.readByteArray(base, 16);", "    console.log('[*] Data: ' + hexdump(data));", "    // Write NOP", "    Memory.protect(base, 0x1000, 'rwx');", "    Memory.writeU8(base, 0x1f); // NOP", "    Memory.writeU8(base.add(1), 0xef);", "});"))

)
