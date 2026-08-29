package dev.khayin.app.core.security

import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

object KhaYinSecurityBridge {
    private const val APP_HMAC_SECRET = "khayin_sec_k98_2026_m39_v1_live"

    fun generateNonce(): String =
        UUID.randomUUID().toString().replace("-", "")

    fun generateTimestamp(): Long =
        System.currentTimeMillis()

    fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun computeSignature(
        method: String,
        url: String,
        body: String,
        nonce: String,
        timestamp: Long,
    ): String {
        val canonicalPayload = "$nonce:$timestamp:${method.uppercase()}:$url:$body:$APP_HMAC_SECRET"
        return sha256Hex(canonicalPayload)
    }

    fun verifySignature(
        method: String,
        url: String,
        body: String,
        nonce: String,
        timestamp: Long,
        signature: String,
    ): Boolean {
        val expected = computeSignature(method, url, body, nonce, timestamp)
        return expected.equals(signature, ignoreCase = true)
    }

    /**
     * Encrypts plain text payload using dynamic keystream derived from SHA-256(secret + nonce).
     */
    fun encryptPayload(plainText: String, nonce: String): String {
        if (plainText.isEmpty()) return ""
        val keyHash = sha256Hex("$APP_HMAC_SECRET:$nonce")
        val keyBytes = keyHash.toByteArray(Charsets.UTF_8)
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        val cipherBytes = ByteArray(plainBytes.size)

        for (i in plainBytes.indices) {
            cipherBytes[i] = (plainBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }

        return Base64.getEncoder().encodeToString(cipherBytes)
    }

    /**
     * Decrypts cipher text payload using dynamic keystream derived from SHA-256(secret + nonce).
     */
    fun decryptPayload(cipherBase64: String, nonce: String): String {
        if (cipherBase64.isBlank()) return ""
        return runCatching {
            val keyHash = sha256Hex("$APP_HMAC_SECRET:$nonce")
            val keyBytes = keyHash.toByteArray(Charsets.UTF_8)
            val cipherBytes = Base64.getDecoder().decode(cipherBase64.trim())
            val plainBytes = ByteArray(cipherBytes.size)

            for (i in cipherBytes.indices) {
                plainBytes[i] = (cipherBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }

            String(plainBytes, Charsets.UTF_8)
        }.getOrElse { cipherBase64 }
    }

    /**
     * Generates secure headers for network requests.
     */
    fun buildSecureHeaders(
        method: String,
        url: String,
        body: String = "",
        nonce: String = generateNonce(),
        timestamp: Long = generateTimestamp(),
    ): Map<String, String> {
        val signature = computeSignature(method, url, body, nonce, timestamp)
        return mapOf(
            "x-khayin-nonce" to nonce,
            "x-khayin-timestamp" to timestamp.toString(),
            "x-khayin-signature" to signature,
            "x-khayin-app-id" to "khayin_tv_android",
        )
    }
}
