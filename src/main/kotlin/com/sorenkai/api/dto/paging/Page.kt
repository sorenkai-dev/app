package com.sorenkai.api.dto.paging

import kotlinx.serialization.Serializable

/**
 * Generic page of results for cursor-based pagination.
 *
 * @param T The type of items contained in the page.
 * @property items The list of items in this page.
 * @property nextCursor An encoded [Cursor] string (Base64 URL-safe) representing
 * the position after the last item. Pass this back as `?cursor=` on the next
 * request to resume pagination. `null` means there are no further results.
 *
 * ### Example
 * ```kotlin
 * val page: Page<Discussion> = service.listRoot(limit = 10, cursor = null)
 * println(page.items.size)        // up to 10 items
 * println(page.nextCursor)        // may be null if no more data
 * ```
 *
 * ### Usage in APIs
 * - Returned from list endpoints such as `/v1/discussions`
 * - The client stores `nextCursor` and provides it back on the next request.
 * - This avoids numeric offsets and ensures stable iteration under concurrent writes.
 */
@Serializable
data class Page<T>(
    val items: List<T>,
    val nextCursor: String? = null
)
