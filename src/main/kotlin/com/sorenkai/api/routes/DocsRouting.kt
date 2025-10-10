package com.sorenkai.api.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

/**
 *  Routes for service OpenAPI spec and Swagger UI.
 */
fun Routing.docsRoutes() {
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
                  <meta charset="utf-8"/>
                  <title>SorenKai API Docs</title>
                  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
                </head>
                <body>
                  <div id="swagger-ui"></div>
                  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                  <script>
                    window.ui = SwaggerUIBundle({
                      url: window.location.origin + '/openapi',
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
