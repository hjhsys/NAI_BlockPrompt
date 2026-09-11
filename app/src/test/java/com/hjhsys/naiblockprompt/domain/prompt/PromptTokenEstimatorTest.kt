package com.hjhsys.naiblockprompt.domain.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTokenEstimatorTest {
    @Test fun wildcardAndOfficialRandomizerProduceRange() {
        val range = PromptTokenEstimator.estimate(
            "1girl, __hair__, ||smile|looking at viewer with embarrassed expression||",
            mapOf("hair" to listOf("black hair", "very long silver gradient hair")),
        )
        assertTrue(range.maximum >= range.minimum)
        assertTrue(range.hasRange)
    }

    @Test fun plainPromptProducesSingleValue() {
        val range = PromptTokenEstimator.estimate("1girl, black hair", emptyMap())
        assertEquals(range.minimum, range.maximum)
    }

    @Test fun repeatedEmptyCommasDoNotInflateEstimate() {
        assertEquals(
            PromptTokenEstimator.estimate("1girl, solo", emptyMap()),
            PromptTokenEstimator.estimate("1girl, ,,, solo", emptyMap()),
        )
    }
}
