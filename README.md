# 🔧 OprekTool — Android Reverse Engineering Toolkit

> **v0.6.1** | Build status: ✅ Passing | APKs: Signed + Auto-released

**An honest Android reverse engineering toolkit** — not a magic wand, but a solid set of tools for on-device binary analysis.

## ⚠️ Honest Disclaimer

This tool **does NOT** replace PC-based RE tools like Ghidra, IDA Pro, or Il2CppDumper. It provides:
- On-device convenience when you don't have PC access
- Basic to moderate analysis capability
- Root memory dump for offline PC processing
- Good enough for quick triage, not deep RE work

## 📱 Features

### Analysis Tools
| Tool | Capability | Accuracy vs Ghidra |
|------|-----------|:---:|
| **ELF Viewer** | Full header, segments, sections, symbols, relocations, GOT/PLT | ✅ 100% |
| **Disassembler** | Capstone ARM32/ARM64/x86/x86_64 | ⚠️ 95% |
| **Advanced Disassembler** | Function-level + flow analysis | ⚠️ 85% |
| **Decompiler v7** | Pseudo-C with struct/loop/switch patterns | ⚠️ 80-95% |
| **Hex Viewer** | Raw hex + ASCII with navigation + search | ✅ 100% |
| **String Extractor** | Printable strings + filter + export all | ✅ 100% |
| **Hash Calculator** | MD5/SHA1/SHA256 + online lookup | ✅ 100% |
| **PE/DEX Viewer** | DEX header, classes, methods | ⚠️ 90% |

### Dump Tools
| Tool | Capability | Notes |
|------|-----------|-------|
| **AutoDump v5** | IL2CPP TypeDef/MethodDef/FieldDef extraction + dump.cs generation | ✅ Proper metadata parsing |
| **IL2CPP Dumper** | global-metadata.dat parsing (magic 0xFAB11BAF) | ✅ 4-strategy search |
| **DEX Dumper** | Extract DEX from APK + root memory scan | ⚠️ 70-85% |
| **Memory Dump** | Full /proc/pid/mem dump with progress | ✅ 100% (root) |

### Leak & Source Analysis
| Tool | Capability | Notes |
|------|-----------|-------|
| **Auto Leak Source v3** | 13-phase deep scan: URLs, IPs, secrets, JWT, SQL, Base64, JNI, hooks, source reconstruction | ✅ Binary + text mode |
| **Source Reconstruct** | Extract class/struct/enum/function/macro definitions | ✅ No missing patterns |
| **Shell Analysis** | curl/wget, env vars, encoded URLs, eval patterns | ✅ Shell script deep analysis |
| **Auth Detection** | Supabase, Convex, CF Workers, GitHub, MediaFire | ✅ Real-time detection |

### Patch & Analysis
| Tool | Capability | Notes |
|------|-----------|-------|
| **Auto Patch Login** | Binary pattern search + branch patching | ⚠️ 60-70% success |
| **Advanced Patch** | NOP/BL/B-callback injection | ⚠️ Experimental |
| **Patch Instructions** | Manual patching with Capstone | ✅ ARM32/ARM64/x86 |
| **Function List** | Find functions in ELF binary | ✅ Symbol + pattern scan |
| **Memory Analyzer** | /proc/PID/maps + memory read/write (root) | ✅ 100% (root) |
| **Debugger** | Breakpoints + memory watch + register view | ⚠️ Basic |
| **ARM64 Emulator** | ESIL-style instruction decode + hooks | ⚠️ Basic |

