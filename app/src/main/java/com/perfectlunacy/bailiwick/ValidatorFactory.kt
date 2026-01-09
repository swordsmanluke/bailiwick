package com.perfectlunacy.bailiwick

import com.perfectlunacy.bailiwick.util.GsonProvider
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

class ValidatorFactory {
    companion object {
        @JvmStatic
        fun <T>jsonValidator(clazz: Class<T>): (ByteArray) -> Boolean{
            return { data -> GsonProvider.gson.fromJson(String(data), clazz) != null }
        }

        @JvmStatic
        fun jsonValidator(): (ByteArray) -> Boolean {
            return { data -> GsonProvider.gson.newJsonReader(InputStreamReader(ByteArrayInputStream(data))).hasNext() }
        }

        fun mimeTypeValidator(mimeType: String): (ByteArray) -> Boolean {
            // TODO: Validate that data matches the expected mime type.
            return { _ -> true }
        }
    }
}