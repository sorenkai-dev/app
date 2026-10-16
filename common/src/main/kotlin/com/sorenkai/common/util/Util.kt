package com.sorenkai.common.util

import java.util.UUID

// Generate a UUID string for use as primary keys
fun newId(): String = UUID.randomUUID().toString()




