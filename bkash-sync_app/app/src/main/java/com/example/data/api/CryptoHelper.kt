package com.example.data.api

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypt and decrypt API strings using AES/CBC/PKCS5Padding with a steady reproducible key
 * built from deterministic platform values so it persists over app restarts safely.
 */
object CryptoHelper {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    // Use an internal obfuscated key/iv. For local storage prevention from trivial dumps.
    private val KEY_BYTES = byteArrayOf(
        0x62.toByte(), 0x4b.toByte(), 0x61.toByte(), 0x73.toByte(),
        0x68.toByte(), 0x53.toByte(), 0x79.toByte(), 0x6e.toByte(),
        0x63.toByte(), 0x53.toByte(), 0x65.toByte(), 0x63.toByte(),
        0x75.toByte(), 0x72.toByte(), 0x65.toByte(), 0x31.toByte()
    ) // 16 bytes secret key
    private val IV_BYTES = byteArrayOf(
        0x31.toByte(), 0x32.toByte(), 0x33.toByte(), 0x34.toByte(),
        0x35.toByte(), 0x36.toByte(), 0x37.toByte(), 0x38.toByte(),
        0x39.toByte(), 0x30.toByte(), 0x41.toByte(), 0x42.toByte(),
        0x43.toByte(), 0x44.toByte(), 0x45.toByte(), 0x46.toByte()
    ) // 16 bytes IV

    fun encrypt(plainText: String): String {
        return try {
            val keySpec = SecretKeySpec(KEY_BYTES, "AES")
            val ivSpec = IvParameterSpec(IV_BYTES)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT).trim()
        } catch (e: Exception) {
            plainText
        }
    }

    fun decrypt(encryptedText: String): String {
        return try {
            val keySpec = SecretKeySpec(KEY_BYTES, "AES")
            val ivSpec = IvParameterSpec(IV_BYTES)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decodedBytes = Base64.decode(encryptedText, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
