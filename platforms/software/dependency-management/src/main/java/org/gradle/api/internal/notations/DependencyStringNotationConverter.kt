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
package org.gradle.api.internal.notations

import com.google.common.collect.Interner
import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.internal.artifacts.dsl.ParsedModuleStringNotation
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper
import org.gradle.api.internal.catalog.parser.StrictVersionParser
import org.gradle.api.problems.Problems
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationConvertResult
import org.gradle.internal.typeconversion.NotationConverter
import org.gradle.internal.typeconversion.TypeConversionException

class DependencyStringNotationConverter<T>(private val instantiator: Instantiator, private val wantedType: Class<T?>, private val stringInterner: Interner<String?>, problems: Problems?) :
    NotationConverter<String?, T?> {
    private val strictVersionParser: StrictVersionParser

    init {
        this.strictVersionParser = StrictVersionParser(stringInterner, problems)
    }

    override fun describe(visitor: DiagnosticsVisitor) {
        visitor.candidate("String or CharSequence values").example("'org.gradle:gradle-core:1.0'")
    }

    @Throws(TypeConversionException::class)
    override fun convert(notation: String, result: NotationConvertResult<in T?>) {
        result.converted(createDependencyFromString(notation))
    }

    private fun createDependencyFromString(notation: String): T? {
        val parsedNotation = splitModuleFromExtension(notation)
        val version = strictVersionParser.parse(parsedNotation.getVersion())
        val moduleDependency = instantiator.newInstance<T?>(wantedType, intern(parsedNotation.getGroup()), intern(parsedNotation.getName()), intern(version.require))
        maybeEnrichVersion(version, moduleDependency)
        if (moduleDependency is ExternalDependency) {
            ModuleFactoryHelper.addExplicitArtifactsIfDefined(moduleDependency as ExternalDependency, parsedNotation.artifactType, parsedNotation.getClassifier())
        }

        return moduleDependency
    }

    private fun intern(sample: String?): String? {
        if (sample == null) {
            return null
        }
        return stringInterner.intern(sample)
    }

    private fun maybeEnrichVersion(version: StrictVersionParser.RichVersion, moduleDependency: T?) {
        if (version.strictly != null) {
            val versionAction = Action { v: MutableVersionConstraint? ->
                v!!.strictly(version.strictly)
                if (!version.prefer.isEmpty()) {
                    v.prefer(version.prefer)
                }
            }
            if (moduleDependency is ExternalDependency) {
                (moduleDependency as ExternalDependency).version(versionAction)
            }
            if (moduleDependency is DependencyConstraint) {
                (moduleDependency as DependencyConstraint).version(versionAction)
            }
        }
    }

    @Suppress("deprecation")
    private fun splitModuleFromExtension(notation: String): ParsedModuleStringNotation {
        val idx = notation.lastIndexOf('@')
        if (idx == -1) {
            return ParsedModuleStringNotation(notation, null)
        }
        val versionIndx = notation.lastIndexOf(':')
        if (versionIndx < idx) {
            return ParsedModuleStringNotation(notation.substring(0, idx), notation.substring(idx + 1))
        }
        return ParsedModuleStringNotation(notation, null)
    }
}
