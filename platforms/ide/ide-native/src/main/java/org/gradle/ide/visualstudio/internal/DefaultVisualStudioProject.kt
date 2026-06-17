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
package org.gradle.ide.visualstudio.internal

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskDependency
import org.gradle.ide.visualstudio.XmlConfigFile
import org.gradle.internal.file.PathToFileResolver
import org.gradle.plugins.ide.internal.IdeProjectMetadata
import org.gradle.util.internal.CollectionUtils.toList
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.util.UUID
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * A VisualStudio project represents a set of binaries for a component that may vary in build type and target platform.
 */
class DefaultVisualStudioProject(private val name: String?, private val componentName: String?, fileResolver: PathToFileResolver, objectFactory: ObjectFactory, providerFactory: ProviderFactory) :
    VisualStudioProjectInternal {
    private val projectFile: DefaultConfigFile
    private val filtersFile: DefaultConfigFile
    val visualStudioVersion: Property<VersionNumber?>
    val sdkVersion: Property<VersionNumber?>
    private val additionalFiles: MutableList<File?> = ArrayList<File?>()
    private val configurations: MutableMap<VisualStudioTargetBinary, VisualStudioProjectConfiguration?> = LinkedHashMap<VisualStudioTargetBinary, VisualStudioProjectConfiguration?>()
    private val buildDependencies = DefaultTaskDependency()
    val sourceFiles: ConfigurableFileCollection
    val headerFiles: ConfigurableFileCollection

    init {
        this.visualStudioVersion = objectFactory.property<VersionNumber?>(VersionNumber::class.java).convention(AbstractCppBinaryVisualStudioTargetBinary.Companion.DEFAULT_VISUAL_STUDIO_VERSION)
        this.sdkVersion = objectFactory.property<VersionNumber?>(VersionNumber::class.java).convention(AbstractCppBinaryVisualStudioTargetBinary.Companion.DEFAULT_SDK_VERSION)
        this.projectFile = objectFactory.newInstance<DefaultConfigFile>(DefaultConfigFile::class.java, fileResolver, getName() + ".vcxproj")
        this.filtersFile = objectFactory.newInstance<DefaultConfigFile>(DefaultConfigFile::class.java, fileResolver, getName() + ".vcxproj.filters")
        this.sourceFiles = objectFactory.fileCollection().from(providerFactory.provider<MutableSet<File?>?>(Callable {
            val allSourcesFromBinaries: MutableSet<File?> = LinkedHashSet<File?>()
            for (binary in configurations.keys) {
                allSourcesFromBinaries.addAll(binary.getSourceFiles().getFiles())
            }
            allSourcesFromBinaries
        }), providerFactory.provider<MutableList<File?>?>(Callable { additionalFiles }))
        this.headerFiles = objectFactory.fileCollection().from(providerFactory.provider<MutableSet<File?>?>(Callable {
            val allHeadersFromBinaries: MutableSet<File?> = LinkedHashSet<File?>()
            for (binary in configurations.keys) {
                allHeadersFromBinaries.addAll(binary.getHeaderFiles().getFiles())
            }
            allHeadersFromBinaries
        }))
    }

    override fun getComponentName(): String? {
        return componentName
    }

    override fun getProjectFile(): DefaultConfigFile {
        return projectFile
    }

    override fun getFiltersFile(): DefaultConfigFile {
        return filtersFile
    }

    fun addSourceFile(sourceFile: File?) {
        additionalFiles.add(sourceFile)
    }

    val resourceFiles: MutableSet<File?>
        get() {
            val allResources: MutableSet<File?> = LinkedHashSet<File?>()
            for (binary in configurations.keys) {
                allResources.addAll(binary.getResourceFiles().getFiles())
            }
            return allResources
        }

    fun getConfigurations(): MutableList<VisualStudioProjectConfiguration?> {
        if (configurations.isEmpty()) {
            return ImmutableList.of<VisualStudioProjectConfiguration?>(VisualStudioProjectConfiguration(this, "unbuildable", null))
        }
        return toList<VisualStudioProjectConfiguration?>(configurations.values)
    }

    fun addConfiguration(nativeBinary: VisualStudioTargetBinary, configuration: VisualStudioProjectConfiguration?) {
        configurations.put(nativeBinary, configuration)
        builtBy(nativeBinary.getSourceFiles())
        builtBy(nativeBinary.getResourceFiles())
        builtBy(nativeBinary.getHeaderFiles())
    }

    fun getConfiguration(nativeBinary: VisualStudioTargetBinary?): VisualStudioProjectConfiguration? {
        return configurations.get(nativeBinary)
    }

    override fun builtBy(vararg tasks: Any?) {
        buildDependencies.add(*tasks)
    }

    override fun getBuildDependencies(): TaskDependency {
        return buildDependencies
    }

    override fun getName(): String? {
        return name
    }

    override fun getPublishArtifact(): IdeProjectMetadata {
        return VisualStudioProjectMetadata(this)
    }

    class DefaultConfigFile @Inject constructor(private val fileResolver: PathToFileResolver, defaultLocation: String?) : XmlConfigFile {
        @get:Nested
        val xmlActions: MutableList<Action<in XmlProvider?>?> = ArrayList<Action<in XmlProvider?>?>()
        private var location: Any?

        init {
            this.location = defaultLocation
        }

        override fun getLocation(): File? {
            return fileResolver.resolve(location)
        }

        override fun setLocation(location: Any?) {
            this.location = location
        }

        override fun withXml(action: Action<in XmlProvider?>?) {
            xmlActions.add(action)
        }
    }

    companion object {
        fun getUUID(projectFile: File): String {
            return "{" + UUID.nameUUIDFromBytes(projectFile.getAbsolutePath().toByteArray()).toString().uppercase() + "}"
        }
    }
}
