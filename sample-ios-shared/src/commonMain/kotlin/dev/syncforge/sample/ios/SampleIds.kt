package dev.syncforge.sample.ios

import kotlin.random.Random

internal fun randomSampleId(): String =
    buildString {
        repeat(4) { append(Random.nextInt(0, 256).toString(16).padStart(2, '0')) }
        append('-')
        repeat(7) { append(Random.nextInt(0, 256).toString(16).padStart(2, '0')) }
    }