/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.internal.tasks

import org.gradle.api.plugins.internal.ant.AntWorkParameters
import java.io.Serializable

interface GroovydocParameters : AntWorkParameters {
    @JvmField
    val source: ConfigurableFileCollection?
    @JvmField
    val destinationDirectory: DirectoryProperty?
    @JvmField
    val use: Property<Boolean?>?
    @JvmField
    val noTimestamp: Property<Boolean?>?
    @JvmField
    val noVersionStamp: Property<Boolean?>?

    @JvmField
    val windowTitle: Property<String?>?

    @JvmField
    val docTitle: Property<String?>?

    @JvmField
    val header: Property<String?>?

    @JvmField
    val footer: Property<String?>?

    @JvmField
    val overview: Property<String?>?

    @JvmField
    val access: Property<GroovydocAccess?>?

    @JvmField
    val links: SetProperty<Link?>?

    @JvmField
    val includeAuthor: Property<Boolean?>?

    @JvmField
    val processScripts: Property<Boolean?>?

    @JvmField
    val includeMainForScripts: Property<Boolean?>?

    @JvmField
    val tmpDir: RegularFileProperty?

    class Link(
        private val packages: MutableList<String?>?,
        val url: String?
    ) : Serializable {
        fun getPackages(): Iterable<String?>? {
            return packages
        }
    }
}
