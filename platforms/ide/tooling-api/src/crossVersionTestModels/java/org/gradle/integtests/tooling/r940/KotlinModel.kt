/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.integtests.tooling.r940

import org.gradle.tooling.Failure
import java.io.File
import java.io.Serializable
import java.util.Map
import java.util.function.Function
import java.util.stream.Collectors

internal class KotlinModel(scriptModels: MutableMap<File?, KotlinDslScriptModel?>?, failures: MutableMap<File?, Failure?>) : Serializable {
    val scriptModels: MutableMap<File?, KotlinDslScriptModel?>?
    val failures: MutableMap<File?, String?>

    init {
        this.scriptModels = scriptModels
        this.failures = failures.entries.stream().collect(Collectors.toMap(Function { Map.Entry.key }, Function { e: MutableMap.MutableEntry<File?, Failure?>? -> e!!.value.getDescription() }))
    }
}
