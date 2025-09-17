package com.sorenkai.api.dto.paging

import kotlinx.serialization.Serializable

@Serializable
data class Page<T>(
    val items: List<T>,
    val nextCursor: String? = null
)
