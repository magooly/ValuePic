package com.example.valuefinder

object TagUtils {
    const val MAX_TAGS = 6
    private const val MAX_TAG_LENGTH = 50

    fun parseTags(raw: String): List<String> {
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.take(MAX_TAG_LENGTH) }  // Truncate overly long tags
            .distinctBy { it.lowercase() }
    }

    /** Normalize tags: parse, deduplicate, limit to MAX_TAGS, and rejoin */
    fun normalizeTags(raw: String): String {
        return parseTags(raw)
            .take(MAX_TAGS)
            .joinToString(", ")
    }

    /**
     * Normalize user input while preserving a trailing comma+space so the user
     * can type "tag1, tag2, ..." naturally without the cursor jumping.
     */
    fun normalizeTagsInput(raw: String): String {
        val trimmedEnd = raw.trimEnd(' ')
        val hasTrailingComma = trimmedEnd.endsWith(',')
        val normalized = normalizeTags(raw)
        return if (hasTrailingComma && normalized.isNotEmpty()) "$normalized, " else normalized
    }

    fun hasTag(rawTags: String, selectedTag: String): Boolean {
        if (selectedTag.isBlank()) return true
        val target = selectedTag.trim().lowercase()
        return parseTags(rawTags).any { it.lowercase() == target }
    }

    fun renameTag(rawTags: String, oldTag: String, newTag: String): String {
        val oldClean = oldTag.trim()
        val newClean = newTag.trim()
        if (oldClean.isBlank() || newClean.isBlank()) return normalizeTags(rawTags)
        return parseTags(rawTags)
            .map { if (it.equals(oldClean, ignoreCase = true)) newClean else it }
            .take(MAX_TAGS)
            .joinToString(", ")
    }

    fun removeTag(rawTags: String, tagToRemove: String): String {
        val cleanTag = tagToRemove.trim()
        if (cleanTag.isBlank()) return normalizeTags(rawTags)
        return parseTags(rawTags)
            .filterNot { it.equals(cleanTag, ignoreCase = true) }
            .take(MAX_TAGS)
            .joinToString(", ")
    }
}

