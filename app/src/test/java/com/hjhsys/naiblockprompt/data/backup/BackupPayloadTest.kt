package com.hjhsys.naiblockprompt.data.backup

import com.hjhsys.naiblockprompt.data.local.entity.TagEntity
import com.hjhsys.naiblockprompt.data.local.entity.UserTagOverrideEntity
import com.hjhsys.naiblockprompt.data.local.entity.TagExclusionEntity
import com.hjhsys.naiblockprompt.data.local.entity.HistoryEntryEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupPayloadTest {
    private val json = Json { encodeDefaults = true }

    @Test fun `portable tag payload preserves accumulated sources and overrides`() {
        val tag = TagEntity(
            id = "tag", canonicalTag = "sample", danbooruCategory = null, appCategory = null,
            legacyPostCount = null, danbooruPostCount = null, naiCount = 1.0, naiConfidence = .9,
            novelAiSource = true, danbooruSource = true, userCreated = true, lastSeenAt = 1,
            bundled = false,
        )
        val override = UserTagOverrideEntity("override", "tag", "샘플", "예시", "other", true, null, 2)
        val exclusion = TagExclusionEntity("hidden_tag", "AI", "typo", "Likely misspelling", true, 3, 4)
        val payload = PortableTagData(tags = listOf(tag), aliases = emptyList(), overrides = listOf(override), categories = emptyList(), exclusions = listOf(exclusion))
        val restored = json.decodeFromString<PortableTagData>(json.encodeToString(payload))
        assertEquals(payload, restored)
    }

    @Test fun `legacy portable tag payload defaults to no exclusions`() {
        val restored = json.decodeFromString<PortableTagData>("""{"version":1,"tags":[],"aliases":[],"overrides":[],"categories":[]}""")
        assertEquals(emptyList<TagExclusionEntity>(), restored.exclusions)
    }

    @Test fun `legacy app backup payload still reads thumbnail bytes`() {
        val payload = AppBackupData(
            settings = com.hjhsys.naiblockprompt.domain.model.AppSettings(), currentSession = null, stash = null,
            folders = emptyList(), blocks = emptyList(), presets = emptyList(), sets = emptyList(), history = emptyList(),
            tagData = PortableTagData(tags = emptyList(), aliases = emptyList(), overrides = emptyList(), categories = emptyList()),
            historyThumbnails = mapOf("history" to byteArrayOf(1, 2, 3)),
        )
        val restored = json.decodeFromString<AppBackupData>(json.encodeToString(payload))
        assertArrayEquals(byteArrayOf(1, 2, 3), restored.historyThumbnails.getValue("history"))
    }

    @Test fun `metadata only app backup preserves favorite history without media`() {
        val history = HistoryEntryEntity(
            id = "favorite", createdAt = 1, imagePath = "", thumbnailPath = "", model = "nai-diffusion-4-5-full",
            snapshotVersion = 1, snapshotJson = "{\"snapshotVersion\":1}", favorite = true,
        )
        val payload = AppBackupData(
            settings = com.hjhsys.naiblockprompt.domain.model.AppSettings(), currentSession = null, stash = null,
            folders = emptyList(), blocks = emptyList(), presets = emptyList(), sets = emptyList(), history = listOf(history),
            tagData = PortableTagData(tags = emptyList(), aliases = emptyList(), overrides = emptyList(), categories = emptyList()),
        )

        val restored = json.decodeFromString<AppBackupData>(json.encodeToString(payload))

        assertEquals(history, restored.history.single())
        assertFalse(restored.historyThumbnails.isNotEmpty())
        assertFalse(restored.tagThumbnails.isNotEmpty())
    }
}
