package com.oprek.tool.core

object NativeLib {
    init {
        try {
            System.loadLibrary("oprek_native")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("NativeLib", "Failed to load native library: ${e.message}")
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
    // arch: 0=ARM, 1=ARM64, 2=X86
    // mode: 0=ARM, 1=THUMB, 2=ARM64, 3=X86_64, 4=X86_32
    external fun disassemble(code: ByteArray, offset: Long, arch: Int, mode: Int, count: Int): String
    external fun disassembleFunction(code: ByteArray, funcAddr: Long, funcSize: Long, arch: Int, mode: Int): String

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

    // Capstone constants
    companion object {
        const val ARCH_ARM = 0
        const val ARCH_ARM64 = 1
        const val ARCH_X86 = 2

        const val MODE_ARM = 0
        const val MODE_THUMB = 1
        const val MODE_ARM64 = 2
        const val MODE_X86_64 = 3
        const val MODE_X86_32 = 4

        fun detectArchFromElf(eMachine: Int): Pair<Int, Int> {
            return when (eMachine) {
                0x28 -> ARCH_ARM to MODE_ARM
                0xB7 -> ARCH_ARM64 to MODE_ARM64
                0x03 -> ARCH_X86 to MODE_X86_32
                0x3E -> ARCH_X86 to MODE_X86_64
                else -> ARCH_ARM64 to MODE_ARM64
            }
        }
    }
}
