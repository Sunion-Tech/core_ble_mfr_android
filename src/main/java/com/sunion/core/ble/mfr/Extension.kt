package com.sunion.core.ble.mfr

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.*

//Byte
fun Byte.unSignedInt(): Int = this.toInt() and 0xFF

fun Byte.toHexString(): String {
    return String.format("%02X", this)
}

fun Byte.toBooleanList(list: MutableList<Boolean> = mutableListOf()): List<Boolean> {
    (0..7).forEach { index ->
        list.add((this.toInt() and (1 shl index)) != 0)
    }
    return list
}

//ByteArray
fun ByteArray.toHexPrint(): String {
    return joinToString(", ") { "%02X".format(it) }
}

fun ByteArray.toHexString(): String {
    var hexStr = ""
    for (b in this) hexStr += String.format("%02X", b)
    return hexStr
}

fun ByteArray.toInt(): Int {
    val paddedByteArray = this.copyOf(4)
    val byteBuffer = ByteBuffer.wrap(paddedByteArray)
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
    return byteBuffer.int
}

fun ByteArray.toLong(): Long {
    val paddedByteArray = this.copyOf(8)
    val byteBuffer = ByteBuffer.wrap(paddedByteArray)
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN) // 設定為小端序
    return byteBuffer.long
}

fun ByteArray.accessByteArrayToString(): String{
    return this.map { it.unSignedInt().toString() }.joinToString(separator = "") { it }
}

fun ByteArray.toAsciiString(): String {
    val filteredBytes = this.filter { it != 0.toByte() }.toByteArray()
    return filteredBytes.toString(Charsets.US_ASCII)
}

fun ByteArray.nameToString(): String {
    val filteredBytes = this.filter { it != 0.toByte() }.toByteArray()
    return String(filteredBytes)
}

fun ByteArray.extendedByteArray(length: Int): ByteArray {
    val extendedByteArray = ByteArray(length)
    System.arraycopy(this, 0, extendedByteArray, 0, this.size)
    return extendedByteArray
}

fun ByteArray.toCString(): String {
    // 找出第一個 0x00 的位置
    val nullIndex = this.indexOf(0.toByte())
    return if (nullIndex == -1) {
        // 如果沒有 0x00，就直接轉整個
        String(this, Charsets.UTF_8)
    } else {
        // 只轉 0x00 之前的部分
        String(this, 0, nullIndex, Charsets.UTF_8)
    }
}

//String
fun String.hexToByteArray(): ByteArray {
    val hex: CharArray = this.toCharArray()
    val length = hex.size / 2
    val rawData = ByteArray(length)
    for (i in 0 until length) {
        val high = Character.digit(hex[i * 2], 16)
        val low = Character.digit(hex[i * 2 + 1], 16)
        var value = high shl 4 or low
        if (value > 127) value -= 256
        rawData[i] = value.toByte()
    }
    return rawData
}

fun String.hexStringToByteArray(): ByteArray {
    val hexString = if (this.length % 2 != 0) "0$this" else this
    return hexString.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun String.colonMac(): String {
    return if (":" in this) this.toUpperCaseMac() else this.uppercase(Locale.getDefault()).chunked(2).joinToString(":")
}

fun String.noColonMac(): String {
    return if (":" in this) this.toUpperCaseMac().replace(":", "") else this.uppercase(Locale.getDefault())
}

fun String.accessCodeToHex(): ByteArray {
    return this.takeIf { it.isNotBlank() }
        ?.filter { it.isDigit() }
        ?.map { Character.getNumericValue(it).toByte() }
        ?.toByteArray()
        ?: ByteArray(0)
}

fun String.toUpperCaseMac(): String {
    val parts = this.split(":")
    val upperCaseParts = parts.map { it.uppercase(Locale.getDefault()) }
    return upperCaseParts.joinToString(":")
}

fun String.isDeviceUuid(): Boolean {
    return this.length == 16
}

fun String.toPaddedByteArray(length: Int, charset: Charset = Charsets.UTF_8): ByteArray {
    val byteArray = this.toByteArray(charset)
    return when {
        byteArray.size > length -> throw IllegalArgumentException("Name must be less than or equal to $length bytes")
        byteArray.size == length -> byteArray
        else -> ByteArray(length) { 0x00 }.apply {
            System.arraycopy(byteArray, 0, this, 0, byteArray.size)
        }
    }
}

fun String.toAsciiByteArray(): ByteArray {
    return this.toByteArray(Charsets.US_ASCII)
}

fun String.byteLength(): Int {
    return this.toByteArray(Charsets.UTF_8).size
}

fun String.uIntStringToLittleEndianByteArray(): ByteArray {
    val value = this.toULong()
    val byteArray = ByteArray(8)
    for (i in 0 until 8) {
        byteArray[i] = (value shr (i * 8)).toByte()
    }
    return byteArray
}

//Int
fun Int.toLittleEndianByteArray(): ByteArray {
    val byteArray = ByteArray(4)
    val byteBuffer = ByteBuffer.allocate(4)
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
    byteBuffer.putInt(this)
    byteBuffer.flip()
    byteBuffer.get(byteArray)
    return byteArray
}

fun Int.toLittleEndianByteArrayInt16(): ByteArray {
    if (this !in 0..0xFFFF) {
        throw IllegalArgumentException("Value $this is out of range for UInt16 (0..65535)")
    }
    val bytes = ByteArray(2)
    bytes[0] = (this and 0xFF).toByte()
    bytes[1] = ((this shr 8) and 0xFF).toByte()
    return bytes
}

fun Int.isSupport(): Boolean {
    return this != 0xFF
}

fun Int.isSupport2Byte(): Boolean {
    return this != 0xFFFF
}

fun Int.isNotSupport(): Boolean {
    return this == 0xFF
}

fun Int.isNotSupport2Byte(): Boolean {
    return this == 0xFFFF
}

fun Int.toHexString(): String {
    return String.format("%02X", this)
}

fun Int.toSupportPhoneticLanguageList(): List<Int> {
    val result = mutableListOf<Int>()
    var num = this
    var index = 0
    while (num > 0) {
        if (num and 1 == 1) {
            result.add(index)
        }
        num = num shr 1
        index++
    }
    return result
}

//Long
fun Long.toLittleEndianByteArray(): ByteArray {
    val byteArray = ByteArray(8)
    val byteBuffer = ByteBuffer.allocate(8)
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
    byteBuffer.putLong(this)
    byteBuffer.flip()
    byteBuffer.get(byteArray)
    return byteArray
}

fun Long.toLittleEndianUInt32ByteArray(): ByteArray {
    if (this !in 0..4294967295L) {
        throw IllegalArgumentException("Timestamp $this exceeds UInt32 limit, truncation will occur.")
    }
    val byteArray = ByteArray(4)
    byteArray[0] = (this and 0xFF).toByte()
    byteArray[1] = ((this shr 8) and 0xFF).toByte()
    byteArray[2] = ((this shr 16) and 0xFF).toByte()
    byteArray[3] = ((this shr 24) and 0xFF).toByte()
    return byteArray
}

fun Long.limitValidTimeRange(): Long {
    return when {
        this < 0 -> 0
        this > 4294967295 -> 4294967295
        else -> this
    }
}
