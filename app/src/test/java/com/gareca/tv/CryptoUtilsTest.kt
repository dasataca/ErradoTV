package com.gareca.tv

import com.gareca.tv.api.CryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoUtilsTest {

    @Test
    fun testFactoryKeyNotEmptyAndValidLength() {
        val factoryKey = CryptoUtils.getFactoryKey()
        assertNotNull("Factory key should not be null", factoryKey)
        assertFalse("Factory key should not be empty", factoryKey.isEmpty())
        assertEquals("Factory key standard base64 length is 44", 44, factoryKey.length)
        assertTrue("Factory key ends with double equal padding", factoryKey.endsWith("=="))
    }

    @Test
    fun testPlainJsonBypassesDecryption() {
        val sampleJson = """{"version":"1.9.19","status":"ok"}"""
        val result = CryptoUtils.decrypt(sampleJson)
        assertEquals("Plain JSON objects should be returned without alteration", sampleJson, result)

        val sampleJsonArray = """[{"id":1,"name":"Canal 1"}]"""
        val arrayResult = CryptoUtils.decrypt(sampleJsonArray)
        assertEquals("Plain JSON arrays should be returned without alteration", sampleJsonArray, arrayResult)
    }

    @Test
    fun testInvalidOrMalformedPayloadReturnsEmpty() {
        val corruptedPayload = "not_valid_base64_payload_at_all!!@#$%"
        val result = CryptoUtils.decrypt(corruptedPayload)
        assertEquals("Corrupted or invalid encrypted payload should safely resolve to empty string", "", result)
    }

    @Test
    fun testDecryptEnvelopeWithShortHexReturnsEmpty() {
        val shortHex = "0011223344"
        val result = CryptoUtils.decryptEnvelope(shortHex)
        assertEquals("Envelope hex shorter than minimum IV+block size should return empty string", "", result)
    }

    @Test
    fun testEncryptAndDecryptRoundtripWithFactoryKey() {
        val originalText = """{"status":"success","channels_count":42}"""
        val key = CryptoUtils.getFactoryKey()

        val encryptedBase64 = CryptoUtils.encryptWithKey(originalText, key)
        assertNotNull("Encrypted string should not be null", encryptedBase64)
        assertFalse("Encrypted string should not be empty", encryptedBase64.isEmpty())

        // Decrypt using CryptoUtils.decrypt which uses factory key
        val decryptedText = CryptoUtils.decrypt(encryptedBase64)
        assertEquals("Decrypted text should match the original plaintext payload", originalText, decryptedText)
    }

    @Test
    fun testResilientBase64EncodingDecoding() {
        val testBytes = "GarecaTV Encrypted Test Payload 2026".toByteArray(Charsets.UTF_8)
        val encoded = CryptoUtils.encodeBase64(testBytes)
        assertNotNull(encoded)
        val decoded = CryptoUtils.decodeBase64(encoded)
        assertEquals("Decoded bytes should match original bytes", String(testBytes, Charsets.UTF_8), String(decoded, Charsets.UTF_8))
    }
}
