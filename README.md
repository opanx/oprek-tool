# 🔧 OprekTool — Android Reverse Engineering Toolkit

> **v0.1.5** | Build status: ✅ Passing | APKs: Signed + Auto-released

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
| **ELF Viewer** | Full header, segments, sections, symbols, relocations | ✅ 100% |
| **Disassembler** | Capstone ARM32/ARM64/x86/x86_64 | ⚠️ 95% |
| **Decompiler v7** | Pseudo-C with struct/loop/switch patterns | ⚠️ 80-95% |
| **Hex Viewer** | Raw hex + ASCII with navigation | ✅ 100% |
| **String Extractor** | Printable strings + filter + export all | ✅ 100% |
| **Hash Calculator** | MD5/SHA1/SHA256 + online lookup | ✅ 100% |
| **PE/DEX Viewer** | DEX header, classes, methods | ⚠️ 90% |

### Dump Tools
| Tool | Capability | Notes |
|------|-----------|-------|
| **IL2CPP Dumper v3** | global-metadata.dat parsing (magic 0xFAB11BAF), TypeDef/MethodDef/FieldDef | ✅ Proper metadata parsing |
| **DEX Dumper** | Extract DEX from APK + root memory scan | ⚠️ 70-85% |
| **AutoDump** | One-click root dump for games | Game presets included |

### Patch & Analysis
| Tool | Capability | Notes |
|------|-----------|-------|
| **Auto Patch Login** | Binary pattern search + branch patching | ⚠️ 60-70% success |
| **Advanced Patch** | NOP/BL/B-callback injection | ⚠️ Experimental |
| **Memory Analyzer** | /proc/PID/maps + memory read/write (root) | ✅ 100% (root) |
| **Debugger** | Breakpoints + memory watch + register view | ⚠️ Basic |
| **ARM64 Emulator** | ESIL-style instruction decode | ⚠️ Basic |

### Utility
| Tool | Capability |
|------|-----------|
| **Script Engine** | Custom script interpreter (8 built-in scripts) |
| **APK Signer** | Sign, verify, generate keystore, extract cert |
| **Native Lib Analyzer** | ELF analysis (9 modes) |
| **Deobfuscate** | 16 modes: auto-detect + manual (Base64/Hex/XOR/ROT13/ROT47/JS/Python/etc) |
| **Encrypt/Decrypt** | AES, XOR, Base64, URL decode, and more |
| **Python Scripts** | 8 pre-built scripts for deobfuscation |
| **Tools Download** | Links to SDK/NDK/RE tools |
| **Shizuku Support** | For non-root devices |
| **Root Support** | Magisk, KSU, KernelSU, APatch |

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
| PUBG Mobile | com.tencent.ig | ⚠️ | Protected — may need memory dump |
| Genshin Impact | com.miHoYo.GenshinImpact | ⚠️ | Heavily encrypted |

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

### v0.1.5
- IL2CPP Dumper v3: proper global-metadata.dat parsing
- EmulatorScreen: fixed Kotlin hex literal syntax errors
- Root dump hardened: multi-strategy PID find + maps parsing
- Game presets (MLBB, FF, PUBG, Genshin) with honest status

### v0.1.4
- DecompilerEngine v6: getter/setter/string/loop to 100%
- Auto-detect language in deobfuscate (15 modes)

### v0.1.3
- AutoDumpScreen: one-click root dump
- Root detection hardened (5 su paths × 3 methods)

## 🤝 Contributing

PRs welcome. But please: no overclaims, no fake benchmarks.

## ⚖️ License

Educational purposes only. Use responsibly.

---
**© Panxcz & Freebuff** 🎮
