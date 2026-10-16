package com.sorenkai.writings.service

import com.sorenkai.writings.model.Writing
import com.sorenkai.writings.model.WritingPublisher
import com.sorenkai.writings.model.WritingsType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object WritingStructuredDataFactory {
    fun generate(
        writing: Writing,
        publisher: WritingPublisher? = null,
    ): JsonObject {
        val json = mutableMapOf<String, JsonElement>()
        json["@context"] = JsonPrimitive("https://schema.org")

        val type =
            when (writing.writingsType) {
                WritingsType.BOOK -> "Book"
                else -> "Article"
            }
        json["@type"] = JsonPrimitive(type)

        val url = "${writing.language}/writing?id=${writing.id}"
        json["@id"] = JsonPrimitive(url)
        json["url"] = JsonPrimitive(url)

        json["headline"] = JsonPrimitive(writing.title)
        writing.seoDescription?.let { json["description"] = JsonPrimitive(it) }
        json["inLanguage"] = JsonPrimitive(writing.language)

        json["author"] =
            buildJsonObject {
                put("@type", "Person")
                put("name", "Soren Kai")
            }

        publisher?.let {
            json["publisher"] = publisherToJson(it)
        }

        json["dateCreated"] = JsonPrimitive(writing.createdAt.toString())
        val publishedAt = writing.publishedAt ?: writing.createdAt
        json["datePublished"] = JsonPrimitive(publishedAt.toString())

        writing.coverUrl?.let {
            json["image"] = JsonPrimitive(it)
        }

        if (writing.writingsType == WritingsType.BOOK) {
            val offers = mutableMapOf<String, JsonElement>()
            offers["@type"] = JsonPrimitive("Offer")
            writing.salesLink?.let { offers["url"] = JsonPrimitive(it) }
            json["offers"] = JsonObject(offers)
        }

        return JsonObject(json)
    }

    fun updateJsonLdOffers(
        existingJsonLd: JsonObject,
        salesLink: String?,
        isbn: String?,
    ): JsonObject {
        val map = existingJsonLd.toMutableMap()

        // Update offers
        val offers = map["offers"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        offers["@type"] = JsonPrimitive("Offer")
        if (salesLink != null) {
            offers["url"] = JsonPrimitive(salesLink)
        } else {
            offers.remove("url")
        }

        // Update isbn inside offers
        if (isbn != null) {
            offers["isbn"] = JsonPrimitive(isbn)
        } else {
            offers.remove("isbn")
        }

        map["offers"] = JsonObject(offers)

        // Remove top-level isbn if it exists from previous version
        map.remove("isbn")

        return JsonObject(map)
    }

    fun updateJsonLdImage(
        existingJsonLd: JsonObject,
        coverUrl: String?,
    ): JsonObject {
        val map = existingJsonLd.toMutableMap()
        if (coverUrl != null) {
            map["image"] = JsonPrimitive(coverUrl)
        } else {
            map.remove("image")
        }
        return JsonObject(map)
    }

    fun updateJsonLdPublisher(
        existingJsonLd: JsonObject,
        publisher: WritingPublisher,
    ): JsonObject {
        val map = existingJsonLd.toMutableMap()
        map["publisher"] = publisherToJson(publisher)
        return JsonObject(map)
    }

    private fun publisherToJson(publisher: WritingPublisher): JsonObject =
        buildJsonObject {
            put("@type", if (publisher is WritingPublisher.Organization) "Organization" else "Person")
            put("name", publisher.name)
            publisher.url?.let { put("url", it) }
        }
}




