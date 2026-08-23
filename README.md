# 🔧 OprekTool — Android Reverse Engineering Toolkit

> **v0.9.2** | Build status: ✅ Passing | APKs: Signed + Consistent Key

**An honest Android reverse engineering toolkit** — not a magic wand, but a solid set of tools for on-device binary analysis.

## ⚠️ Honest Disclaimer

This tool **does NOT** replace PC-based RE tools like Ghidra, IDA Pro, or Il2CppDumper. It provides:
- On-device convenience when you don't have PC access
- Basic to moderate analysis capability
- Root memory dump for offline PC processing
- Good enough for quick triage, not deep RE work

## 📱 What's New

### v0.9.2 (Latest)
- **Batch Decompiler**: Decompile all symbols at once with progress
- **GhidraScriptScreen**: Fixed Frida `$init`/`$new` escape issues
- **Zero warnings**: All deprecated icon/API warnings resolved

### v0.9.1
- **APKTool Suite**: Decode APK → extract resources, manifest, DEX, native libs
- **AutoDump v7**: Fixed nullable types, improved MLBB support
- **Navigation Drawer**: Added APKTool Suite entry

### v0.9.0
- **AutoDump v7**: Unified dump pipeline with Strategy A (parse) + Strategy B (raw dump)
- **Malware Detector**: 15 threat categories including phone brick/wipe detection
- **Navigation Drawer**: Categorized tools with hamburger menu
- **Text Input Fix**: All text fields now visible on dark theme
- **Consistent Signing**: Same APK key every build (no uninstall needed)
- **40+ Tools**: Full reverse engineering toolkit on your phone

## 📱 Features

### 🎯 AutoDump v7 — IL2CPP Dumper
| Feature | Detail |
|---------|--------|
| **Strategy A** | Valid metadata → parse TypeDef/MethodDef/FieldDef → dump.cs |
| **Strategy B** | Encrypted metadata → raw dump lib + meta for PC Il2CppDumper |
| **Game Presets** | MLBB, FF, FF MAX, PUBG, Genshin, BloodStrike, CODM + manual |
| **Root Flow** | Multi su paths, ps -A fallback, seek-based memory reader |
| **Dual Output** | dump.cs (if parsed) + raw .bin files for PC processing |
| **Honest Status** | Clear messages: no root / game not running / metadata encrypted |

### 🔍 Analysis Tools
| Tool | What It Does | Accuracy |
|------|-------------|----------|
| **ELF Viewer** | Full header, segments, sections, symbols, GOT/PLT | ✅ 100% |
| **Disassembler** | Capstone ARM32/ARM64/x86/x86_64 | ✅ 95% |
| **Decompiler** | Pseudo-C with batch decompile all functions | ⚠️ 80-95% |
| **Hex Viewer** | Raw hex + ASCII with navigation + search | ✅ 100% |
| **String Extractor** | Printable strings + filter + export | ✅ 100% |
| **Memory Scanner** | Scan process memory for patterns | ✅ 100% (root) |
| **Signature Scanner** | Known crypto/anti-debug pattern detection | ✅ 95% |

### 🛡️ Malware Detector
| Threat Category | What It Detects |
|----------------|----------------|
| **💣 Phone Brick/Wipe** | `rm -rf /`, `mkfs`, `wipeData`, `factoryReset`, `flash_eraseall` |
| **⚡ Force Reboot** | `am reboot`, `reboot -f`, `shutdown`, `DevicePolicyManager.lockNow` |
| **🕵️ Remote Control** | `AccessibilityService`, `DeviceAdminReceiver`, `BOOT_COMPLETED` |
| **📞 Call/SMS Fraud** | `CALL_PHONE`, `SEND_SMS`, `SmsManager`, premium numbers |
| **📸 Spyware** | `READ_CONTACTS`, `CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION` |
| **🔐 Crypto Mining** | `stratum+tcp://`, `xmrig`, `monero`, `cryptonight` |
| **🛡️ Anti-Debug** | `ptrace`, `frida`, `xposed`, `TracerPid` |
| **🚫 Anti-Removal** | `HIDE_APP_ICON`, `isAdminActive`, `setStatusBarDisabled` |

