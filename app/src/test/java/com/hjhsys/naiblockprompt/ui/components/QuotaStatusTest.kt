package com.hjhsys.naiblockprompt.ui.components

import com.hjhsys.naiblockprompt.ui.SubscriptionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaStatusTest {
    @Test fun `available quota exposes both shared values`() {
        val values = SubscriptionUiState.Available(anlas = 1234, opusPercent = 87).toQuotaStatusValues()
        assertEquals(1234, values.anlas)
        assertEquals(87, values.opusPercent)
        assertFalse(values.loading)
    }

    @Test fun `loading and unavailable quota are safe display states`() {
        assertTrue(SubscriptionUiState.Loading.toQuotaStatusValues().loading)
        SubscriptionUiState.Unavailable.toQuotaStatusValues().let { values ->
            assertNull(values.anlas)
            assertNull(values.opusPercent)
            assertFalse(values.loading)
        }
    }
}
