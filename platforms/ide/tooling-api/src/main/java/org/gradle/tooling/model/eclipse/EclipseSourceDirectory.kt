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
package org.gradle.tooling.model.eclipse

import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.SourceDirectory
import org.gradle.tooling.model.UnsupportedMethodException

/**
 * A source directory in an Eclipse project.
 */
interface EclipseSourceDirectory : SourceDirectory, EclipseClasspathEntry {
    /**
     * Returns the relative path for this source directory.
     *
     * @return The path for this source directory. Does not return null.
     */
    val path: String?

    @get:Throws(UnsupportedMethodException::class)
    val includes: MutableList<String?>?

    @get:Throws(UnsupportedMethodException::class)
    val excludes: MutableList<String?>?

    @get:Throws(UnsupportedMethodException::class)
    val output: String?

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedMethodException For Gradle versions older than 3.0, where this method is not supported.
     *
     * @since 3.0
     */
    override val classpathAttributes: DomainObjectSet<out ClasspathAttribute?>?
}
