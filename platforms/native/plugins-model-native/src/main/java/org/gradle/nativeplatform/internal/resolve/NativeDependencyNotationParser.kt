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
package org.gradle.nativeplatform.internal.resolve

import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.typeconversion.MapKey
import org.gradle.internal.typeconversion.MapNotationConverter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.TypedNotationConverter
import org.gradle.nativeplatform.NativeLibraryRequirement
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.internal.ProjectNativeLibraryRequirement

internal object NativeDependencyNotationParser {
    fun parser(): NotationParser<Any?, NativeLibraryRequirement?>? {
        return NotationParserBuilder
            .toType<NativeLibraryRequirement?>(NativeLibraryRequirement::class.java)
            .converter(LibraryConverter())
            .converter(NativeLibraryRequirementMapNotationConverter())
            .toComposite()
    }

    private class LibraryConverter : TypedNotationConverter<NativeLibrarySpec?, NativeLibraryRequirement?>(NativeLibrarySpec::class.java) {
        override fun parseType(notation: NativeLibrarySpec): NativeLibraryRequirement? {
            return notation.getShared()
        }
    }

    private class NativeLibraryRequirementMapNotationConverter : MapNotationConverter<NativeLibraryRequirement?>() {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Map with mandatory 'library' and optional 'project' and 'linkage' keys").example("[project: ':someProj', library: 'mylib', linkage: 'static']")
        }

        @Suppress("unused")
        protected fun parseMap(
            @MapKey("library") libraryName: String?,
            @MapKey("project") projectPath: String?,
            @MapKey("linkage") linkage: String?
        ): NativeLibraryRequirement {
            return ProjectNativeLibraryRequirement(projectPath, libraryName, linkage)
        }
    }
}
