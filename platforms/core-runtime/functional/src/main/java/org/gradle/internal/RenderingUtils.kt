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

import java.lang.String
import java.util.function.Function
import java.util.stream.Collector
import java.util.stream.Collectors
import kotlin.collections.MutableCollection
import kotlin.collections.MutableList

object RenderingUtils {
    fun quotedOxfordListOf(values: MutableCollection<String>, conjunction: String): String {
        return values.stream()
            .sorted()
            .map<String> { s: String? -> "'" + s + "'" }
            .collect(oxfordJoin(conjunction))
    }

    fun oxfordListOf(values: MutableCollection<String>, conjunction: String): String {
        return values.stream()
            .collect(oxfordJoin(conjunction))
    }

    fun oxfordJoin(conjunction: String): Collector<in String, *, String> {
        return Collectors.collectingAndThen(Collectors.toList(), Function { stringList: MutableList<String>? ->
            when (stringList!!.size) {
                0 -> return@collectingAndThen ""
                1 -> return@collectingAndThen stringList.get(0)
                2 -> return@collectingAndThen String.join(" " + conjunction + " ", stringList)
                else -> {
                    val bound = stringList.size - 1
                    return@collectingAndThen String.join(", ", stringList.subList(0, bound)) + ", " + conjunction + " " + stringList.get(bound)
                }
            }
        })
    }
}