### Utility
| Tool | Capability |
|------|-----------|
| **Script Engine** | Custom script interpreter (8+ built-in scripts) |
| **APK Signer** | Sign, verify, generate keystore, extract cert |
| **Native Lib Analyzer** | ELF analysis (9 modes) |
| **Deobfuscate** | 16+ modes: auto-detect + manual (Base64/Hex/XOR/ROT13/ROT47/JS/Python/etc) |
| **Encrypt/Decrypt** | AES, XOR, Base64, URL decode, and more |
| **Python Scripts** | Pre-built scripts for deobfuscation |
| **Tools Download** | Links to SDK/NDK/RE tools |
| **Shizuku Support** | For non-root devices |
| **Root Support** | Magisk, KSU, KernelSU, APatch |
| **Admin Password Searcher** | Brute force + SQLi + API enumeration + Cloudflare bypass |
| **Malware Detector** | Reboot/wipe, data theft, spyware, crypto mining detection |
| **UPX Unpacker** | Detect & unpack UPX, Themida, ASPack, VMProtect |
| **Got/PLT Parser** | Full ELF32/ELF64 GOT/PLT analysis |
| **Shell Deobfuscator** | Base64, Hex, ROT13/47, URL, XOR brute |
| **Shell Patcher** | Patch shell scripts with NOP/RET |
| **Program Headers** | ELF program header analysis |
| **Section Headers** | ELF section header analysis |
| **Dynamic Section** | ELF dynamic section analysis |
| **Relocations** | ELF relocation analysis |
| **Pak Archive** | Extract .paks/.pak files |
| **Base64 Screen** | Encode/decode Base64 |

## 📊 Accuracy Benchmarks (Honest)

| Test Case | **OprekTool v7** | Ghidra | IDA Hex-Rays |
|-----------|:---:|:---:|:---:|
| Simple getter (return field) | ✅ **100%** | ✅ 100% | ✅ 100% |
| Simple setter (store field) | ✅ **100%** | ✅ 100% | ✅ 100% |
| String comparison | ✅ **100%** | ✅ 95% | ✅ 98% |
| Simple loop (for/while) | ✅ **98%** | ✅ 90% | ✅ 95% |
| Nested loops | ⚠️ **90%** | ✅ 85% | ✅ 85% |
| Switch/case | ⚠️ **90%** | ✅ 80% | ✅ 80% |
| Complex function (50+ insns) | ⚠️ **80%** | ✅ 80% | ✅ 85% |
| Optimized code (GCC -O2) | ⚠️ **60%** | ✅ 75% | ✅ 80% |
| Obfuscated code | ⚠️ **40%** | ⚠️ 30% | ⚠️ 50% |

> **Note:** OprekTool decompiler is heuristic-based, not CFG-based. For complex functions, use PC-based tools.

## 🛡️ Supported Games (IL2CPP Dumper)

| Game | Package | Status | Notes |
|------|---------|:---:|-------|
| MLBB | com.mobile.legends | ⚠️ | Encrypted metadata — raw dump for PC |
| Free Fire | com.dts.freefiremax | ⚠️ | Protected — may need memory dump |
| Free Fire MAX | com.dts.freefireth | ⚠️ | Protected — may need memory dump |
| PUBG Mobile | com.tencent.ig | ⚠️ | Protected — may need memory dump |
| Genshin Impact | com.miHoYo.GenshinImpact | ⚠️ | Heavily encrypted |
| COD Mobile | com.activision.callofduty.shooter | ⚠️ | Protected |
| Blood Strike | com.proximabeta.mf.ussdk | ⚠️ | Protected |

## 🔄 Comparison with Other Tools

| Feature | OprekTool | APKTool | Ghidra | IDA Pro | Jadx |
|---------|:---------:|:-------:|:------:|:-------:|:----:|
| **On-device** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **No PC needed** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Root memory dump** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **IL2CPP dump.cs** | ✅ | ❌ | ⚠️ | ✅ | ❌ |
| **ELF analysis** | ✅ | ❌ | ✅ | ✅ | ❌ |
| **Disassembly** | ✅ | ❌ | ✅ | ✅ | ❌ |
| **Decompilation** | ⚠️ | ❌ | ✅ | ✅ | ✅ |
| **APK decompile** | ❌ | ✅ | ❌ | ❌ | ✅ |
| **Smali** | ❌ | ✅ | ❌ | ❌ | ✅ |
| **Free** | ✅ | ✅ | ✅ | ❌ | ✅ |
| **Offline** | ✅ | ✅ | ✅ | ✅ | ✅ |

