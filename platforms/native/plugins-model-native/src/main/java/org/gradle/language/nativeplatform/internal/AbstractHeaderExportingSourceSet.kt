/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.nativeplatform.internal

import org.gradle.api.file.SourceDirectorySet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.sources.BaseLanguageSourceSet
import org.gradle.language.nativeplatform.HeaderExportingSourceSet

/**
 * A convenience base class for implementing language source sets with dependencies and exported headers.
 */
abstract class AbstractHeaderExportingSourceSet : BaseLanguageSourceSet(), HeaderExportingSourceSet, LanguageSourceSet {
    private val exportedHeaders: SourceDirectorySet
    private val implicitHeaders: SourceDirectorySet

    init {
        this.exportedHeaders = objectFactory.sourceDirectorySet("exported", "exported headers")
        this.implicitHeaders = objectFactory.sourceDirectorySet("implicit", "implicit headers")
    }

    override fun getExportedHeaders(): SourceDirectorySet {
        return exportedHeaders
    }

    override fun getImplicitHeaders(): SourceDirectorySet {
        return implicitHeaders
    }
}
