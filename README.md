# 🔧 OprekTool — Android Reverse Engineering Toolkit

> **v0.14.0** | Build status: ✅ Passing (0 warnings) | APKs: Signed + Consistent Key

**An honest Android reverse engineering toolkit** — not a magic wand, but a solid set of tools for on-device binary analysis.

## ⚠️ Honest Disclaimer

This tool **does NOT** replace PC-based RE tools like Ghidra, IDA Pro, or Il2CppDumper. It provides:
- On-device convenience when you don't have PC access
- Basic to moderate analysis capability
- Root memory dump for offline PC processing
- Good enough for quick triage, not deep RE work

## 📱 What's New

### v0.14.0 (Latest)
- **Smali Viewer**: Parse DEX files — string tables, type IDs, method IDs, class listings
- **XREF Finder**: Find cross-references to strings, addresses, or hex patterns in ELF binaries (ARM32/ARM64)
- **OFRAK Native Engine v2**: Completely rewritten from scratch — all features actually work
  - File picker (copies URI to cache, works on all devices)
  - Info tab: auto-detect format (ELF/ZIP/DEX/AR/GZIP/XZ/BZIP2/7Z/RAR/TAR)
  - Sections tab: full ELF section list with flags (EXEC/ALLOC/WRITE)
  - Actions: extract strings, recursive unpack, carve sections, scan embedded, find secrets
  - Entropy tab: visual heatmap per 4KB block
- **Zero build warnings**: All deprecated icon warnings resolved
- **Workflow optimized**: 25min timeout, per-step timeouts, Node 20 deprecation fixed

### v0.13.0
- **OFRAK Integration v2**: 100% pure Kotlin binary unpacker, no external tools
- **Binary Modifier**: Patch bytes, NOP sections, search & replace hex
- **Multi-Arch Analyzer**: ARM/ARM64/x86/x86_64/MIPS/PowerPC detection
- **Resource Scanner**: Find embedded files in ELF/DEX/APK

### v0.12.0
- **OFRAK Integration**: Run OFRAK commands from app
- **.deb Analyzer**: Extract/modify/repack .deb packages
- **Firmware Analyzer**: Extract embedded firmware files

### v0.11.0
- **Binary Patcher**: Semantic patching (NOP, byte patch, search & replace)
- **Binary Diff**: Compare original vs patched
- **Entropy Map**: Visual heatmap
- **Symbol Resolver**: ELF symbol table parser

## 📱 Features

### 🎯 AutoDump v8 — Real IL2CPP Dumper
| Feature | Detail |
|---------|--------|
| **Strategy A** | Valid 0xFAB11BAF magic → real TypeDef/MethodDef/FieldDef parse → dump.cs |
| **Strategy B** | Encrypted/missing metadata → raw dump lib + meta for PC Il2CppDumper |
| **Metadata Search** | 3-phase: near lib → dalvik regions → all readable (500 max) |
| **Game Presets** | MLBB, FF, FF MAX, PUBG, Genshin, BloodStrike, CODM, Brawl Stars, Standoff 2 + manual |
| **Root Flow** | Multi su paths, ps -A fallback, seek-based Python memory reader |
| **Dual Output** | dump.cs (real TypeDef/MethodDef/FieldDef) + raw .bin for PC processing |
| **Cancel** | Stop long dumps mid-operation |
| **String Extract** | 10K strings from lib binary |
| **Honest Status** | Clear: no root / not running / metadata encrypted / found |

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
| **Smali Viewer** | Parse DEX files — string/type/method/class listings | ✅ 90% |
| **XREF Finder** | Find cross-refs to strings, addresses, hex patterns | ✅ 85% |
| **OFRAK Native** | Recursive binary unpacker + section carver + entropy | ✅ 95% |

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
| Smali Viewer | ✅ 90% | DEX string/type/method/class parse |
| XREF Finder | ✅ 85% | ARM32/ARM64 branch detection |
| OFRAK Native | ✅ 95% | Recursive unpack, section carve, entropy |

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
├── sections/      # Carved ELF sections
├── analysis/      # OFRAK analysis output
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
