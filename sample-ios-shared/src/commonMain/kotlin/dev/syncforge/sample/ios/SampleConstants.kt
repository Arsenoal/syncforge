package dev.syncforge.sample.ios

/** iOS Simulator → host machine running `:mock-server`. Exported for Swift. */
const val IOS_SAMPLE_DEFAULT_BASE_URL: String = "http://127.0.0.1:8080"

/** Must match BGTaskSchedulerPermittedIdentifiers in ios-sample Info.plist and AppDelegate registration. */
const val IOS_SAMPLE_BACKGROUND_SYNC_TASK_ID: String = "dev.syncforge.sample.ios.refresh"