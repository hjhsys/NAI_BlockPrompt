package com.hjhsys.naiblockprompt.data.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogStoreTest {
    @Test
    fun redactSecrets_removesAuthorizationAndTokenValues() {
        val redacted = CrashLogStore.redactSecrets(
            "Authorization: Bearer secret-value persistent_token=another-secret harmless=value",
        )

        assertFalse(redacted.contains("secret-value"))
        assertFalse(redacted.contains("another-secret"))
        assertTrue(redacted.contains("harmless=value"))
        assertTrue(redacted.contains("<REDACTED>"))
    }
}
