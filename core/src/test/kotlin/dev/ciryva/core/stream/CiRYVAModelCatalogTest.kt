package dev.ciryva.core.stream

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CiRYVAModelCatalogTest {
    @Test fun `default low cost Luna is an actual standard-group API model`() {
        val model = CiRYVAModelCatalog.find("gpt-6-luna")
        assertNotNull(model)
        assertEquals(IncentiveGroup.STANDARD, model!!.incentiveGroup)
        assertTrue("none" in model.reasoningEfforts)
        assertTrue("max" in model.reasoningEfforts)
        assertTrue(model.supportsVerbosity)
    }

    @Test fun `mini incentive group is not conflated with cheap standard Luna`() {
        assertEquals(IncentiveGroup.SMALL,
            CiRYVAModelCatalog.find("gpt-5.4-mini-2026-03-17")?.incentiveGroup)
        assertEquals(IncentiveGroup.SMALL,
            CiRYVAModelCatalog.find("gpt-5.6-luna")?.incentiveGroup)
        assertEquals(IncentiveGroup.STANDARD,
            CiRYVAModelCatalog.find("gpt-6-luna")?.incentiveGroup)
        assertEquals(IncentiveGroup.STANDARD,
            CiRYVAModelCatalog.find("o3-2025-04-16")?.incentiveGroup)
    }

    @Test fun `retired o1 and o4 mini are never selectable`() {
        for (model in listOf("o1-2024-12-17", "o4-mini-2025-04-16")) {
            assertTrue(CiRYVAModelCatalog.find(model)?.retired == true)
            assertFalse(CiRYVAModelCatalog.selectable.any { it.id == model })
            assertThrows(IllegalArgumentException::class.java) {
                GenerationSettings(modelId = model)
            }
        }
    }

    @Test fun `legacy GPT4 supports sampling and rejects modern reasoning controls`() {
        assertNotNull(GenerationSettings("gpt-4o-2024-11-20", temperature = 0.7))
        assertNotNull(GenerationSettings("gpt-4o-mini-2024-07-18", topP = 0.9))
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("gpt-4o-2024-11-20", reasoningEffort = "medium")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("gpt-4o-mini-2024-07-18", verbosity = "high")
        }
    }

    @Test fun `legacy o3 only accepts supported reasoning effort`() {
        assertNotNull(GenerationSettings("o3-2025-04-16", reasoningEffort = "high"))
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("o3-2025-04-16", reasoningEffort = "none")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("o3-2025-04-16", temperature = 0.3)
        }
    }

    @Test fun `GPT6 no reasoning unlocks temperature while reasoning rejects it`() {
        assertNotNull(GenerationSettings("gpt-6-luna",
            reasoningEffort = "none", temperature = 0.3))
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("gpt-6-luna", reasoningEffort = "medium", temperature = 0.3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("gpt-6.1-sol", reasoningEffort = "none")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("gpt-6-astra", reasoningEffort = "minimal")
        }
    }

    @Test fun `invalid numeric values and incompatible sampling settings fail safely`() {
        for (v in listOf(-0.1, 2.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                GenerationSettings("gpt-4o-2024-11-20", temperature = v)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("gpt-4o-2024-11-20", temperature = 0.6, topP = 0.8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("gpt-4o-2024-11-20", topP = 0.0)
        }
    }

    @Test fun `verbosity and summary are capability validated`() {
        assertNotNull(GenerationSettings("gpt-6-luna",
            reasoningEffort = "low", reasoningSummary = "concise", verbosity = "high"))
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("gpt-6-luna", verbosity = "ultra")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("gpt-4o-2024-11-20", reasoningSummary = "detailed")
        }
    }

    @Test fun `legacy o3 summary modes exclude concise but retain detailed`() {
        assertNotNull(GenerationSettings("o3-2025-04-16", reasoningSummary = "auto"))
        assertNotNull(GenerationSettings("o3-2025-04-16", reasoningSummary = "detailed"))
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("o3-2025-04-16", reasoningSummary = "concise")
        }
        assertNotNull(GenerationSettings("gpt-6-luna", reasoningSummary = "concise"))
    }

    @Test fun `unknown model ID is never silently accepted as a configured profile`() {
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings("not-a-real-model", reasoningEffort = "high")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TextResponseRequest("gpt-6-luna", "hello",
                settings = GenerationSettings("gpt-5.4-mini-2026-03-17"))
        }
    }
}
