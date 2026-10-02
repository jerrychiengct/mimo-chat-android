package com.jerry.mimochat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelContextLimitsTest {
    private fun model(id: String, length: Int) = OpenRouterModel(id, id, length, "0", "0")

    @Test fun retainedContextSurvivesEmptyCatalogueAfterRestart() {
        val saved = ModelContextLimits.toJson(listOf(model("large", 128000), model("small", 8000))).toString()
        assertEquals(128000, ModelContextLimits.resolve("large", emptyList(), JSONObject(saved)))
        assertEquals(8000, ModelContextLimits.resolve("small", emptyList(), JSONObject(saved)))
    }

    @Test fun refreshedMetadataOverridesStaleCacheWithoutLeakingBetweenModels() {
        val cache = JSONObject().put("large", 128000)
        assertEquals(64000, ModelContextLimits.resolve("large", listOf(model("large", 64000)), cache))
        assertEquals(4096, ModelContextLimits.resolve("unknown", emptyList(), cache))
    }

    @Test fun invalidMetadataFallsBackConservatively() {
        assertEquals(4096, ModelContextLimits.resolve("invalid", listOf(model("invalid", 0)), JSONObject().put("invalid", -1)))
    }
}
