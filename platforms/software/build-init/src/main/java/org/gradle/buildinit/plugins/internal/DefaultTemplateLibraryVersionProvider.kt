/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import java.io.IOException
import java.util.Properties

class DefaultTemplateLibraryVersionProvider : TemplateLibraryVersionProvider {
    private val libraryVersions = Properties()

    init {
        try {
            this.libraryVersions.load(javaClass.getResourceAsStream("/org/gradle/buildinit/tasks/templates/library-versions.properties"))
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    override fun getVersion(module: String): String {
        val property = checkNotNull(libraryVersions.getProperty(module)) { module + " version is not defined" }
        return property
    }
}
