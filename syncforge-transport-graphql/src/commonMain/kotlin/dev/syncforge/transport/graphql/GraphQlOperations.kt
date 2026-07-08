package dev.syncforge.transport.graphql

internal object GraphQlOperations {
    const val SYNC_PUSH = """
        mutation syncPush(${'$'}entries: [OutboxEntryInput!]!) {
          syncPush(entries: ${'$'}entries) {
            acknowledgedIds
            rejected {
              outboxId
              code
              message
            }
          }
        }
    """

    const val SYNC_PULL = """
        query syncPull(${'$'}since: Long!, ${'$'}types: [String!]!, ${'$'}limit: Int, ${'$'}cursor: String) {
          syncPull(since: ${'$'}since, types: ${'$'}types, limit: ${'$'}limit, cursor: ${'$'}cursor) {
            deltas {
              entityType
              entityId
              payloadJson
              serverVersion
              updatedAtMillis
              isDeleted
            }
            serverTimestampMillis
            hasMore
            nextPageCursor
          }
        }
    """
}