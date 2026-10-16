package com.sorenkai.server.routing

import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.core.rate.RateLimiter
import com.sorenkai.server.routing.discussions.discussionCrud
import com.sorenkai.server.routing.discussions.discussionLikes
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.communityRoutes() {
    val discussions by inject<DiscussionService>()
    val rateLimiter by inject<RateLimiter>()

    authenticate("zitadel") {
        route("/community/discussions") {
            discussionCrud(discussions, rateLimiter)
            discussionLikes(discussions)
        }
    }
}
