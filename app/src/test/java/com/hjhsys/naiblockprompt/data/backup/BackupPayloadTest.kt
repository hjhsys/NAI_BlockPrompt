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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupPayloadTest {
    private val json = Json { encodeDefaults = true }
    @get:Rule val temporaryFolder = TemporaryFolder()

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
            version = 2,
            settings = com.hjhsys.naiblockprompt.domain.model.AppSettings(), currentSession = null, stash = null,
            folders = emptyList(), blocks = emptyList(), presets = emptyList(), sets = emptyList(), history = emptyList(),
            tagData = PortableTagData(tags = emptyList(), aliases = emptyList(), overrides = emptyList(), categories = emptyList()),
            historyThumbnails = mapOf("history" to byteArrayOf(1, 2, 3)),
        )
        val restored = json.decodeFromString<AppBackupData>(json.encodeToString(payload))
        assertArrayEquals(byteArrayOf(1, 2, 3), restored.historyThumbnails.getValue("history"))
        assertEquals(BackupMediaManifest(), restored.mediaManifest)
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

    @Test fun `v3 archive stores thumbnails as files and restores them to app media directories`() {
        val historyBytes = byteArrayOf(1, 3, 5, 7)
        val tagBytes = byteArrayOf(2, 4, 6, 8)
        val historySource = temporaryFolder.newFile("history-source.img").apply { writeBytes(historyBytes) }
        val tagSource = temporaryFolder.newFile("tag-source.img").apply { writeBytes(tagBytes) }
        val manifest = BackupMediaManifest(
            historyThumbnails = mapOf("history-id" to "media/history/0.img"),
            tagThumbnails = mapOf("tag-id" to "media/tags/0.img"),
        )
        val payload = emptyAppBackup().copy(mediaManifest = manifest)

        val archive = BackupArchiveCodec.encode(
            json = json,
            metadataEntry = "backup.json",
            serializer = AppBackupData.serializer(),
            content = payload,
            media = listOf(
                BackupArchiveSource("media/history/0.img", historySource),
                BackupArchiveSource("media/tags/0.img", tagSource),
            ),
        )
        val decoded = BackupArchiveCodec.decodeMetadata(archive, "backup.json", json, AppBackupData.serializer())
        val historyDirectory = temporaryFolder.newFolder("restored-history")
        val tagDirectory = temporaryFolder.newFolder("restored-tags")
        val restored = BackupArchiveCodec.extractMedia(archive, decoded.mediaManifest, historyDirectory, tagDirectory)

        assertEquals(3, decoded.version)
        assertEquals(manifest, decoded.mediaManifest)
        assertArrayEquals(historyBytes, File(restored.historyThumbnails.getValue("history-id")).readBytes())
        assertArrayEquals(tagBytes, File(restored.tagThumbnails.getValue("tag-id")).readBytes())
        assertTrue(restored.createdFiles.all(File::isFile))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `v3 archive rejects unsafe media manifest paths`() {
        BackupArchiveCodec.extractMedia(
            bytes = byteArrayOf(),
            manifest = BackupMediaManifest(historyThumbnails = mapOf("history" to "../outside.img")),
            historyDirectory = temporaryFolder.newFolder("history"),
            tagDirectory = temporaryFolder.newFolder("tags"),
        )
    }

    private fun emptyAppBackup() = AppBackupData(
        settings = com.hjhsys.naiblockprompt.domain.model.AppSettings(),
        currentSession = null,
        stash = null,
        folders = emptyList(),
        blocks = emptyList(),
        presets = emptyList(),
        sets = emptyList(),
        history = emptyList(),
        tagData = PortableTagData(
            tags = emptyList(),
            aliases = emptyList(),
            overrides = emptyList(),
            categories = emptyList(),
        ),
    )
}
