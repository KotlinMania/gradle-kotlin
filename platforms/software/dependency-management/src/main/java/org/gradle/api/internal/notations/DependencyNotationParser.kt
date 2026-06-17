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
package org.gradle.api.internal.notations

import com.google.common.collect.Interner
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableMinimalDependency
import org.gradle.api.internal.artifacts.dependencies.MinimalExternalModuleDependencyInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory
import org.gradle.api.problems.Problems
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.MapNotationConverter
import org.gradle.internal.typeconversion.NotationConvertResult
import org.gradle.internal.typeconversion.NotationConverter
import org.gradle.internal.typeconversion.NotationConverterToNotationParserAdapter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.TypeConversionException

class DependencyNotationParser private constructor(
    val notationParser: NotationParser<Any?, Dependency?>?,
    val stringNotationParser: NotationParser<String?, out ExternalModuleDependency?>?,
    val minimalExternalModuleDependencyNotationParser: NotationParser<MinimalExternalModuleDependency?, out MinimalExternalModuleDependency?>?,
    val mapNotationParser: NotationParser<MutableMap<String?, *>?, out ExternalModuleDependency?>?,
    val fileCollectionNotationParser: NotationParser<FileCollection?, out FileCollectionDependency?>?,
    val projectNotationParser: NotationParser<Project?, out ProjectDependency?>?
) {
    private class MinimalExternalDependencyNotationConverter(private val instantiator: Instantiator) : NotationConverter<MinimalExternalModuleDependency?, MinimalExternalModuleDependency?> {
        @Throws(TypeConversionException::class)
        override fun convert(notation: MinimalExternalModuleDependency, result: NotationConvertResult<in MinimalExternalModuleDependency?>) {
            val moduleDependency = instantiator.newInstance<DefaultMutableMinimalDependency>(
                DefaultMutableMinimalDependency::class.java,
                notation.getModule(),
                notation.getVersionConstraint(),
                notation.getTargetConfiguration()
            )
            val internal = notation as MinimalExternalModuleDependencyInternal
            internal.copyTo(moduleDependency)
            result.converted(moduleDependency)
        }

        override fun describe(visitor: DiagnosticsVisitor?) {
        }
    }

    companion object {
        fun create(
            instantiator: Instantiator,
            dependencyFactory: DefaultProjectDependencyFactory?,
            classPathRegistry: ClassPathRegistry?,
            fileCollectionFactory: FileCollectionFactory?,
            runtimeShadedJarFactory: RuntimeShadedJarFactory?,
            stringInterner: Interner<String?>?,
            problems: Problems?
        ): DependencyNotationParser {
            val stringNotationConverter: NotationConverter<String?, out ExternalModuleDependency?> =
                DependencyStringNotationConverter<DefaultExternalModuleDependency?>(instantiator, DefaultExternalModuleDependency::class.java, stringInterner, problems)
            val minimalExternalDependencyNotationConverter: NotationConverter<MinimalExternalModuleDependency?, out MinimalExternalModuleDependency?> =
                MinimalExternalDependencyNotationConverter(instantiator)
            val mapNotationConverter: MapNotationConverter<out ExternalModuleDependency?> =
                DependencyMapNotationConverter<DefaultExternalModuleDependency?>(instantiator, DefaultExternalModuleDependency::class.java)
            val filesNotationConverter: NotationConverter<FileCollection?, out FileCollectionDependency?> =
                DependencyFilesNotationConverter(instantiator)
            val projectNotationConverter: NotationConverter<Project?, out ProjectDependency?> =
                DependencyProjectNotationConverter(dependencyFactory)
            val dependencyClassPathNotationConverter = DependencyClassPathNotationConverter(instantiator, classPathRegistry, fileCollectionFactory, runtimeShadedJarFactory)
            val notationParser = NotationParserBuilder
                .toType<Dependency?>(Dependency::class.java)
                .noImplicitConverters()
                .fromCharSequence(stringNotationConverter)
                .fromType<MinimalExternalModuleDependency?>(MinimalExternalModuleDependency::class.java, minimalExternalDependencyNotationConverter)
                .converter(mapNotationConverter)
                .fromType<FileCollection?>(FileCollection::class.java, filesNotationConverter)
                .fromType<Project?>(Project::class.java, projectNotationConverter)
                .fromType<DependencyFactoryInternal.ClassPathNotation?>(DependencyFactoryInternal.ClassPathNotation::class.java, dependencyClassPathNotationConverter)
                .invalidNotationMessage("Comprehensive documentation on dependency notations is available in DSL reference for DependencyHandler type.")
                .toComposite()
            return DependencyNotationParser(
                notationParser,
                NotationConverterToNotationParserAdapter<String?, ExternalModuleDependency?>(stringNotationConverter),
                NotationConverterToNotationParserAdapter<MinimalExternalModuleDependency?, MinimalExternalModuleDependency?>(minimalExternalDependencyNotationConverter),
                NotationConverterToNotationParserAdapter<MutableMap<String?, *>?, ExternalModuleDependency?>(mapNotationConverter),
                NotationConverterToNotationParserAdapter<FileCollection?, FileCollectionDependency?>(filesNotationConverter),
                NotationConverterToNotationParserAdapter<Project?, ProjectDependency?>(projectNotationConverter)
            )
        }
    }
}
