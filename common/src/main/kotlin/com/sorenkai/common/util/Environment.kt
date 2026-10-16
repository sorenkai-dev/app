package com.sorenkai.common.util

fun isProdEnv(): Boolean {
    val env = System.getenv("ENV") ?: System.getProperty("ENV")
    return env?.equals("prod", ignoreCase = true) == true
}




