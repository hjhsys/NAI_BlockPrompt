package com.hjhsys.naiblockprompt.ui

import com.hjhsys.naiblockprompt.data.generation.GenerationRecord
import com.hjhsys.naiblockprompt.data.network.nai.NaiApiFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationResultRetentionTest {
    private val resultA = GenerationRecord("A.png", "A-thumb.png", 1L)
    private val resultB = GenerationRecord("B.png", "B-thumb.png", 2L)

    @Test
    fun `loading and failure retain the last successful result`() {
        assertEquals(resultA, GenerationResultRetention.next(resultA, GenerationUiState.Loading))
        assertEquals(resultA, GenerationResultRetention.next(resultA, GenerationUiState.Failed(NaiApiFailure.Network("offline"))))
    }

    @Test
    fun `only a successful response replaces the retained result`() {
        assertEquals(resultB, GenerationResultRetention.next(resultA, GenerationUiState.Success(resultB)))
        assertNull(GenerationResultRetention.next(null, GenerationUiState.Loading))
    }
}
