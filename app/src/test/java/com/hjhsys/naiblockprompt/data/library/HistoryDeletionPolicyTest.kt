package com.hjhsys.naiblockprompt.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryDeletionPolicyTest {
    @Test fun `history only never reports original failure`() {
        assertEquals(HistoryDeleteResult.Success, HistoryDeletionPolicy.result(false, true, false))
    }
    @Test fun `requested existing original reports success or failure`() {
        assertEquals(HistoryDeleteResult.Success, HistoryDeletionPolicy.result(true, true, true))
        assertEquals(HistoryDeleteResult.OriginalDeleteFailed, HistoryDeletionPolicy.result(true, true, false))
    }
    @Test fun `missing original is safe success`() {
        assertEquals(HistoryDeleteResult.Success, HistoryDeletionPolicy.result(true, false, false))
    }
}
