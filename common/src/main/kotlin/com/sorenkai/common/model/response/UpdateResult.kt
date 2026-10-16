package com.sorenkai.common.model.response

sealed class UpdateResult {
    data object Success : UpdateResult()

    data class Failure(
        val message: String,
    ) : UpdateResult()
}




