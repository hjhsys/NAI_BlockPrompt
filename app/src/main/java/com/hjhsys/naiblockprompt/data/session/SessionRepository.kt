package com.hjhsys.naiblockprompt.data.session

import com.hjhsys.naiblockprompt.data.local.dao.SessionDao
import com.hjhsys.naiblockprompt.data.local.entity.CurrentSessionEntity
import com.hjhsys.naiblockprompt.data.local.entity.StashEntity
import com.hjhsys.naiblockprompt.domain.model.CURRENT_SNAPSHOT_VERSION
import com.hjhsys.naiblockprompt.domain.model.Session
import com.hjhsys.naiblockprompt.domain.model.SessionSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class SessionRepository(
    private val dao: SessionDao,
    private val json: Json,
) {
    fun observeSession(): Flow<Session> = dao.observeCurrent().map { entity ->
        entity?.let(::decode) ?: Session.empty()
    }

    suspend fun restoreOrCreate(): Session {
        val existing = dao.getCurrent()?.let(::decode)
        if (existing != null) return existing
        return Session.empty().also { save(it) }
    }

    suspend fun save(session: Session) {
        val updated = session.copy(updatedAtEpochMillis = System.currentTimeMillis())
        dao.upsert(
            CurrentSessionEntity(
                snapshotVersion = CURRENT_SNAPSHOT_VERSION,
                snapshotJson = json.encodeToString(SessionSnapshot.serializer(), SessionSnapshot(session = updated)),
                updatedAt = updated.updatedAtEpochMillis,
            ),
        )
    }

    suspend fun swapWithStash(current: Session): Session? {
        val stashed = dao.getStash()?.let { entity -> runCatching {
            require(entity.snapshotVersion == CURRENT_SNAPSHOT_VERSION)
            json.decodeFromString(SessionSnapshot.serializer(), entity.snapshotJson).session
        }.getOrNull() }
        saveStash(current)
        return stashed
    }

    suspend fun stashAndReplace(current: Session, replacement: Session) {
        saveStash(current)
        save(replacement)
    }

    suspend fun hasStash(): Boolean = dao.getStash() != null

    suspend fun restoreStash(): Session? = dao.getStash()?.let { entity -> runCatching {
        require(entity.snapshotVersion == CURRENT_SNAPSHOT_VERSION)
        json.decodeFromString(SessionSnapshot.serializer(), entity.snapshotJson).session
    }.getOrNull() }

    private suspend fun saveStash(session: Session) {
        dao.upsertStash(StashEntity(snapshotVersion = CURRENT_SNAPSHOT_VERSION, snapshotJson = json.encodeToString(SessionSnapshot.serializer(), SessionSnapshot(session = session)), updatedAt = System.currentTimeMillis()))
    }

    private fun decode(entity: CurrentSessionEntity): Session? = runCatching {
        require(entity.snapshotVersion == CURRENT_SNAPSHOT_VERSION)
        json.decodeFromString(SessionSnapshot.serializer(), entity.snapshotJson).session
    }.getOrNull()
}
