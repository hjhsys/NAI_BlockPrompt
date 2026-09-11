package com.hjhsys.naiblockprompt.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryStorageEstimatorTest {
    @Test fun `estimate uses average original size and selected history limit`() {
        assertEquals(
            25_000_000L,
            HistoryStorageEstimator.estimatedBytes(HistoryStorageSample(2_500_000L, 5), 10),
        )
    }

    @Test fun `estimate is unavailable with too few originals`() {
        assertNull(HistoryStorageEstimator.estimatedBytes(HistoryStorageSample(2_500_000L, 2), 10))
    }
}
