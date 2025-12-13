package com.sorenkai.common.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Date

// This is a minimal, general-purpose Date serializer for Ktor/Kotlinx that uses a Long (timestamp).
// It might be better to use a BSON-specific one, but let's start here.
@OptIn(ExperimentalSerializationApi::class)
object DateAsLongSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)

    override fun serialize(
        encoder: Encoder,
        value: Date,
    ) {
        encoder.encodeLong(value.time)
    }

    override fun deserialize(decoder: Decoder): Date = Date(decoder.decodeLong())
}
