package dev.syncforge.sample.notes

fun noteLocalEditBody(body: String): String = "$body (local)"

fun noteServerEditBody(body: String): String = "$body (server)"