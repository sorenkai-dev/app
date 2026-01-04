package com.sorenkai.common.logging

import ch.qos.logback.classic.pattern.MessageConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * A custom Logback message converter that ensures sensitive data is not logged.
 *
 * This converter applies the [Sanitizer] utility to every log message before
 * it is written out by Logback. It intercepts the fully formatted message
 * (`ILoggingEvent.formattedMessage`) and returns a sanitized version.
 *
 * ### Purpose
 * Prevents accidental leakage of:
 * - Authorization tokens (e.g., `Bearer <token>`)
 * - JSON authorization fields
 * - Email addresses
 *
 * ### Usage
 * Register this converter in your `logback.xml` configuration so that all
 * `%msg` (message) placeholders are routed through it. For example:
 *
 * ```xml
 * <configuration>
 *   <conversionRule conversionWord="sanmsg"
 *                   converterClass="com.sorenkai.common.logging.SanitizingMessageConverter" />
 *
 *   <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
 *     <encoder>
 *       <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %sanitizeMsg%n</pattern>
 *     </encoder>
 *   </appender>
 * </configuration>
 * ```
 *
 * With this setup, all log messages will automatically be passed through
 * [Sanitizer.sanitize] before output.
 */
class SanitizingMessageConverter : MessageConverter() {
    override fun convert(event: ILoggingEvent?): String {
        val msg = event?.formattedMessage
        return Sanitizer.sanitize(msg)
    }
}
