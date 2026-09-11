package com.hjhsys.naiblockprompt.domain.model

data class TagDictionaryItem(
    val id: String,
    val canonicalTag: String,
    val danbooruCategory: String?,
    val appCategory: String?,
    val danbooruPostCount: Long?,
    val naiCount: Double?,
    val naiConfidence: Double?,
    val novelAiSource: Boolean,
    val danbooruSource: Boolean,
    val userCreated: Boolean,
    val useCount: Int,
    val lastUsedAt: Long?,
    val lastSeenAt: Long?,
    val korean: String?,
    val koreanAliases: String?,
    val englishAliases: String?,
    val favorite: Boolean,
    val thumbnailPath: String?,
    val bundled: Boolean,
)

enum class TagDictionaryFilter { ALL, FAVORITES }

enum class TagDictionarySort { POPULAR, APP_USAGE, RECENT, NAME }

enum class TagExclusionOrigin { AI, USER }

enum class TagExclusionReason(val storageValue: String) {
    TYPO("typo"), INVALID("invalid"), NOISE("noise"), USER_HIDDEN("user-hidden"),
}

data class ExcludedTagItem(
    val canonicalTag: String,
    val origin: TagExclusionOrigin,
    val reasonCode: String,
    val reasonText: String?,
    val userConfirmed: Boolean,
    val updatedAt: Long,
)

enum class AppTagCategory(val value: String) {
    CLOTHES("clothes"), POSE("pose"), HAIR("hair"), BODY("body"), EXPRESSION("expression"),
    ACCESSORY("accessory"), BACKGROUND("background"), COMPOSITION("composition"), LIGHTING("lighting"), EFFECT("effect"), OTHER("other"),
}
