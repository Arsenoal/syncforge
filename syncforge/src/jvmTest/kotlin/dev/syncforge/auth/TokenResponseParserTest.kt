package dev.syncforge.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class TokenResponseParserTest {

    private val parser = TokenResponseParser()

    @Test
    fun parse_defaultFieldNames() {
        val tokens = parser.parse(
            """{"access_token":"a1","refresh_token":"r1","expires_in":3600}""",
            AuthTokenFieldMapping(),
        )
        assertEquals("a1", tokens.accessToken)
        assertEquals("r1", tokens.refreshToken)
        assertEquals(true, tokens.expiresAtMillis != null)
    }

    @Test
    fun parse_customFieldNames() {
        val tokens = parser.parse(
            """{"accessToken":"a2","refreshToken":"r2","expiresIn":60}""",
            AuthTokenFieldMapping(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresInSeconds = "expiresIn",
            ),
        )
        assertEquals("a2", tokens.accessToken)
        assertEquals("r2", tokens.refreshToken)
    }
}