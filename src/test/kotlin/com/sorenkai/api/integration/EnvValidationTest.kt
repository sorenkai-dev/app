package com.sorenkai.api.integration

import com.sorenkai.api.module
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EnvValidationTest {

    @Test
    fun missing_required_env_fails_startup() {
        val origEnv = System.getProperty("ENV")
        try {
            System.setProperty("ENV", "prod")
            // Ensure required props are absent
            System.clearProperty("MONGODB_URI")
            System.clearProperty("FIREBASE_PROJECT_ID")
            System.clearProperty("GOOGLE_CLOUD_PROJECT")
            System.clearProperty("APP_CHECK_ENFORCE")
            System.clearProperty("ALLOWED_ORIGINS")

            assertThrows(IllegalStateException::class.java) {
                testApplication {
                    application { module() }
                }
            }
        } finally {
            if (origEnv == null) System.clearProperty("ENV") else System.setProperty("ENV", origEnv)
        }
    }
}
