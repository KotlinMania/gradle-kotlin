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
package org.gradle.api.publish.maven.internal.artifact

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenArtifact
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
import java.io.File

class MavenArtifactNotationParserFactory(private val instantiator: Instantiator, private val fileResolver: FileResolver, private val taskDependencyFactory: TaskDependencyFactory) :
    Factory<NotationParser<Any?, MavenArtifact?>?> {
    override fun create(): NotationParser<Any?, MavenArtifact?> {
        val fileNotationConverter: FileNotationConverter = MavenArtifactNotationParserFactory.FileNotationConverter(fileResolver)
        val archiveTaskNotationConverter: ArchiveTaskNotationConverter = MavenArtifactNotationParserFactory.ArchiveTaskNotationConverter()
        val publishArtifactNotationConverter: PublishArtifactNotationConverter = MavenArtifactNotationParserFactory.PublishArtifactNotationConverter()
        val providerNotationConverter: ProviderNotationConverter = MavenArtifactNotationParserFactory.ProviderNotationConverter()

        val sourceNotationParser = NotationParserBuilder
            .toType<MavenArtifact?>(MavenArtifact::class.java)
            .fromType<Provider<*>?>(Provider::class.java, uncheckedCast<NotationConverter<in Provider<*>?, out MavenArtifact?>?>(providerNotationConverter))
            .fromType<AbstractArchiveTask?>(AbstractArchiveTask::class.java, archiveTaskNotationConverter)
            .fromType<PublishArtifact?>(PublishArtifact::class.java, publishArtifactNotationConverter)
            .converter(fileNotationConverter)
            .toComposite()

        val mavenArtifactMapNotationConverter = MavenArtifactMapNotationConverter(sourceNotationParser)

        return NotationParserBuilder
            .toType<MavenArtifact?>(MavenArtifact::class.java)
            .fromType<AbstractArchiveTask?>(AbstractArchiveTask::class.java, archiveTaskNotationConverter)
            .fromType<PublishArtifact?>(PublishArtifact::class.java, publishArtifactNotationConverter)
            .fromType<Provider<*>?>(Provider::class.java, uncheckedCast<NotationConverter<in Provider<*>?, out MavenArtifact?>?>(providerNotationConverter))
            .converter(mavenArtifactMapNotationConverter)
            .converter(fileNotationConverter)
            .toComposite()
    }

    private inner class ArchiveTaskNotationConverter : NotationConverter<AbstractArchiveTask?, MavenArtifact?> {
        @Throws(TypeConversionException::class)
        override fun convert(archiveTask: AbstractArchiveTask, result: NotationConvertResult<in MavenArtifact?>) {
            val artifact: MavenArtifact = instantiator.newInstance<ArchiveTaskBasedMavenArtifact>(ArchiveTaskBasedMavenArtifact::class.java, archiveTask, taskDependencyFactory)
            result.converted(artifact)
        }

        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Instances of AbstractArchiveTask").example("jar")
        }
    }

    private inner class PublishArtifactNotationConverter : NotationConverter<PublishArtifact?, MavenArtifact?> {
        @Throws(TypeConversionException::class)
        override fun convert(publishArtifact: PublishArtifact, result: NotationConvertResult<in MavenArtifact?>) {
            val artifact: MavenArtifact = instantiator.newInstance<PublishArtifactBasedMavenArtifact>(PublishArtifactBasedMavenArtifact::class.java, publishArtifact, taskDependencyFactory)
            result.converted(artifact)
        }

        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Instances of PublishArtifact")
        }
    }

    private inner class ProviderNotationConverter : NotationConverter<Provider<*>?, MavenArtifact?> {
        @Throws(TypeConversionException::class)
        override fun convert(artifactTaskProvider: Provider<*>, result: NotationConvertResult<in MavenArtifact?>) {
            val artifact: MavenArtifact = instantiator.newInstance<PublishArtifactBasedMavenArtifact>(
                PublishArtifactBasedMavenArtifact::class.java,
                LazyPublishArtifact(artifactTaskProvider, fileResolver, taskDependencyFactory),
                taskDependencyFactory
            )
            result.converted(artifact)
        }

        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Instances of Provider")
        }
    }

    private inner class FileNotationConverter(fileResolver: FileResolver) : NotationConverter<Any?, MavenArtifact?> {
        private val fileResolverNotationParser: NotationParser<Any?, File>

        init {
            this.fileResolverNotationParser = fileResolver.asNotationParser()
        }

        @Throws(TypeConversionException::class)
        override fun convert(notation: Any?, result: NotationConvertResult<in MavenArtifact?>) {
            val file = fileResolverNotationParser.parseNotation(notation)
            val mavenArtifact: MavenArtifact = instantiator.newInstance<FileBasedMavenArtifact>(FileBasedMavenArtifact::class.java, file, taskDependencyFactory)
            if (notation is TaskDependencyContainer) {
                val taskDependencyContainer: TaskDependencyContainer?
                if (notation is Provider<*>) {
                    // wrap to disable special handling of providers by DefaultTaskDependency in this case
                    // (workaround for https://github.com/gradle/gradle/issues/11054)
                    taskDependencyContainer = TaskDependencyContainer { context: TaskDependencyResolveContext? -> context!!.add(notation) }
                } else {
                    taskDependencyContainer = notation
                }
                mavenArtifact.builtBy(taskDependencyContainer)
            }
            result.converted(mavenArtifact)
        }

        override fun describe(visitor: DiagnosticsVisitor?) {
            fileResolverNotationParser.describe(visitor)
        }
    }

    private class MavenArtifactMapNotationConverter(private val sourceNotationParser: NotationParser<Any?, MavenArtifact?>) : MapNotationConverter<MavenArtifact?>() {
        @Suppress("unused") // reflection
        protected fun parseMap(@MapKey("source") source: Any?): MavenArtifact? {
            return sourceNotationParser.parseNotation(source)
        }

        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Maps containing a 'source' entry").example("[source: '/path/to/file', extension: 'zip']")
        }
    }
}
