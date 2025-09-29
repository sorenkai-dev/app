package com.sorenkai.api.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class AuditLog(
    val id: String = com.sorenkai.api.util.newId(),
    val entityType: EntityType,
    val entityId: String,
    val action: Action,
    val userId: String,
    val changes: Map<String, String?> = emptyMap(),
    val timestamp: Instant = com.sorenkai.api.util.now(),
    val ip: String? = null
) {
    @Serializable
    enum class EntityType { STORY, DISCUSSION }
    @Serializable
    enum class Action { CREATE, EDIT, DELETE }
}
