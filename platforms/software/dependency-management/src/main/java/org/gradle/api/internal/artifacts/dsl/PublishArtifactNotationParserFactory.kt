/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.artifacts.publish.DecoratingPublishArtifact
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.internal.Factory
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.typeconversion.MapKey
import org.gradle.internal.typeconversion.MapNotationConverter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.TypedNotationConverter
import java.io.File
import javax.inject.Inject

@ServiceScope(Scope.Project::class)
class PublishArtifactNotationParserFactory @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val metaDataProvider: DependencyMetaDataProvider,
    private val fileResolver: FileResolver,
    private val taskDependencyFactory: TaskDependencyFactory
) : Factory<NotationParser<Any, ConfigurablePublishArtifact>?> {
    override fun create(): NotationParser<Any, ConfigurablePublishArtifact> {
        val fileConverter: FileNotationConverter = PublishArtifactNotationParserFactory.FileNotationConverter()
        return NotationParserBuilder
            .toType<ConfigurablePublishArtifact>(ConfigurablePublishArtifact::class.java)
            .converter(PublishArtifactNotationParserFactory.DecoratingConverter())
            .converter(PublishArtifactNotationParserFactory.ArchiveTaskNotationConverter())
            .converter(PublishArtifactNotationParserFactory.FileProviderNotationConverter())
            .converter(PublishArtifactNotationParserFactory.FileSystemLocationNotationConverter())
            .converter(fileConverter)
            .converter(FileMapNotationConverter(fileConverter))
            .toComposite()
    }

    private inner class DecoratingConverter : TypedNotationConverter<PublishArtifact, ConfigurablePublishArtifact>(PublishArtifact::class.java) {
        override fun parseType(notation: PublishArtifact): ConfigurablePublishArtifact {
            return objectFactory.newInstance<DecoratingPublishArtifact>(DecoratingPublishArtifact::class.java, taskDependencyFactory, notation)
        }
    }

    private inner class ArchiveTaskNotationConverter : TypedNotationConverter<AbstractArchiveTask, ConfigurablePublishArtifact>(AbstractArchiveTask::class.java) {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Instances of AbstractArchiveTask").example("jar")
        }

        override fun parseType(notation: AbstractArchiveTask): ConfigurablePublishArtifact {
            return objectFactory.newInstance<ArchivePublishArtifact>(ArchivePublishArtifact::class.java, taskDependencyFactory, notation)
        }
    }

    private class FileMapNotationConverter(private val fileConverter: FileNotationConverter) : MapNotationConverter<ConfigurablePublishArtifact>() {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Maps with 'file' key")
        }

        @Suppress("unused") // reflection
        protected fun parseMap(@MapKey("file") file: File): PublishArtifact {
            return fileConverter.parseType(file)
        }
    }

    private inner class FileProviderNotationConverter : TypedNotationConverter<Provider<*>, ConfigurablePublishArtifact>(Provider::class.java as Class<*> as Class<Provider<*>?>) {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Instances of Provider<RegularFile>.")
            visitor.candidate("Instances of Provider<Directory>.")
            visitor.candidate("Instances of Provider<File>.")
        }

        override fun parseType(notation: Provider<*>): ConfigurablePublishArtifact {
            val module = metaDataProvider.module
            return objectFactory.newInstance<DecoratingPublishArtifact>(
                DecoratingPublishArtifact::class.java,
                taskDependencyFactory,
                LazyPublishArtifact(notation, module.version, fileResolver, taskDependencyFactory)
            )
        }
    }

    private inner class FileSystemLocationNotationConverter : TypedNotationConverter<FileSystemLocation, ConfigurablePublishArtifact>(FileSystemLocation::class.java) {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Instances of RegularFile.")
            visitor.candidate("Instances of Directory.")
        }

        override fun parseType(notation: FileSystemLocation): ConfigurablePublishArtifact {
            val module = metaDataProvider.module
            return objectFactory.newInstance<DecoratingPublishArtifact>(DecoratingPublishArtifact::class.java, taskDependencyFactory, FileSystemPublishArtifact(notation, module.version))
        }
    }

    private inner class FileNotationConverter : TypedNotationConverter<File, ConfigurablePublishArtifact>(File::class.java) {
        public override fun parseType(file: File): ConfigurablePublishArtifact {
            val module = metaDataProvider.module
            val artifactFile = ArtifactFile(file, module.version)
            val defaultPublishArtifact = objectFactory.newInstance<DefaultPublishArtifact>(DefaultPublishArtifact::class.java, taskDependencyFactory)
            defaultPublishArtifact.setName(artifactFile.getName())
            defaultPublishArtifact.setExtension(artifactFile.getExtension())
            defaultPublishArtifact.setType(artifactFile.getExtension())
            defaultPublishArtifact.setClassifier(artifactFile.getClassifier())
            defaultPublishArtifact.setDate(null)
            defaultPublishArtifact.setFile(file)
            return defaultPublishArtifact
        }
    }
}
