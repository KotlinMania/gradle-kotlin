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
package org.gradle.language.nativeplatform.internal

import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.util.internal.CollectionUtils
import java.io.File

/**
 * A convenience base class for implementing language source sets with dependencies and exported headers.
 */
abstract class AbstractHeaderExportingDependentSourceSet : AbstractHeaderExportingSourceSet(), HeaderExportingSourceSet, LanguageSourceSet, DependentSourceSetInternal {
    private val libs: MutableList<Any?> = ArrayList<Any?>()
    private var preCompiledHeader: String? = null
    private var prefixHeaderFile: File? = null

    override fun getLibs(): MutableCollection<*> {
        return libs
    }

    override fun lib(library: Any?) {
        if (library is Iterable<*>) {
            val iterable = library
            CollectionUtils.addAll(libs, iterable)
        } else {
            libs.add(library)
        }
    }

    override fun getPreCompiledHeader(): String? {
        return preCompiledHeader
    }

    override fun setPreCompiledHeader(header: String?) {
        this.preCompiledHeader = header
    }

    override fun getPrefixHeaderFile(): File? {
        return prefixHeaderFile
    }

    override fun setPrefixHeaderFile(prefixHeaderFile: File?) {
        this.prefixHeaderFile = prefixHeaderFile
    }
}
