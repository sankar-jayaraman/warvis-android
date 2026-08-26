package com.warvis.android.data.model

enum class BacklogBucketId(val displayName: String) {
    CAREER("Career"),
    FITNESS("Fitness"),
    THAGUDU("Thagudu"),
}

data class BacklogItem(
    val id: String,
    val bucketId: BacklogBucketId,
    val title: String,
)

data class BacklogBucket(
    val id: BacklogBucketId,
    val items: List<BacklogItem>,
) {
    val displayName: String get() = id.displayName
}

data class BacklogState(
    val buckets: List<BacklogBucket>,
    val currentBucketIndex: Int,
)
