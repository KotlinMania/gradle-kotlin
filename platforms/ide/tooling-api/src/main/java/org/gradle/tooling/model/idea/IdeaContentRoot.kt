/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.model.idea

import java.io.File
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.UnsupportedMethodException

/**
 * Contains content root information.
 */
interface IdeaContentRoot {
    /**
     * The content root directory.
     */
    val rootDirectory: File?

    /**
     * The set of source directories.
     */
    val sourceDirectories: DomainObjectSet<out IdeaSourceDirectory?>?

    /**
     * The set of test source directories.
     */
    val testDirectories: DomainObjectSet<out IdeaSourceDirectory?>?

    @get:Throws(UnsupportedMethodException::class)
    val resourceDirectories: DomainObjectSet<out IdeaSourceDirectory?>?


    @get:Throws(UnsupportedMethodException::class)
    val testResourceDirectories: DomainObjectSet<out IdeaSourceDirectory?>?

    /**
     * The set of excluded directories.
     */
    val excludeDirectories: MutableSet<File?>?
}
