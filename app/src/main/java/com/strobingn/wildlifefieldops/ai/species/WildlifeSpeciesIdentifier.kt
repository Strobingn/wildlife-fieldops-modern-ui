package com.strobingn.wildlifefieldops.ai.species

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.strobingn.wildlifefieldops.ai.AiAnalysisResult
import com.strobingn.wildlifefieldops.data.remote.AiEdgeGateway
import com.strobingn.wildlifefieldops.data.remote.AiEdgeProtocol
import com.strobingn.wildlifefieldops.data.remote.EdgeChatResult
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Photo species ID. Cloud Grok (Supabase `ai-assistant`, fast model) is first.
 * On-device ML Kit is the fallback and cannot confidently call a raccoon a dog.
 */
@Singleton
class WildlifeSpeciesIdentifier @Inject constructor(
    private val edge: AiEdgeGateway,
) {
    suspend fun identifyStill(context: Context, imageUri: Uri): SpeciesSuggestion? {
        val cloud = identifyCloud(context, imageUri)
        if (cloud != null) return cloud
        val analysis = com.strobingn.wildlifefieldops.ai.PhotoAIHelper.analyzePhotoForFormFilling(context, imageUri)
        return OnDeviceSpeciesClassifier.suggestFromAnalysis(analysis)
    }

    suspend fun identifyFromAnalysis(
        context: Context,
        imageUri: Uri,
        analysis: AiAnalysisResult,
    ): SpeciesSuggestion? {
        val cloud = identifyCloud(context, imageUri)
        if (cloud != null) return cloud
        return OnDeviceSpeciesClassifier.suggestFromAnalysis(analysis)
    }

    private fun identifyCloud(context: Context, imageUri: Uri): SpeciesSuggestion? {
        if (!edge.isConfigured) return null
        val image = jpegDataUrl(context, imageUri) ?: return null
        val result = edge.complete(
            system = WildlifeSpeciesCatalog.visionSystemPrompt(),
            user = WildlifeSpeciesCatalog.VISION_USER_PROMPT,
            maxTokens = 400,
            temperature = 0.1,
            jsonMode = true,
            imageDataUrl = image,
            connectTimeoutMs = 12_000,
            readTimeoutMs = 22_000,
        )
        val text = when (result) {
            is EdgeChatResult.Ok -> if (result.vision) result.text else return null
            is EdgeChatResult.Err -> return null
        }
        return CloudSpeciesIdParser.parse(text)?.toSuggestion("grok")
    }

    companion object {
        fun jpegDataUrl(context: Context, imageUri: Uri): String? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > 1536 || bounds.outHeight / sample > 1536) sample *= 2
            val decoded = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val scaled = scaleDown(decoded, 768)
            val bytes = ByteArrayOutputStream()
            val wrote = scaled.compress(Bitmap.CompressFormat.JPEG, 60, bytes)
            if (scaled !== decoded) scaled.recycle()
            if (!decoded.isRecycled) decoded.recycle()
            if (!wrote) return null
            val jpeg = bytes.toByteArray()
            if (jpeg.size > 900_000) return null
            val encoded = Base64.encodeToString(jpeg, Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$encoded"
            if (dataUrl.length > AiEdgeProtocol.MAX_IMAGE_CHARS) return null
            return dataUrl
        }

        private fun scaleDown(source: Bitmap, maxEdge: Int): Bitmap {
            val edge = maxOf(source.width, source.height)
            if (edge <= maxEdge || edge <= 0) return source
            val scale = maxEdge.toFloat() / edge.toFloat()
            val width = (source.width * scale).toInt().coerceAtLeast(1)
            val height = (source.height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(source, width, height, true)
        }
    }
}
