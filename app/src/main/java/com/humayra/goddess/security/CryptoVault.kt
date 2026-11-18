package com.humayra.goddess.security

import android.security.keystore.*
import android.util.Base64
import java.security.*
import javax.crypto.*
import javax.crypto.spec.*

object CryptoVault {
    private const val ALIAS = "humayra_keystore_aes256"

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return kg.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, masterKey()) }
        val out = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "HV1:${Base64.encodeToString(c.iv, Base64.NO_WRAP)}:${Base64.encodeToString(out, Base64.NO_WRAP)}"
    }
    fun decrypt(stored: String): String {
        if (!stored.startsWith("HV1:")) return stored
        return try {
            val parts = stored.split(":"); if (parts.size != 3) return ""
            val iv = Base64.decode(parts[1], Base64.NO_WRAP); val data = Base64.decode(parts[2], Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv)) }
            String(c.doFinal(data), Charsets.UTF_8)
        } catch (_: Throwable) { "" }
    }

    fun encryptWithPassphrase(plain: String, pass: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = SecretKeySpec(skf.generateSecret(PBEKeySpec(pass.toCharArray(), salt, 100_000, 256)).encoded, "AES")
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val out = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "HVAULT1:${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(c.iv, Base64.NO_WRAP)}:${Base64.encodeToString(out, Base64.NO_WRAP)}"
    }
    fun decryptWithPassphrase(blob: String, pass: String): Result<String> = runCatching {
        val parts = blob.trim().split(":")
        require(parts.size == 4 && parts[0] == "HVAULT1") { "সঠিক .hvault ফাইল নয়।" }
        val salt = Base64.decode(parts[1], Base64.NO_WRAP)
        val iv = Base64.decode(parts[2], Base64.NO_WRAP)
        val data = Base64.decode(parts[3], Base64.NO_WRAP)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = SecretKeySpec(skf.generateSecret(PBEKeySpec(pass.toCharArray(), salt, 100_000, 256)).encoded, "AES")
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        String(c.doFinal(data), Charsets.UTF_8)
    }

    fun hashPin(pin: String, salt: String = "humayra_pin_v2"): String {
        val d = MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(d, Base64.NO_WRAP)
    }
}
