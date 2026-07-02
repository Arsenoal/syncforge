package dev.syncforge.auth

/** Platform secure (or best-effort) token persistence. */
expect fun createTokenStore(): TokenStore