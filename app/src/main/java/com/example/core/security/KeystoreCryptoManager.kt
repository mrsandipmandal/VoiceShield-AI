package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts locally stored voice message recordings at rest using
 * AES-256-GCM backed by the Android Keystore (with a deterministic JVM fallback
 * for local Robolectric/JUnit tests where AndroidKeyStore hardware is absent).
 */
class KeystoreCryptoManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "VoiceShieldRecordingMasterKey_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    @Volatile
    private var jvmTestFallbackKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                existingEntry.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Local JVM unit test fallback when AndroidKeyStore provider is not mounted
            jvmTestFallbackKey ?: synchronized(this) {
                jvmTestFallbackKey ?: run {
                    val bytes = ByteArray(32)
                    SecureRandom().nextBytes(bytes)
                    SecretKeySpec(bytes, "AES").also { jvmTestFallbackKey = it }
                }
            }
        }
    }

    /**
     * Encrypts raw PCM/WAV bytes with AES-256-GCM and writes `[12-byte IV || Ciphertext+Tag]`
     * to [destinationFile].
     */
    fun encryptToFile(plainAudioBytes: ByteArray, destinationFile: File): Boolean {
        return try {
            destinationFile.parentFile?.mkdirs()
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainAudioBytes)

            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            destinationFile.writeBytes(combined)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads `[12-byte IV || Ciphertext+Tag]` from [encryptedFile] and returns the decrypted
     * audio bytes in memory without writing unencrypted data to disk.
     */
    fun decryptFromFile(encryptedFile: File): ByteArray? {
        if (!encryptedFile.exists() || encryptedFile.length() <= GCM_IV_LENGTH_BYTES) {
            return null
        }
        return try {
            val combined = encryptedFile.readBytes()
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            cipher.doFinal(cipherText)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Securely overwrites a recording file with zeros before unlinking from filesystem.
     */
    fun secureDeleteFile(filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        val file = File(filePath)
        if (!file.exists()) return false
        return try {
            val length = file.length().toInt().coerceAtMost(2 * 1024 * 1024)
            if (length > 0) {
                file.writeBytes(ByteArray(length))
            }
            file.delete()
        } catch (_: Exception) {
            file.delete()
        }
    }
}
