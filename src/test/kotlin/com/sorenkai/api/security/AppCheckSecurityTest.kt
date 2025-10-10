package com.sorenkai.api.security

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class AppCheckSecurityTest {

    @Test
    fun `forbidden when origin not allowed`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/test") {
                    if (!AppCheckSecurity.enforce(call, enforce = true, allowedOrigins = setOf("https://allowed.example"))) return@get
                    call.respond(HttpStatusCode.OK, mapOf("ok" to true))
                }
            }
        }
        val res = client.get("/test") {
            header("X-Firebase-AppCheck", "fake-token")
            header(HttpHeaders.Origin, "https://evil.example")
        }
        assertEquals(HttpStatusCode.Forbidden, res.status)
    }

    @Test
    fun `enforce false allows without headers`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/test") {
                    if (!AppCheckSecurity.enforce(call, enforce = false, allowedOrigins = emptySet())) return@get
                    call.respond(HttpStatusCode.OK, mapOf("ok" to true))
                }
            }
        }
        val res = client.get("/test")
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun `enforce true without token returns 401`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/test") {
                    if (!AppCheckSecurity.enforce(call, enforce = true, allowedOrigins = emptySet())) return@get
                    call.respond(HttpStatusCode.OK, mapOf("ok" to true))
                }
            }
        }
        val res = client.get("/test")
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `enforce true with invalid token returns 401`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/test") {
                    if (!AppCheckSecurity.enforce(call, enforce = true, allowedOrigins = setOf("https://allowed.example"))) return@get
                    call.respond(HttpStatusCode.OK, mapOf("ok" to true))
                }
            }
        }
        val res = client.get("/test") {
            header("X-Firebase-AppCheck", "fake-token")
            header(HttpHeaders.Origin, "https://allowed.example")
        }
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }
}
