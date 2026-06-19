/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal

import java.util.function.Function
import java.util.stream.Collector
import java.util.stream.Collectors

object RenderingUtils {
    @JvmStatic
    fun quotedOxfordListOf(values: MutableCollection<String>, conjunction: String): String {
        return values.stream()
            .sorted()
            .map<String> { s: String? -> "'" + s + "'" }
            .collect(oxfordJoin(conjunction))
    }

    @JvmStatic
    fun oxfordListOf(values: MutableCollection<String>, conjunction: String): String {
        return values.stream()
            .collect(oxfordJoin(conjunction))
    }

    @JvmStatic
    fun oxfordJoin(conjunction: String): Collector<in String, *, String> {
        return Collectors.collectingAndThen(Collectors.toList(), Function { stringList: MutableList<String>? ->
            val list = stringList ?: return@Function("")
            when (list.size) {
                0 -> ""
                1 -> list[0]
                2 -> java.lang.String.join(" " + conjunction + " ", list)
                else -> {
                    val bound = list.size - 1
                    return@Function java.lang.String.join(", ", list.subList(0, bound)) + ", " + conjunction + " " + list[bound]
                }
            }
        })
    }
}
