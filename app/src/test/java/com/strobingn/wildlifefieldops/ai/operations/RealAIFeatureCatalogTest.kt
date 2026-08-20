package com.strobingn.wildlifefieldops.ai.operations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealAIFeatureCatalogTest {

    @Test
    fun catalogContainsExactly25DistinctTools() {
        val features = RealAIFeatureCatalog.features
        assertEquals(25, features.size)
        assertEquals(25, features.map { it.id }.toSet().size)
        assertEquals(25, features.map { it.title }.toSet().size)
    }

    @Test
    fun everyToolHasExecutablePromptMetadata() {
        RealAIFeatureCatalog.features.forEach { feature ->
            assertTrue(feature.id.isNotBlank())
            assertTrue(feature.title.isNotBlank())
            assertTrue(feature.description.length >= 20)
            assertTrue(feature.instruction.length >= 60)
        }
    }

    @Test
    fun catalogIncludesJobAndPortfolioReasoning() {
        val scopes = RealAIFeatureCatalog.features.groupingBy { it.scope }.eachCount()
        assertEquals(20, scopes[AIFeatureScope.FOCUS_JOB])
        assertEquals(5, scopes[AIFeatureScope.PORTFOLIO])
    }
}
