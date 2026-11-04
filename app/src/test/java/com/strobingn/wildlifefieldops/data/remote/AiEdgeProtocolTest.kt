package com.strobingn.wildlifefieldops.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
    fun visionRequestCarriesImageDataUrlAndNoProviderSecret() {
        val raw = AiEdgeProtocol.requestJson(
            system = "system",
            user = "user",
            maxTokens = 400,
            temperature = 0.1,
            jsonMode = true,
            imageDataUrl = "data:image/jpeg;base64,aaaa"
        )
        val body = json.parseToJsonElement(raw).jsonObject
        assertEquals("data:image/jpeg;base64,aaaa", body["imageDataUrl"]?.jsonPrimitive?.content)
        assertEquals("complete", body["action"]?.jsonPrimitive?.content)
        assertFalse(raw.contains("api.x.ai"))
        assertFalse(raw.contains("XAI_API_KEY"))
        assertFalse(body.containsKey("model"))
    }

    @Test
    fun visionPayloadOmitsExamplesUntilSirHasCorrections() {
        val raw = AiEdgeProtocol.requestJson(
            system = "system",
            user = "user",
            maxTokens = 400,
            temperature = 0.1,
            jsonMode = true,
            imageDataUrl = "data:image/jpeg;base64,aaaa",
            detectionExamples = emptyList(),
        )
        val body = json.parseToJsonElement(raw).jsonObject
        assertFalse(body.containsKey("detectionExamples"))
        assertEquals("complete", body["action"]?.jsonPrimitive?.content)
    }

    @Test
    fun visionPayloadCarriesFewShotExamplesAndDropsHugeThumbnails() {
        val huge = "data:image/jpeg;base64," + "a".repeat(40_000)
        val raw = AiEdgeProtocol.requestJson(
            system = "system",
            user = "identify",
            maxTokens = 400,
            temperature = 0.1,
            jsonMode = true,
            imageDataUrl = "data:image/jpeg;base64,bbbb",
            detectionExamples = listOf(
                DetectionExampleWire(
                    label = "Soffit",
                    description = "Open soffit gap",
                    verdict = "NO",
                    modelLabel = "Vent",
                    thumbnailDataUrl = huge,
                )
            ),
        )
        val body = json.parseToJsonElement(raw).jsonObject
        val example = body["detectionExamples"]!!.jsonArray.first().jsonObject
        assertEquals("Soffit", example["label"]?.jsonPrimitive?.content)
        assertEquals("Vent", example["modelLabel"]?.jsonPrimitive?.content)
        assertEquals("NO", example["verdict"]?.jsonPrimitive?.content)
        assertFalse(example.containsKey("thumbnailDataUrl"))
        assertFalse(raw.contains("XAI_API_KEY"))
        assertEquals("complete", body["action"]?.jsonPrimitive?.content)
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
        assertFalse(ok.vision)
    }

    @Test
    fun parseReadsReferenceLibraryMatchAndIgnoresAnEmptyLibrary() {
        val matched = AiEdgeProtocol.parse(
            200,
            """{"ok":true,"provider":"xai","vision":true,"text":"{\"species\":\"Raccoon\"}","referenceMatch":{"species":"Raccoon","similarPhotos":4}}"""
        ) as EdgeChatResult.Ok
        assertEquals("Raccoon", matched.referenceMatch?.species)
        assertEquals(4, matched.referenceMatch?.similarPhotos)
        val empty = AiEdgeProtocol.parse(
            200,
            """{"ok":true,"provider":"xai","vision":true,"text":"{\"species\":\"Raccoon\"}"}"""
        ) as EdgeChatResult.Ok
        assertEquals(null, empty.referenceMatch)
    }

    @Test
    fun parseMarksVisionWhenTheFunctionSawThePhoto() {
        val parsed = AiEdgeProtocol.parse(
            200,
            """{"ok":true,"provider":"xai","vision":true,"text":"{\"species\":\"Raccoon\"}"}"""
        ) as EdgeChatResult.Ok
        assertTrue(parsed.vision)
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
