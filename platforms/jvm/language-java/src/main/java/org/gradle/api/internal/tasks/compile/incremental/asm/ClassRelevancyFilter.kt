/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.asm

import com.google.common.collect.ImmutableSet
import java.util.function.Predicate

internal class ClassRelevancyFilter(private val excludedClassName: String) : Predicate<String?> {
    override fun test(className: String): Boolean {
        return !className.startsWith("java.") && (excludedClassName != className) && !PRIMITIVES.contains(className)
    }

    companion object {
        private val PRIMITIVES: MutableSet<String?> = ImmutableSet.builder<String?>()
            .add("void")
            .add("boolean")
            .add("byte")
            .add("char")
            .add("short")
            .add("int")
            .add("long")
            .add("float")
            .add("double")
            .build()
    }
}
