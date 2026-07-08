package dev.syncforge.auth

actual fun createTokenStore(): TokenStore = InMemoryTokenStore()