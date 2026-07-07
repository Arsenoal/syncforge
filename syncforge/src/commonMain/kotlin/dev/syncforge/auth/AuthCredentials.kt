package dev.syncforge.auth

/** Wipes a password buffer after use — pair with [CharArray] login/register overloads. */
fun wipePassword(password: CharArray) {
    password.fill('\u0000')
}

internal fun authFields(
    email: String,
    password: CharArray,
    emailField: String,
    passwordField: String,
): Map<String, String> = mapOf(
    emailField to email,
    passwordField to password.concatToString(),
)