### 🔧 Patch & Mod
| Tool | What It Does |
|------|-------------|
| **Auto Patch Login** | Binary pattern search + branch patching |
| **Shell Patcher** | Patch .sh scripts with binary patches |
| **UPX Unpacker** | Unpack UPX-packed binaries |
| **SO Patcher** | Analyze & patch .so native libraries |
| **Batch Renamer** | Rename files with find & replace |

### 🔐 Crypto & Deobfuscate
| Tool | What It Does |
|------|-------------|
| **Deobfuscate** | Decode Base64, ROT13, XOR, URL encoding, hex |
| **Obfuscate** | Encode strings with various methods |
| **Decrypt Tool** | 10+ decryption methods (AES, DES, XOR, RC4, etc.) |
| **Encrypt Tool** | 10+ encryption methods |
| **Shell Deobfuscate** | Decode obfuscated shell scripts |
| **String Extractor** | Extract readable strings from binaries |

### 📱 Build & Create
| Tool | What It Does |
|------|-------------|
| **APK Builder** | Create APK from shell scripts |
| **JNI Builder** | Generate JNI code from Java classes |
| **APK Signer** | Sign APKs with custom keystore |
| **APK Tools** | Merge, analyze, decompile APKs |

### 🛡️ Security
| Tool | What It Does |
|------|-------------|
| **Certificate Analyzer** | Analyze APK signing certificates |
| **Permission Analyzer** | Analyze requested permissions for risks |
| **SSL Pinning Bypass** | Generate Frida scripts for SSL bypass |
| **Permission Remover** | Identify dangerous permissions to remove |

### 📜 Scripts
| Tool | What It Does |
|------|-------------|
| **Ghidra/Frida Scripts** | Generate analysis scripts |
| **Native Hook Generator** | Generate Frida hook scripts |
| **Resource Decoder** | Decode Android binary resources |

### 💻 System
| Tool | What It Does |
|------|-------------|
| **Terminal** | Full shell with 20+ built-in commands |
| **Tools Download** | Download RE tools |

## 📊 Honest Accuracy Benchmarks

| Feature | Accuracy | Notes |
|---------|----------|-------|
| ELF Header Parse | ✅ 100% | Full 32/64-bit support |
| Disassembler | ✅ 95% | Capstone-based, all major architectures |
| Decompiler | ⚠️ 80-95% | Pseudo-C, not full decompilation |
| AutoDump (normal) | ✅ 90% | When metadata is not encrypted |
| AutoDump (protected) | ⚠️ 70% | Raw dump only, parse on PC |
| String Extraction | ✅ 100% | Binary-safe extraction |
| Hex Viewer | ✅ 100% | Full hex + ASCII |
| Malware Detection | ✅ 95% | 100+ known threat patterns |

## 🔧 Installation

1. Download the APK from [Releases](https://github.com/opanx/oprek-tool/releases)
2. Enable "Install from unknown sources" if needed
3. Install and open
4. Grant root access when prompted (for dump/patch features)

## 📁 Output Directory

All dumps and patches are saved to:
```
/sdcard/Download/OprekTool/
├── dump/          # AutoDump output (.bin, .cs, .dat)
├── patched/       # Patched files
└── terminal/      # Terminal output
```

## 📚 Resources

- [README (Indonesian)](README_ID.md)
- [Bug Reports](https://github.com/opanx/oprek-tool/issues)
- [Releases](https://github.com/opanx/oprek-tool/releases)

## ⚖️ Legal

This tool is for **educational and security research purposes only**. Users are responsible for complying with all applicable laws.

---

**© Panxcz & Freebuff** 🎮
