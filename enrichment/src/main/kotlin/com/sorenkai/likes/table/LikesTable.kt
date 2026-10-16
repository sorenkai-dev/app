package com.sorenkai.likes.table

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import kotlin.uuid.ExperimentalUuidApi

object LikesTable : UUIDTable("likes") {
    // Nullable FK → discussions.id
    @OptIn(ExperimentalUuidApi::class)
    val discussionId =
        uuid("discussion_id")
            .nullable()

    // Mongo-backed writing ID
    val writingId = text("writing_id").nullable()

    // Auth provider user ID (Zitadel)
    val userId = text("user_id")
}
