package dev.syncforge.conflict

internal fun ConflictResolution<*>.toKind(): ConflictResolutionKind = when (this) {
    is ConflictResolution.KeepLocal -> ConflictResolutionKind.KEEP_LOCAL
    is ConflictResolution.AcceptRemote -> ConflictResolutionKind.ACCEPT_REMOTE
    is ConflictResolution.Merged -> ConflictResolutionKind.MERGED
    ConflictResolution.DeleteLocal -> ConflictResolutionKind.DELETE_LOCAL
}