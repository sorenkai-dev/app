package com.sorenkai.core.model.common

sealed class UpdateResult {
    data object Success : UpdateResult()

    data class Failure(
        val message: String,
    ) : UpdateResult()
}
