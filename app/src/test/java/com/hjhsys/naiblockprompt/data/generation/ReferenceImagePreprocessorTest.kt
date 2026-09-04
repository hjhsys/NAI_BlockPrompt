package com.hjhsys.naiblockprompt.data.generation

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceImagePreprocessorTest {
    @Test fun `selects documented precise reference canvas by orientation`() {
        assertEquals(1536 to 1024, ReferenceImagePreprocessor.targetSize(1200, 800))
        assertEquals(1024 to 1536, ReferenceImagePreprocessor.targetSize(800, 1200))
        assertEquals(1472 to 1472, ReferenceImagePreprocessor.targetSize(900, 900))
    }
}
