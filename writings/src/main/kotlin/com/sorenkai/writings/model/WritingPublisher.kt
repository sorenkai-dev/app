package com.sorenkai.writings.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class WritingPublisher {
    abstract val name: String
    abstract val url: String?

    @Serializable
    data class Organization(
        override val name: String,
        override val url: String? = null,
    ) : WritingPublisher()

    @Serializable
    data class Person(
        override val name: String,
        override val url: String? = null,
    ) : WritingPublisher()
}





