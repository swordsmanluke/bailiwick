package com.perfectlunacy.bailiwick

import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

class ValidatorFactory {
    companion object {
        @JvmStatic
        fun <T>jsonValidator(clazz: Class<T>): (ByteArray) -> Boolean{
            return { data -> Gson().fromJson(String(data), clazz) != null }
        }

        @JvmStatic
        fun jsonValidator(): (ByteArray) -> Boolean {
            return { data -> Gson().newJsonReader(InputStreamReader(ByteArrayInputStream(data))).hasNext() }
        }
    }
}