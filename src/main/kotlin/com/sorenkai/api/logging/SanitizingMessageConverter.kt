package com.sorenkai.api.logging

import ch.qos.logback.classic.pattern.MessageConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * Logback converter that applies Sanitizer to all log messages.
 */
class SanitizingMessageConverter : MessageConverter() {
    override fun convert(event: ILoggingEvent?): String {
        val msg = event?.formattedMessage
        return Sanitizer.sanitize(msg)
    }
}
