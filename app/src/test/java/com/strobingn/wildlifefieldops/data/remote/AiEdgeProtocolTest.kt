package com.strobingn.wildlifefieldops.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEdgeProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun requestTargetsEdgeCompleteAndCarriesNoProviderSecret() {
        val raw = AiEdgeProtocol.requestJson(
            system = "system",
            user = "user",
            maxTokens = 9000,
            temperature = 4.0,
            jsonMode = true
        )
        val body = json.parseToJsonElement(raw).jsonObject
        assertEquals("complete", body["action"]?.jsonPrimitive?.content)
        assertEquals("system", body["system"]?.jsonPrimitive?.content)
        assertEquals("user", body["user"]?.jsonPrimitive?.content)
        assertEquals("2000", body["maxTokens"]?.jsonPrimitive?.content)
        assertEquals("1.0", body["temperature"]?.jsonPrimitive?.content)
        assertEquals("true", body["jsonMode"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("model"))
        assertFalse(raw.contains("api.x.ai"))
        assertFalse(raw.contains("Bearer"))
    }

    @Test
    fun parseReadsAssistantText() {
        val parsed = AiEdgeProtocol.parse(
            200,
            """{"ok":true,"provider":"xai","text":"Check the soffit."}"""
        )
        val ok = parsed as EdgeChatResult.Ok
        assertEquals("Check the soffit.", ok.text)
        assertEquals("xai", ok.provider)
    }

    @Test
    fun parsePrefersFunctionErrorOverStatus() {
        val parsed = AiEdgeProtocol.parse(500, """{"ok":false,"error":"No AI provider secret is configured in Supabase."}""")
        assertEquals(
            "No AI provider secret is configured in Supabase.",
            (parsed as EdgeChatResult.Err).message
        )
    }

    @Test
    fun parseHttpFailureWithoutBody() {
        val parsed = AiEdgeProtocol.parse(401, "")
        assertTrue((parsed as EdgeChatResult.Err).message.contains("401"))
    }

    @Test
    fun parseEmptySuccessIsAnError() {
        val parsed = AiEdgeProtocol.parse(200, """{"ok":true,"text":"  "}""")
        assertEquals("Empty AI response.", (parsed as EdgeChatResult.Err).message)
    }
}
