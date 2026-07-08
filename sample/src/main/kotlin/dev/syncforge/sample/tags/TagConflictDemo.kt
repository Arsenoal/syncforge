package dev.syncforge.sample.tags

/** Strips LWW demo suffixes so local/server edits target the same logical tag name. */
fun tagBaseLabel(label: String): String =
    label.removeSuffix(" (local)").removeSuffix(" (server)")

fun tagLocalEditLabel(label: String): String = "${tagBaseLabel(label)} (local)"

fun tagServerEditLabel(label: String): String = "${tagBaseLabel(label)} (server)"