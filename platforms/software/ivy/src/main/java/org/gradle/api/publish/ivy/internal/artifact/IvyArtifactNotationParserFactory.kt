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
package org.gradle.api.publish.ivy.internal.artifact

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.provider.Provider
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Factory
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.MapKey
import org.gradle.internal.typeconversion.MapNotationConverter
import org.gradle.internal.typeconversion.NotationConvertResult
import org.gradle.internal.typeconversion.NotationConverter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.TypeConversionException
import org.gradle.internal.typeconversion.TypedNotationConverter
import java.io.File

class IvyArtifactNotationParserFactory(
    private val instantiator: Instantiator,
    private val fileResolver: FileResolver,
    private val publicationCoordinates: IvyPublicationCoordinates,
    private val taskDependencyFactory: TaskDependencyFactory
) : Factory<NotationParser<Any, IvyArtifact>?> {
    override fun create(): NotationParser<Any, IvyArtifact> {
        val fileNotationConverter: FileNotationConverter = IvyArtifactNotationParserFactory.FileNotationConverter(fileResolver)
        val archiveTaskNotationConverter: ArchiveTaskNotationConverter = IvyArtifactNotationParserFactory.ArchiveTaskNotationConverter()
        val publishArtifactNotationConverter: PublishArtifactNotationConverter = IvyArtifactNotationParserFactory.PublishArtifactNotationConverter()
        val providerNotationConverter: ProviderNotationConverter = IvyArtifactNotationParserFactory.ProviderNotationConverter()

        val sourceNotationParser = NotationParserBuilder
            .toType<IvyArtifact>(IvyArtifact::class.java)
            .fromType<Provider<*>>(Provider::class.java, uncheckedCast<NotationConverter<in Provider<*>, out IvyArtifact>?>(providerNotationConverter))
            .converter(archiveTaskNotationConverter)
            .converter(publishArtifactNotationConverter)
            .converter(fileNotationConverter)
            .toComposite()

        val ivyArtifactMapNotationConverter = IvyArtifactMapNotationConverter(sourceNotationParser)

        return NotationParserBuilder
            .toType<IvyArtifact>(IvyArtifact::class.java)
            .converter(archiveTaskNotationConverter)
            .converter(publishArtifactNotationConverter)
            .fromType<Provider<*>>(Provider::class.java, uncheckedCast<NotationConverter<in Provider<*>, out IvyArtifact>?>(providerNotationConverter))
            .converter(ivyArtifactMapNotationConverter)
            .converter(fileNotationConverter)
            .toComposite()
    }

    private inner class ArchiveTaskNotationConverter : TypedNotationConverter<AbstractArchiveTask, IvyArtifact>(AbstractArchiveTask::class.java) {
        override fun parseType(archiveTask: AbstractArchiveTask): IvyArtifact {
            return instantiator.newInstance<ArchiveTaskBasedIvyArtifact>(ArchiveTaskBasedIvyArtifact::class.java, archiveTask, publicationCoordinates, taskDependencyFactory)
        }
    }

    private inner class PublishArtifactNotationConverter : TypedNotationConverter<PublishArtifact, IvyArtifact>(PublishArtifact::class.java) {
        override fun parseType(publishArtifact: PublishArtifact): IvyArtifact {
            return instantiator.newInstance<PublishArtifactBasedIvyArtifact>(PublishArtifactBasedIvyArtifact::class.java, publishArtifact, publicationCoordinates, taskDependencyFactory)
        }
    }

    private inner class ProviderNotationConverter : NotationConverter<Provider<*>, IvyArtifact> {
        @Throws(TypeConversionException::class)
        override fun convert(publishArtifact: Provider<*>, result: NotationConvertResult<in IvyArtifact>) {
            val artifact: IvyArtifact = instantiator.newInstance<PublishArtifactBasedIvyArtifact>(
                PublishArtifactBasedIvyArtifact::class.java,
                LazyPublishArtifact(publishArtifact, fileResolver, taskDependencyFactory),
                publicationCoordinates,
                taskDependencyFactory
            )
            result.converted(artifact)
        }

        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Instances of Provider.")
        }
    }

    private inner class FileNotationConverter(fileResolver: FileResolver) : NotationConverter<Any, IvyArtifact> {
        private val fileResolverNotationParser: NotationParser<Any, File>

        init {
            this.fileResolverNotationParser = fileResolver.asNotationParser()
        }

        @Throws(TypeConversionException::class)
        override fun convert(notation: Any, result: NotationConvertResult<in IvyArtifact>) {
            val file = fileResolverNotationParser.parseNotation(notation)
            val ivyArtifact: IvyArtifact = instantiator.newInstance<FileBasedIvyArtifact>(FileBasedIvyArtifact::class.java, file, publicationCoordinates, taskDependencyFactory)
            if (notation is TaskDependencyContainer) {
                val taskDependencyContainer: TaskDependencyContainer?
                if (notation is Provider<*>) {
                    // wrap to disable special handling of providers by DefaultTaskDependency in this case
                    // (workaround for https://github.com/gradle/gradle/issues/11054)
                    taskDependencyContainer = TaskDependencyContainer { context: TaskDependencyResolveContext? -> context!!.add(notation) }
                } else {
                    taskDependencyContainer = notation
                }
                ivyArtifact.builtBy(taskDependencyContainer)
            }
            result.converted(ivyArtifact)
        }

        override fun describe(visitor: DiagnosticsVisitor) {
            fileResolverNotationParser.describe(visitor)
        }
    }

    private class IvyArtifactMapNotationConverter(private val sourceNotationParser: NotationParser<Any, IvyArtifact>) : MapNotationConverter<IvyArtifact>() {
        @Suppress("unused")
        protected fun parseMap(@MapKey("source") source: Any): IvyArtifact {
            return sourceNotationParser.parseNotation(source)
        }

        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Maps containing a 'source' entry").example("[source: '/path/to/file', extension: 'zip']")
        }
    }
}
