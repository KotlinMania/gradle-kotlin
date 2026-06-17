/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.buildinit.plugins.internal

/**
 * This is used by SamplesGenerator in build logic.
 *
 * It would be better to introduce a specific API for the generator to use so that it is decouled from
 * the internals of build init infrastructure.
 */
interface CompositeProjectInitDescriptor : BuildGenerator {
    val language: Language?

    fun generateWithExternalComments(settings: InitSettings): MutableMap<String, MutableList<String>>?

    val defaultTestFramework: BuildInitTestFramework?

    val testFrameworks: MutableSet<BuildInitTestFramework>?
}
