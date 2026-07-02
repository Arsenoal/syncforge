package dev.syncforge.network

import dev.syncforge.model.SyncError

class SyncTransportException(val error: SyncError) : Exception(error.message, error.cause)