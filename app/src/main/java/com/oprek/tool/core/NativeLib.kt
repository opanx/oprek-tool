package com.oprek.tool.core

object NativeLib {
    init {
        try {
            System.loadLibrary("oprek_native")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("NativeLib", "Failed to load native library: \${e.message}")
        }
    }

    // ELF
    external fun elfValidate(data: ByteArray): Boolean
    external fun elfGetInfo(data: ByteArray): String
    external fun elfGetSections(data: ByteArray): Array<String>

    // PE
    external fun peValidate(data: ByteArray): Boolean

    // DEX
    external fun dexValidate(data: ByteArray): Boolean
    external fun dexGetInfo(data: ByteArray): String
    external fun dexGetClasses(data: ByteArray): Array<String>

    // Disassembler (Capstone)
    external fun disassemble(code: ByteArray, offset: Long, arch: Int, mode: Int, count: Int): String
    // arch: 0=ARM, 1=ARM64, 2=X86
    // mode: 0=ARM, 1=THUMB, 2=ARM64, 3=X86_64, 4=X86_32

    // Obfuscate
    external fun xorEncrypt(data: ByteArray, key: Byte): ByteArray
    external fun entropy(data: ByteArray): Double
    external fun detectPacker(data: ByteArray): Int
    external fun packerName(id: Int): String

    // Patch
    external fun patchNop(data: ByteArray, offset: Long): Int
    external fun patchRet(data: ByteArray, offset: Long): Int
    external fun patchRetZero(data: ByteArray, offset: Long): Int
    external fun patchBranchUncond(data: ByteArray, offset: Long, target: Long): Int
    external fun patchCondToUncond(data: ByteArray, offset: Long): Int
    external fun searchPattern(data: ByteArray, pattern: ByteArray, start: Long): Long
}
