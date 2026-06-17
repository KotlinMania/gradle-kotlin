/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.ide.xcode.internal

import org.gradle.api.Named
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.provider.Providers
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskDependency
import org.gradle.ide.xcode.internal.xcodeproj.FileTypes
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget
import org.gradle.language.swift.SwiftVersion
import javax.inject.Inject

/**
 * @see [XCode Scheme Concept](https://developer.apple.com/library/content/featuredarticles/XcodeConcepts/Concept-Schemes.html)
 */
class XcodeTarget @Inject constructor(private val name: String?, val id: String?, objectFactory: ObjectFactory) : Named {
    val headerSearchPaths: ConfigurableFileCollection
    val compileModules: ConfigurableFileCollection
    val sources: ConfigurableFileCollection
    val taskDependencies: MutableList<TaskDependency?> = ArrayList<TaskDependency?>()
    var taskName: String? = null
    var gradleCommand: String? = null

    val binaries: MutableList<XcodeBinary?> = ArrayList<XcodeBinary?>()
    var debugOutputFile: Provider<out FileSystemLocation?>?
        private set
    var productType: PBXTarget.ProductType? = null
    var productName: String? = null
    val swiftSourceCompatibility: Property<SwiftVersion?>?
    val defaultConfigurationName: Property<String?>?

    init {
        this.sources = objectFactory.fileCollection()
        this.headerSearchPaths = objectFactory.fileCollection()
        this.compileModules = objectFactory.fileCollection()
        this.swiftSourceCompatibility = objectFactory.property<SwiftVersion?>(SwiftVersion::class.java)
        this.defaultConfigurationName = objectFactory.property<String?>(String::class.java)
        this.defaultConfigurationName.set(DefaultXcodeProject.Companion.BUILD_DEBUG)
        this.debugOutputFile = Providers.notDefined<FileSystemLocation?>()
    }

    override fun getName(): String? {
        return name
    }

    val outputFileType: String?
        get() = toFileType(productType)

    val isRunnable: Boolean
        get() = PBXTarget.ProductType.TOOL == this.productType

    val isUnitTest: Boolean
        get() = PBXTarget.ProductType.UNIT_TEST == this.productType

    fun addTaskDependency(taskDependency: TaskDependency?) {
        taskDependencies.add(taskDependency)
    }

    fun addBinary(configuration: String, outputFile: Provider<out FileSystemLocation?>?, architectureName: String?) {
        binaries.add(XcodeBinary(configuration, outputFile, architectureName))
        if (configuration.contains("Debug")) {
            this.debugOutputFile = outputFile
        }
    }

    val isBuildable: Boolean
        get() = !binaries.isEmpty()

    companion object {
        private fun toFileType(productType: PBXTarget.ProductType?): String? {
            if (PBXTarget.ProductType.TOOL == productType) {
                return FileTypes.MACH_O_EXECUTABLE.identifier
            } else if (PBXTarget.ProductType.DYNAMIC_LIBRARY == productType) {
                return FileTypes.MACH_O_DYNAMIC_LIBRARY.identifier
            } else if (PBXTarget.ProductType.STATIC_LIBRARY == productType) {
                return FileTypes.ARCHIVE_LIBRARY.identifier
            } else {
                return "compiled"
            }
        }
    }
}
