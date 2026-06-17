/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.buildinit.plugins.internal.modifiers

enum class Language(val name: String?, private val displayName: String?, val extension: String?) {
    // These are in display order
    JAVA("Java"),
    KOTLIN("kotlin", "Kotlin", "kt"),
    GROOVY("Groovy"),
    SCALA("Scala"),
    CPP("cpp", "C++", "cpp"),
    SWIFT("swift", "Swift", "swift");

    constructor(displayName: String) : this(displayName.lowercase(), displayName, displayName.lowercase())

    override fun toString(): String {
        return displayName!!
    }
}
