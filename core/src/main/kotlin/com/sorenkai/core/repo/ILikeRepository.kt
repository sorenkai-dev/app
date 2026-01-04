package com.sorenkai.core.repo

import com.sorenkai.core.model.auth.UID

interface ILikeRepository {
    /**
     * Adds a like from userId to targetId. Should be idempotent.
     */
    suspend fun like(
        targetId: String,
        userId: UID,
    )

    /**
     * Removes a like from userId to targetId.
     */
    suspend fun unlike(
        targetId: String,
        userId: UID,
    )

    /**
     * Counts likes for a list of targetIds.
     * Returns a map of targetId to count.
     */
    suspend fun countLikes(targetIds: List<String>): Map<String, Long>

    /**
     * Checks which targets from the given list are liked by a given user.
     * Returns a set of targetIds liked by the user.
     */
    suspend fun getLikedTargets(
        targetIds: List<String>,
        userId: UID,
    ): Set<String>
}
