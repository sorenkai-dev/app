package com.sorenkai.api.routes

import com.sorenkai.api.repo.StoryRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

suspend fun initRepos() {
    StoryRepository().ensureIndexes()
    com.sorenkai.api.repo.CommentRepository().ensureIndexes()
    com.sorenkai.api.repo.ReportRepository().ensureIndexes()
}

fun Application.configureRouting() {
    install(StatusPages) {
        // Domain-level API exceptions -> structured JSON with proper HTTP status
        exception<com.sorenkai.api.errors.ApiException> { call, e ->
            val body = com.sorenkai.api.dto.response.ApiErrorResponse(
                ok = false,
                error = com.sorenkai.api.dto.response.ApiErrorBody(
                    code = e.code.name,
                    message = e.message ?: e.code.name,
                    details = e.details
                )
            )
            call.respond(e.code.http, body)
        }

        // Ktor RequestValidation -> 400 BAD REQUEST with reasons
        exception<io.ktor.server.plugins.requestvalidation.RequestValidationException> { call, e ->
            val details = mapOf("reasons" to e.reasons.joinToString("; "))
            val code = com.sorenkai.api.errors.ErrorCode.VALIDATION_FAILED
            val body = com.sorenkai.api.dto.response.ApiErrorResponse(
                ok = false,
                error = com.sorenkai.api.dto.response.ApiErrorBody(
                    code = code.name,
                    message = "Validation failed",
                    details = details
                )
            )
            call.respond(code.http, body)
        }

        // Mongo duplicate key -> 409 CONFLICT
        exception<com.mongodb.MongoWriteException> { call, e ->
            val code = if (e.error.code == 11000) com.sorenkai.api.errors.ErrorCode.CONFLICT else com.sorenkai.api.errors.ErrorCode.INTERNAL
            val message = if (e.error.code == 11000) "Duplicate key" else (e.message ?: "Mongo write error")
            val body = com.sorenkai.api.dto.response.ApiErrorResponse(
                ok = false,
                error = com.sorenkai.api.dto.response.ApiErrorBody(
                    code = code.name,
                    message = message,
                    details = null
                )
            )
            call.respond(code.http, body)
        }
        exception<com.mongodb.MongoBulkWriteException> { call, e ->
            val hasDup = e.writeErrors.any { it.code == 11000 }
            val code = if (hasDup) com.sorenkai.api.errors.ErrorCode.CONFLICT else com.sorenkai.api.errors.ErrorCode.INTERNAL
            val message = if (hasDup) "Duplicate key" else (e.message ?: "Mongo bulk write error")
            val body = com.sorenkai.api.dto.response.ApiErrorResponse(
                ok = false,
                error = com.sorenkai.api.dto.response.ApiErrorBody(
                    code = code.name,
                    message = message,
                    details = null
                )
            )
            call.respond(code.http, body)
        }

        // Service-level common exceptions
        exception<com.sorenkai.api.services.StoryService.RateLimitedException> { call, e ->
            val code = com.sorenkai.api.errors.ErrorCode.RATE_LIMITED
            val body = com.sorenkai.api.dto.response.ApiErrorResponse(false, com.sorenkai.api.dto.response.ApiErrorBody(code.name, e.message ?: "Rate limited"))
            call.respond(code.http, body)
        }
        exception<com.sorenkai.api.services.CommentService.RateLimitedException> { call, e ->
            val code = com.sorenkai.api.errors.ErrorCode.RATE_LIMITED
            val body = com.sorenkai.api.dto.response.ApiErrorResponse(false, com.sorenkai.api.dto.response.ApiErrorBody(code.name, e.message ?: "Rate limited"))
            call.respond(code.http, body)
        }
        exception<com.sorenkai.api.services.ReportService.RateLimitedException> { call, e ->
            val code = com.sorenkai.api.errors.ErrorCode.RATE_LIMITED
            val body = com.sorenkai.api.dto.response.ApiErrorResponse(false, com.sorenkai.api.dto.response.ApiErrorBody(code.name, e.message ?: "Rate limited"))
            call.respond(code.http, body)
        }
        exception<com.sorenkai.api.services.CommentService.NotFoundException> { call, e ->
            val code = com.sorenkai.api.errors.ErrorCode.NOT_FOUND
            val body = com.sorenkai.api.dto.response.ApiErrorResponse(false, com.sorenkai.api.dto.response.ApiErrorBody(code.name, e.message ?: "Not found"))
            call.respond(code.http, body)
        }
        exception<com.sorenkai.api.services.CommentService.ValidationException> { call, e ->
            val code = com.sorenkai.api.errors.ErrorCode.VALIDATION_FAILED
            val body = com.sorenkai.api.dto.response.ApiErrorResponse(false, com.sorenkai.api.dto.response.ApiErrorBody(code.name, e.message ?: "Bad request"))
            call.respond(code.http, body)
        }

        // Fallback: unhandled exceptions -> 500 with generic code
        exception<Throwable> { call, cause ->
            val code = com.sorenkai.api.errors.ErrorCode.INTERNAL
            val body = com.sorenkai.api.dto.response.ApiErrorResponse(
                ok = false,
                error = com.sorenkai.api.dto.response.ApiErrorBody(
                    code = code.name,
                    message = cause.message ?: "Internal server error"
                )
            )
            call.respond(code.http, body)
        }
    }
    routing {
        get("/healthz") { call.respond(mapOf("ok" to true)) }
        storiesRoutes()
        reportsRoutes()
        adminRoutes()

        // OpenAPI and Swagger UI (enabled by default in non-prod; override with SWAGGER_ENABLED)
        val enableDocsFlag = (System.getenv("SWAGGER_ENABLED") ?: "auto").lowercase()
        val docsEnabled = when (enableDocsFlag) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> (System.getenv("ENV")?.lowercase() != "prod")
        }
        if (docsEnabled) {
            // Serve OpenAPI YAML
            get("/openapi") {
                val stream = this::class.java.classLoader.getResourceAsStream("openapi/documentation.yaml")
                if (stream == null) {
                    call.respond(HttpStatusCode.NotFound, "OpenAPI spec not found")
                } else {
                    val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    call.respondText(text, ContentType.parse("application/yaml"))
                }
            }
            // Serve a minimal Swagger UI page that points to /openapi
            get("/docs") {
                val html = """
                    <!doctype html>
                    <html>
                    <head>
                      <meta charset=\"utf-8\"/>
                      <title>SorenKai API Docs</title>
                      <link rel=\"stylesheet\" href=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui.css\" />
                    </head>
                    <body>
                      <div id=\"swagger-ui\"></div>
                      <script src=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js\"></script>
                      <script>
                        window.ui = SwaggerUIBundle({
                          url: '/openapi',
                          dom_id: '#swagger-ui',
                          presets: [SwaggerUIBundle.presets.apis],
                          layout: 'BaseLayout'
                        });
                      </script>
                    </body>
                    </html>
                """.trimIndent()
                call.respondText(html, ContentType.Text.Html)
            }
        }
    }
}
