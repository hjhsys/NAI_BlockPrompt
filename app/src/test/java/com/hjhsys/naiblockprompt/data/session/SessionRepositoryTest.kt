package com.hjhsys.naiblockprompt.data.session

import com.hjhsys.naiblockprompt.data.local.dao.SessionDao
import com.hjhsys.naiblockprompt.data.local.entity.CurrentSessionEntity
import com.hjhsys.naiblockprompt.data.local.entity.StashEntity
import com.hjhsys.naiblockprompt.domain.model.PromptBlock
import com.hjhsys.naiblockprompt.domain.model.PromptPair
import com.hjhsys.naiblockprompt.domain.model.Session
import com.hjhsys.naiblockprompt.domain.model.SessionSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRepositoryTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Test
    fun `stash and replace stores current A over previous X before applying B`() = runTest {
        val dao = FakeSessionDao(json)
        val repository = SessionRepository(dao, json)
        val previous = sessionNamed("X")
        val current = sessionNamed("A")
        val replacement = sessionNamed("B")

        repository.stashAndReplace(previous, current)
        repository.stashAndReplace(current, replacement)

        assertEquals("B", decode(dao.current!!.snapshotJson).base.prompts.positiveBlocks.single().content)
        assertEquals("A", decode(dao.stash!!.snapshotJson).base.prompts.positiveBlocks.single().content)
        assertEquals(listOf("stash:A", "current:B"), dao.operations.takeLast(2))
    }

    private fun sessionNamed(name: String) = Session.empty().copy(
        base = Session.empty().base.copy(
            prompts = PromptPair(positiveBlocks = listOf(PromptBlock(name = name, content = name))),
        ),
    )

    private fun decode(value: String): Session = json.decodeFromString<SessionSnapshot>(value).session

    private class FakeSessionDao(private val json: Json) : SessionDao {
        var current: CurrentSessionEntity? = null
        var stash: StashEntity? = null
        val operations = mutableListOf<String>()
        private val currentFlow = MutableStateFlow<CurrentSessionEntity?>(null)

        override fun observeCurrent(): Flow<CurrentSessionEntity?> = currentFlow
        override suspend fun getCurrent(): CurrentSessionEntity? = current
        override suspend fun upsert(entity: CurrentSessionEntity) {
            current = entity
            currentFlow.value = entity
            operations += "current:${content(entity.snapshotJson)}"
        }
        override suspend fun upsertStash(entity: StashEntity) {
            stash = entity
            operations += "stash:${content(entity.snapshotJson)}"
        }
        override suspend fun getStash(): StashEntity? = stash
        override suspend fun clearStash() { stash = null }

        private fun content(value: String): String =
            json.decodeFromString<SessionSnapshot>(value).session.base.prompts.positiveBlocks.single().content
    }
}
