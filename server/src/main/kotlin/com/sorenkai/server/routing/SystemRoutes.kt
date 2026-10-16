package com.sorenkai.server.routing

import io.ktor.server.routing.Route

fun Route.systemRoutes() {
    healthRoutes()
}
