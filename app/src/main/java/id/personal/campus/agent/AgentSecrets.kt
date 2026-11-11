package id.personal.campus.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Penyimpanan kunci API: AES-GCM dengan kunci dari Android Keystore, ciphertext + IV
 * ditulis ke berkas privat di [Context.getNoBackupFilesDir] (tidak ikut auto-backup).
 *
 * Aturan yang tidak boleh dilanggar:
 * - kunci tidak pernah masuk log, pesan exception, atau `toString()`;
 * - semua pesan error di sini generik, tanpa potongan kunci.
 */
class AgentSecrets(context: Context) {
    private val file = File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    /** Memeriksa keberadaan kunci tanpa mendekripsi (murah, tidak menyentuh Keystore). */
    fun hasKey(): Boolean = file.isFile && file.length() > HEADER_BYTES

    fun saveKey(key: String) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "Kunci API belum diisi." }
        require(trimmed.length <= MAX_KEY_LENGTH) { "Kunci API terlalu panjang." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        require(iv.size in 1..255) { "IV kunci tidak didukung perangkat ini." }
        val payload = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        writeAtomically(byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + payload)
    }

    /** `null` bila belum ada, atau bila berkas rusak/kunci Keystore berubah (berkas dibuang). */
    fun loadKey(): String? {
        if (!hasKey()) return null
        return try {
            val bytes = file.readBytes()
            if (bytes.size <= HEADER_BYTES || bytes[0] != FORMAT_VERSION) {
                clearKey()
                return null
            }
            val ivSize = bytes[1].toInt() and 0xFF
            if (ivSize <= 0 || bytes.size <= HEADER_BYTES + ivSize) {
                clearKey()
                return null
            }
            val iv = bytes.copyOfRange(HEADER_BYTES, HEADER_BYTES + ivSize)
            val payload = bytes.copyOfRange(HEADER_BYTES + ivSize, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } catch (_: Exception) {
            // Termasuk AEADBadTagException (berkas diubah) atau kunci Keystore hilang:
            // simpanan tidak dapat dipercaya lagi, jadi dibuang dan user diminta mengisi ulang.
            clearKey()
            null
        }
    }

    fun clearKey() {
        try {
            if (file.exists()) file.delete()
        } catch (_: SecurityException) {
            // Tidak ada yang bisa dilakukan; hasKey() akan tetap benar karena berkas masih ada.
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun writeAtomically(bytes: ByteArray) {
        val directory = file.parentFile ?: throw IllegalStateException("Penyimpanan privat tidak tersedia.")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IllegalStateException("Penyimpanan privat tidak dapat dibuat.")
        }
        val temp = File(directory, "$FILE_NAME.tmp")
        try {
            temp.outputStream().use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kampus_agent_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FILE_NAME = "agent_key.bin"
        const val FORMAT_VERSION: Byte = 1
        const val HEADER_BYTES = 2
        const val GCM_TAG_BITS = 128
        const val MAX_KEY_LENGTH = 8192
    }
}
