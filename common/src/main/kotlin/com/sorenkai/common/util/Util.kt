package com.sorenkai.common.util

import org.bson.types.ObjectId

// Generate a MongoDB ObjectId hex string for use as primary keys
fun newId(): String = ObjectId().toHexString()
