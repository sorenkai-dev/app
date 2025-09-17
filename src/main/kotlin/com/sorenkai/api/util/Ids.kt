package com.sorenkai.api.util

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
// If you prefer compact IDs (no hyphens):
// fun newCompactId(): String = UUID.randomUUID().toString().replace("-", "")
