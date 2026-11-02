package dev.ciryva.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * User-owned OpenAI key encrypted by an AndroidKeyStore non-exportable AES key.
 * Ciphertext stays in the OS noBackup directory and is never written into a
 * regular SharedPreference, log, Git repository, Intent extra or backend.
 *
 * For personal BYOK only. This protects at rest, not against root compromise,
 * malicious accessibility services or a tampered APK.
 */
class AndroidByokKeyStore(private val context: Context) {
    private val alias = "dev.ciryva.openai.byok.v1"
    private val filename = "ciryva-openai-key.bin"
    private val store get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val file: File get() = File(context.noBackupFilesDir, filename)

    fun hasKey(): Boolean = file.isFile

    fun save(value: String) {
        require(value.isNotBlank() && value.length <= 512 &&
            '\n' !in value && '\r' !in value) { "Invalid API key format" }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val secret = (store.getKey(alias, null) as? SecretKey) ?: run {
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generator.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        val iv = cipher.iv
        require(iv.size == 12) { "Unexpected encryption IV" }
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val encoded = byteArrayOf(0x43, 0x49, 0x52, 0x59) + iv + ciphertext
        val temporary = File.createTempFile("ciryva-key-", ".tmp", context.noBackupFilesDir)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(encoded)
                output.fd.sync()
            }
            // On Android's app-private directory, rename replaces an existing
            // file atomically; no plaintext temp file is ever created.
            require(temporary.renameTo(file)) { "Unable to commit encrypted key" }
        } finally {
            temporary.delete()
        }
    }

    fun load(): String? {
        if (!file.isFile) return null
        val encoded = file.readBytes()
        require(encoded.size in 33..4096 &&
            encoded.copyOfRange(0, 4).contentEquals(byteArrayOf(0x43, 0x49, 0x52, 0x59))) {
            "Invalid encrypted key file"
        }
        val secret = store.getKey(alias, null) as? SecretKey
            ?: throw IllegalStateException("KeyStore key unavailable; remove and re-enter key")
        val iv = encoded.copyOfRange(4, 16)
        val ciphertext = encoded.copyOfRange(16, encoded.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    /** Remove both encrypted content and the local Keystore entry. */
    fun clear() {
        require(!file.exists() || file.delete()) { "Unable to remove key file" }
        store.deleteEntry(alias)
    }
}
