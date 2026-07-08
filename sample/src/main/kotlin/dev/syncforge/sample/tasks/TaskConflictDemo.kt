package dev.syncforge.sample.tasks

/** Strips gitLike demo suffixes so local/server edits target the same logical task title. */
fun taskBaseTitle(title: String): String =
    title.removeSuffix(" (local edit)").removeSuffix(" (server edit)")

fun taskLocalEditTitle(title: String): String = "${taskBaseTitle(title)} (local edit)"

fun taskServerEditTitle(title: String): String = "${taskBaseTitle(title)} (server edit)"