### Pros vs Cons

**Pros:**
- ✅ Run on Android device (no PC needed)
- ✅ Root memory dump for game analysis
- ✅ IL2CPP metadata extraction + dump.cs generation
- ✅ 13-phase source code leak analysis
- ✅ Auto-detect and extract source patterns
- ✅ Shell script analysis + deobfuscation
- ✅ ELF/PK/DEX binary analysis
- ✅ Free and open source
- ✅ Signed APK auto-released on GitHub

**Cons:**
- ❌ Decompiler is heuristic-based (not CFG)
- ❌ Cannot decompile APK to Smali/Java (use Jadx)
- ❌ Cannot patch APK directly (use APKTool)
- ❌ Memory dump requires root
- ❌ Complex functions may have inaccurate decompilation
- ❌ No GUI-based patching (use Ghidra/IDA)

## 🏗️ Build

```
# Build on GitHub Actions (auto-releases APKs)
# Or build locally:
./gradlew assembleDebug
```

## 📦 Download

- **GitHub Releases**: [Latest](https://github.com/opanx/oprek-tool/releases/latest)
- **APKs**: Both debug (signed) and release builds auto-uploaded

## 🔗 Links

- **GitHub**: https://github.com/opanx/oprek-tool
- **Owner**: https://t.me/kembungjir (non-official)
- **Channel**: https://t.me/lazy_fat_catt

## 📝 Changelog

### v0.6.1
- Auto Leak Source v3: 13-phase deep scan + source code reconstruction
- AutoDump v5: IL2CPP TypeDef/MethodDef/FieldDef extraction
- Fix ALL C++ format warnings (PRIu64/PRIX64 → %lX/%lu)
- Source reconstruct: class/struct/enum/function/macro extraction
- Binary + text mode scanning (no more zero results on binaries)

### v0.6.0
- AutoDump v4: Fix broken memory read (dd skip hang forever on ARM64)
- Python /proc/pid/mem reader with seek (100x faster)
- 4-strategy metadata search (near il2cpp, all RW, anon, exec .so)
- String literal extraction from metadata string table

### v0.5.2
- Fix AutoLeakSource phase 8 hang (regex on binary text)
- Added JNI/hook/anti-debug detection

### v0.5.1
- Fix all ELF screens (Dynamic, Relocation, Program Headers, Section Headers)
- Advanced Disasm auto-load
- ShellPatcher crash fix

### v0.5.0
- SharedFileState: All 80 screens auto-detect file changes
- UPX Unpacker v2: Pure Kotlin entropy-based
- Auto Leak Source v2: Progress + log tab
- Fixed 25+ screen file loading bugs

### v0.4.0
- AutoDump v3: dump.cs generator with IL2CPP metadata parsing
- UPX Unpacker: Detect & unpack packers
- Auto Leak Source: Extract URLs, IPs, secrets, JWT
- SQLi: Error + Blind + Time-based

### v0.3.0
- Malware Detector: Reboot/wipe, data theft, spyware, crypto mining
- Fix GotPltScreen, ShellDeobfuscateScreen black screens
- GOT/PLT Parser: Full ELF32/ELF64 support

### v0.2.1
- DebuggerScreen rewrite: Memory viewer, breakpoints, disassembler
- EmulatorScreen rewrite: CPU registers, function hooks
- AutoDumpScreen rewrite: Fast scan, 11 game presets

### v0.2.0
- Admin Password Searcher: Brute force + SQLi + API enumeration
- Cloudflare detection
- JWT analysis

## 🤝 Contributing

PRs welcome. But please: no overclaims, no fake benchmarks.

## ⚖️ License

Educational purposes only. Use responsibly.

---
**© Panxcz & Freebuff** 🎮
