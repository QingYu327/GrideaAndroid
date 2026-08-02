package com.gridea.android.data.model

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val category: String,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
    val important: Boolean = false
)

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR, ACTION;

    companion object {
        fun fromString(s: String): LogLevel = values().find { it.name == s } ?: INFO
    }
}
