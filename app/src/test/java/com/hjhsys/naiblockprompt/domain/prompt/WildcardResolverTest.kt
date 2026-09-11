package com.hjhsys.naiblockprompt.domain.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardResolverTest {
    private val values = mapOf("hair" to listOf("black hair", "silver hair", "blonde hair"))

    @Test fun sameSeedProducesSameResult() {
        assertEquals(
            WildcardResolver.resolve("1girl, __hair__", values, 42L),
            WildcardResolver.resolve("1girl, __hair__", values, 42L),
        )
    }

    @Test fun unknownWildcardIsPreserved() {
        assertEquals("__missing__", WildcardResolver.resolve("__missing__", values, 1L))
    }

    @Test fun nestedWildcardResolvesAndCycleDoesNotLoop() {
        val nested = mapOf("a" to listOf("__b__"), "b" to listOf("done"), "loop" to listOf("__loop__"))
        assertEquals("done", WildcardResolver.resolve("__a__", nested, 1L))
        assertTrue(WildcardResolver.resolve("__loop__", nested, 1L).contains("__loop__"))
    }
}
