package com.sorenkai.api.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger

private val securityLog = KtorSimpleLogger("Security")

fun Application.configureSecurity() {
    ensureFirebase()
    val internalLog = securityLog
    install(Authentication) {
        // Bearer auth, we verify token via Firebase Admin SDK
        bearer("firebase") {
            authenticate { tokenCredential ->
                val token = tokenCredential.token
                try {
                    val decoded = FirebaseAuth.getInstance().verifyIdToken(token, /*checkRevoked=*/true)
                    // Optionally enforce custom claims here:
                    // val claims = decoded.claims
                    // val accepted = claims["acceptedPolicies"] as? Boolean ?: false
                    // if (!accepted) return@authenticate null

                    UserIdPrincipal(decoded.uid)
                } catch (e: Exception) {
                    internalLog.warn("Firebase token verification failed: ${e.message}")
                    null
                }
            }
        }
    }

    // Simple health route (unauthenticated)
    routing {
        get("/healthz") {
            call.respondText("ok", ContentType.Text.Plain)
        }

        // Example protected route:
        authenticate("firebase") {
            get("/me") {
                val principal = call.principal<UserIdPrincipal>()!!
                call.respond(HttpStatusCode.OK, mapOf("uid" to principal.name))
            }
        }
    }
}

/**
 * Initialize Firebase Admin using GOOGLE_APPLICATION_CREDENTIALS if not already initialized.
 */
private fun ensureFirebase() {
    if (FirebaseApp.getApps().isNotEmpty()) return

    val credsPathOrJson = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        ?: error("GOOGLE_APPLICATION_CREDENTIALS env var is required")

    val credentials = when {
        // Allow inline JSON (starts with '{') or path to file or directory containing the file
        credsPathOrJson.trim().startsWith("{") -> {
            GoogleCredentials.fromStream(credsPathOrJson.byteInputStream())
        }
        else -> {
            val path = credsPathOrJson.trim()
            val file = java.io.File(path)
            require(file.exists()) { "Service account path not found at $path" }

            val targetFile = when {
                file.isFile -> file
                file.isDirectory -> {
                    // Prefer a conventional name, else any .json, else any regular file
                    val named = java.io.File(file, "firebase-service-account.json")
                    if (named.isFile) named else {
                        val files = file.listFiles()?.toList().orEmpty()
                        files.firstOrNull { it.isFile && it.name.lowercase().endsWith(".json") }
                            ?: files.firstOrNull { it.isFile }
                            ?: error("No credential file found in directory $path")
                    }
                }
                else -> error("Invalid credentials path: $path")
            }

            targetFile.inputStream().use { GoogleCredentials.fromStream(it) }
        }
    }

    val projectId = System.getenv("FIREBASE_PROJECT_ID")
        ?: error("FIREBASE_PROJECT_ID env var is required")

    val options = FirebaseOptions.builder()
        .setCredentials(credentials)
        .setProjectId(projectId)
        .build()

    FirebaseApp.initializeApp(options)
    securityLog.info("Firebase initialized for project '$projectId'")
}
