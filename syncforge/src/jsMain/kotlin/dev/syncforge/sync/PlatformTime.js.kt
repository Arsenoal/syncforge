package dev.syncforge.sync

internal actual fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()