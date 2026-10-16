package com.sorenkai.server.util

import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.errorHandler
import com.sorenkai.server.security.UserPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext

// This extension makes uid extraction a one-liner
suspend inline fun RoutingContext.withUser(crossinline block: suspend (uid: String) -> Unit) {
    val uid = call.principal<UserPrincipal>()?.user?.uid
    if (uid.isNullOrBlank()) {
        call.errorHandler(ErrorCode.VALIDATION_FAILED, "Missing uid or id")
    } else {
        block(uid)
    }
}
