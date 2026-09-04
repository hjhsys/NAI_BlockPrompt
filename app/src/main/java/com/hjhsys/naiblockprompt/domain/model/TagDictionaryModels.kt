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
)

enum class TagDictionaryFilter { ALL, FAVORITES }

enum class TagDictionarySort { POPULAR, APP_USAGE, RECENT, NAME }

enum class AppTagCategory(val value: String) {
    CLOTHES("clothes"), POSE("pose"), HAIR("hair"), BODY("body"), EXPRESSION("expression"),
    ACCESSORY("accessory"), BACKGROUND("background"), COMPOSITION("composition"), LIGHTING("lighting"), EFFECT("effect"), OTHER("other"),
}
