package com.oprek.tool

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.oprek.tool.ui.screens.*

@Composable
fun AppNavigation(navController: NavHostController, vm: MainViewModel = viewModel()) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController, vm) }
        composable("hex") { HexViewerScreen(navController, vm) }
        composable("strings") { StringExtractorScreen(navController, vm) }
        composable("elf") { ElfAnalyzerScreen(navController, vm) }
        composable("apk") { ApkAnalyzerScreen(navController, vm) }
        composable("patch") { PatchEditorScreen(navController, vm) }
        composable("info") { FileInfoScreen(navController, vm) }
        composable("terminal") { TerminalScreen(navController) }
        composable("deobfuscate") { DeobfuscateScreen(navController) }
        composable("obfuscate") { ObfuscateScreen(navController) }
        composable("disasm") { DisassemblerScreen(navController) }
        composable("memory") { MemoryAnalyzerScreen(navController) }
        composable("android") { AndroidToolsScreen(navController) }
        composable("hash") { HashCalculatorScreen(navController) }
        composable("bookmark") { BookmarkScreen(navController) }
        composable("diff") { DiffToolScreen(navController) }
        composable("advpatch") { AdvancedPatchScreen(navController) }
        composable("keygen") { KeygenScreen(navController) }
        composable("manifest") { ManifestReaderScreen(navController) }
        composable("frida") { FridaHookScreen(navController) }
        composable("export") { ExportScreen(navController) }
        composable("recent") { RecentFilesScreen(navController) }
        composable("base64") { Base64Screen(navController) }
        composable("antidebug") { AntiDebugScreen(navController) }
        composable("logcat") { LogcatScreen(navController) }
        composable("hexcopy") { HexCopyScreen(navController) }
        composable("elfsymbol") { ELFSymbolScreen(navController) }
        composable("inlinehook") { InlineHookScreen(navController) }
        composable("shellscript") { ShellScriptScreen(navController) }
        composable("elfheader") { ElfHeaderScreen(navController) }
        composable("packer") { PackerDetectionScreen(navController) }
        composable("shellpatch") { ShellPatcherScreen(navController) }
        composable("memdump") { MemoryDumpScreen(navController) }
        composable("lua") { LuaAnalyzerScreen(navController) }
        composable("pak") { PakArchiveScreen(navController) }
        // === NEW v4 SCREENS ===
        composable("proghdr") { ProgramHeaderScreen(navController) }
        composable("sechdr") { SectionHeaderScreen(navController) }
        composable("symtable") { SymbolTableScreen(navController) }
        composable("dynamic") { DynamicSectionScreen(navController) }
        composable("reloc") { RelocationScreen(navController) }
        composable("gotplt") { GotPltScreen(navController) }
        composable("shelldeob") { ShellDeobfuscateScreen(navController) }
        composable("advdisasm") { AdvancedDisasmScreen(navController) }
        composable("xref") { XrefScreen(navController) }
        composable("patchinsn") { PatchInstructionScreen(navController) }
        composable("patchbranch") { PatchBranchScreen(navController) }
        composable("autologin") { AutoPatchLoginScreen(navController) }
        composable("patchstring") { PatchStringScreen(navController) }
        composable("xorbrute") { XorBruteForceScreen(navController) }
        composable("patchantidebug") { PatchAntiDebugScreen(navController) }
        composable("unpacker") { UnpackerScreen(navController) }
        composable("funclist") { FunctionListScreen(navController) }
        composable("idaststrings") { IdaStringWindowScreen(navController) }
        composable("entropy") { EntropyAnalyzerScreen(navController) }
        composable("strencrypt") { StringEncryptorScreen(navController) }
        composable("session") { SessionScreen(navController) }
        composable("encrypt") { EncryptToolScreen(navController) }
        composable("decrypt") { DecryptToolScreen(navController) }
        composable("decompiler") { DecompilerScreen(navController) }
        composable("cfg") { CfgScreen(navController) }
        composable("fridalib") { FridaLibraryScreen(navController) }
        composable("manifestpatch") { ManifestPatcherScreen(navController) }
        composable("smali") { SmaliScreen(navController) }
        composable("multidiff") { MultiDiffScreen(navController) }
        composable("scripting") { ScriptingScreen(navController) }
        composable("python") { PythonScreen(navController) }
        composable("toolsdl") { ToolsDownloadScreen(navController) }
        composable("search?query={query}", arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" })) { backStackEntry ->
            SearchScreen(navController, vm, backStackEntry.arguments?.getString("query") ?: "")
        }
    }
}
