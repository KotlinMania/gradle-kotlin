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
package org.gradle.ide.xcode.tasks

import com.dd.plist.NSDictionary
import com.dd.plist.NSString
import com.google.common.base.Joiner
import com.google.common.base.Optional
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.PropertyListTransformer
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.ide.xcode.XcodeProject
import org.gradle.ide.xcode.internal.DefaultXcodeProject
import org.gradle.ide.xcode.internal.XcodePropertyAdapter
import org.gradle.ide.xcode.internal.XcodeUtils
import org.gradle.ide.xcode.internal.xcodeproj.GidGenerator
import org.gradle.ide.xcode.internal.xcodeproj.PBXBuildFile
import org.gradle.ide.xcode.internal.xcodeproj.PBXFileReference
import org.gradle.ide.xcode.internal.xcodeproj.PBXGroup
import org.gradle.ide.xcode.internal.xcodeproj.PBXLegacyTarget
import org.gradle.ide.xcode.internal.xcodeproj.PBXNativeTarget
import org.gradle.ide.xcode.internal.xcodeproj.PBXProject
import org.gradle.ide.xcode.internal.xcodeproj.PBXReference
import org.gradle.ide.xcode.internal.xcodeproj.PBXShellScriptBuildPhase
import org.gradle.ide.xcode.internal.xcodeproj.PBXSourcesBuildPhase
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget
import org.gradle.ide.xcode.internal.xcodeproj.XcodeprojSerializer
import org.gradle.ide.xcode.tasks.internal.XcodeProjectFile
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.serialization.Cached
import org.gradle.language.swift.SwiftVersion
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.plugins.ide.api.PropertyListGeneratorTask
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.function.Consumer
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * Task for generating a Xcode project file (e.g. `Foo.xcodeproj/project.pbxproj`). A project contains all the elements used to build your products and maintains the relationships between those elements. It contains one or more targets, which specify how to build products. A project defines default build settings for all the targets in the project (each target can also specify its own build settings, which override the project build settings).
 *
 * @see XcodeProject
 *
 * @since 4.2
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class GenerateXcodeProjectFileTask @Inject constructor(private val gidGenerator: GidGenerator?) : PropertyListGeneratorTask<XcodeProjectFile?>() {
    @Transient
    private var xcodeProject: DefaultXcodeProject? = null
    private val pathToFileReference: MutableMap<String?, PBXFileReference?> = HashMap<String?, PBXFileReference?>()
    private val spec = Cached.of({ this.calculateSpec() })

    private fun calculateSpec(): ProjectSpec {
        val targets: MutableList<TargetSpec> = ArrayList<TargetSpec>()
        for (target in xcodeProject!!.getTargets()) {
            val binaries: MutableList<BinarySpec?> = ArrayList<BinarySpec?>()
            for (binary in target.getBinaries()) {
                binaries.add(
                    BinarySpec(
                        binary.getBuildConfigurationName(),
                        binary.getArchitectureName(),
                        binary.getOutputFile()
                    )
                )
            }
            targets.add(
                GenerateXcodeProjectFileTask.TargetSpec(
                    target.getName(),
                    target.getId(),
                    target.isBuildable(),
                    target.isUnitTest(),
                    target.getSources(),
                    target.getCompileModules(),
                    target.getHeaderSearchPaths(),
                    target.getTaskName(),
                    target.getOutputFileType(),
                    target.getProductName(),
                    target.getProductType(),
                    target.getDebugOutputFile(),
                    target.getGradleCommand(),
                    target.getSwiftSourceCompatibility(),
                    binaries
                )
            )
        }

        return ProjectSpec(
            getProject().getPath(),
            xcodeProject!!.getGroups().getSources(),
            xcodeProject!!.getGroups().getHeaders(),
            xcodeProject!!.getGroups().getTests(),
            xcodeProject!!.getGroups().getRoot(),
            targets
        )
    }

    override fun configure(projectFile: XcodeProjectFile) {
        val spec = this.spec.get()
        val project = PBXProject(spec!!.projectPath)

        addToGroup(project.getMainGroup(), spec.sources, "Sources")
        addToGroup(project.getMainGroup(), spec.headers, "Headers")
        addToGroup(project.getMainGroup(), spec.tests, "Tests")
        addToGroup(project.getMainGroup(), spec.root)

        for (xcodeTarget in spec.targets) {
            if (xcodeTarget.buildable) {
                project.getTargets().add(toGradlePbxTarget(spec, xcodeTarget))
            } else {
                getLogger().warn("'" + xcodeTarget.name + "' component in project '" + spec.projectPath + "' is not buildable.")
            }
            project.getTargets().add(toIndexPbxTarget(xcodeTarget))

            if (!xcodeTarget.unitTest && xcodeTarget.debugOutputFile.isPresent()) {
                val debugOutputFile = xcodeTarget.debugOutputFile.get().getAsFile()
                val fileReference = PBXFileReference(debugOutputFile.getName(), debugOutputFile.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE)
                fileReference.setExplicitFileType(Optional.of<String?>(xcodeTarget.outputFileType))
                project.getMainGroup().getOrCreateChildGroupByName(PRODUCTS_GROUP_NAME).getChildren().add(fileReference)
            }
        }

        // Create build configuration at the project level from all target's build configuration
        project.getTargets().stream().flatMap<String?> { it: PBXTarget? -> it!!.getBuildConfigurationList().getBuildConfigurationsByName().asMap().keys.stream() }
            .forEach { key: String? -> project.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(key!!) }

        val serializer = XcodeprojSerializer(gidGenerator, project)
        val rootObject = serializer.toPlist()

        projectFile.transformAction(Action { dict: NSDictionary? ->
            dict!!.clear()
            dict.putAll(rootObject!!)
        })
    }

    private fun addToGroup(mainGroup: PBXGroup, sources: FileCollection, groupName: String?) {
        if (!sources.isEmpty()) {
            addToGroup(mainGroup.getOrCreateChildGroupByName(groupName), sources)
        }
    }

    private fun addToGroup(group: PBXGroup, sources: Iterable<File>) {
        for (source in sources) {
            val fileReference = toFileReference(source)
            pathToFileReference.put(source.getAbsolutePath(), fileReference)
            group.getChildren().add(fileReference)
        }
    }

    private fun getAllBinaries(xcodeProject: ProjectSpec): MutableList<BinarySpec?> {
        return xcodeProject.targets.stream().map<MutableList<BinarySpec?>?> { it: TargetSpec? -> it!!.binaries }.flatMap<BinarySpec?> { obj: MutableList<BinarySpec?>? -> obj!!.stream() }.collect(
            Collectors.toList()
        )
    }

    override fun create(): XcodeProjectFile {
        return XcodeProjectFile(uncheckedNonnullCast<PropertyListTransformer<NSDictionary?>?>(getPropertyListTransformer()))
    }

    private fun toFileReference(file: File): PBXFileReference {
        return PBXFileReference(file.getName(), file.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE)
    }

    private fun toGradlePbxTarget(xcodeProject: ProjectSpec, xcodeTarget: TargetSpec): PBXTarget {
        if (xcodeTarget.unitTest) {
            return toXCTestPbxTarget(xcodeProject, xcodeTarget)
        }
        return toToolAndLibraryPbxTarget(xcodeTarget)
    }

    private fun toToolAndLibraryPbxTarget(xcodeTarget: TargetSpec): PBXTarget {
        val target = PBXLegacyTarget(xcodeTarget.name, xcodeTarget.productType)
        target.setProductName(xcodeTarget.productName)

        target.setBuildToolPath(xcodeTarget.gradleCommand)
        target.setBuildArgumentsString(buildGradleArgs(xcodeTarget))
        target.setGlobalID(xcodeTarget.id)
        val outputFile = xcodeTarget.debugOutputFile.get().getAsFile()
        target.setProductReference(PBXFileReference(outputFile.getName(), outputFile.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE))

        xcodeTarget.binaries.forEach(Consumer { xcodeBinary: BinarySpec? ->
            val settings = target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(xcodeBinary!!.buildConfigurationName).getBuildSettings()
            val binaryOutputFile = xcodeBinary.outputFile.get().getAsFile()
            settings.put("CONFIGURATION_BUILD_DIR", NSString(binaryOutputFile.getParentFile().getAbsolutePath()))
            settings.put("PRODUCT_NAME", target.getProductName())
            settings.put("SWIFT_VERSION", toXcodeSwiftVersion(xcodeTarget.swiftSourceCompatibility))
            settings.put("ARCHS", toXcodeArchitecture(xcodeBinary.architectureName))
            settings.put(
                "VALID_ARCHS",
                xcodeTarget.binaries.stream().map<String?> { it: BinarySpec? -> it!!.architectureName }.map<String?> { architectureName: String? -> Companion.toXcodeArchitecture(architectureName!!) }
                    .distinct().collect(
                        Collectors.joining(" ")
                    ))
        })

        return target
    }

    private fun buildGradleArgs(xcodeTarget: TargetSpec): String {
        return Joiner.on(' ').join(XcodePropertyAdapter.Companion.getAdapterCommandLine()) + " " + xcodeTarget.taskName
    }

    private fun toXCTestPbxTarget(xcodeProject: ProjectSpec, xcodeTarget: TargetSpec): PBXTarget {
        val hackBuildPhase = PBXShellScriptBuildPhase()
        hackBuildPhase.setShellPath("/bin/sh")
        hackBuildPhase.setShellScript(
            ("# Script to generate specific Swift files Xcode expects when running tests.\n"
                    + "set -eu\n"
                    + "ARCH_ARRAY=(\$ARCHS)\n"
                    + "SUFFIXES=(swiftdoc swiftmodule h)\n"
                    + "for ARCH in \"\${ARCH_ARRAY[@]}\"\n"
                    + "do\n"
                    + "  for SUFFIX in \"\${SUFFIXES[@]}\"\n"
                    + "  do\n"
                    + "    touch \"\$OBJECT_FILE_DIR_normal/\$ARCH/\$PRODUCT_NAME.\$SUFFIX\"\n"
                    + "  done\n"
                    + "done")
        )

        val gradleBuildPhase = PBXShellScriptBuildPhase()
        gradleBuildPhase.setShellPath("/bin/sh")
        gradleBuildPhase.setShellScript("exec \"" + xcodeTarget.gradleCommand + "\" " + buildGradleArgs(xcodeTarget) + " < /dev/null")

        val target = PBXNativeTarget(xcodeTarget.name, xcodeTarget.productType)
        target.setProductName(xcodeTarget.productName)
        target.setGlobalID(xcodeTarget.id)
        // Note the order in which the build phase are added is important
        target.getBuildPhases().add(hackBuildPhase)
        target.getBuildPhases().add(newSourceBuildPhase(xcodeTarget.sources))
        target.getBuildPhases().add(gradleBuildPhase)
        val outputFile = xcodeTarget.debugOutputFile.get().getAsFile()
        target.setProductReference(PBXFileReference(outputFile.getName(), outputFile.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE))

        getAllBinaries(xcodeProject).stream().filter { it: BinarySpec? -> it!!.buildConfigurationName != DefaultXcodeProject.Companion.TEST_DEBUG }.forEach(configureBuildSettings(xcodeTarget, target))

        val testRunnerSettings = target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(DefaultXcodeProject.Companion.TEST_DEBUG).getBuildSettings()

        if (!xcodeTarget.compileModules.isEmpty()) {
            testRunnerSettings.put("SWIFT_INCLUDE_PATHS", XcodeUtils.toSpaceSeparatedList(parentDirs(xcodeTarget.compileModules)))
        }

        testRunnerSettings.put("SWIFT_VERSION", toXcodeSwiftVersion(xcodeTarget.swiftSourceCompatibility))
        testRunnerSettings.put("PRODUCT_NAME", target.getProductName())
        testRunnerSettings.put("OTHER_LDFLAGS", "-help")
        testRunnerSettings.put("OTHER_CFLAGS", "-help")
        testRunnerSettings.put("OTHER_SWIFT_FLAGS", "-help")
        testRunnerSettings.put("SWIFT_INSTALL_OBJC_HEADER", "NO")
        testRunnerSettings.put("SWIFT_OBJC_INTERFACE_HEADER_NAME", "$(PRODUCT_NAME).h")

        return target
    }

    private fun toIndexPbxTarget(xcodeTarget: TargetSpec): PBXTarget {
        val target = PBXNativeTarget("[INDEXING ONLY] " + xcodeTarget.name, PBXTarget.ProductType.INDEXER)
        target.setProductName(xcodeTarget.productName)
        target.getBuildPhases().add(newSourceBuildPhase(xcodeTarget.sources))

        xcodeTarget.binaries.forEach(configureBuildSettings(xcodeTarget, target))

        // Create unbuildable build configuration so the indexer can keep functioning
        if (xcodeTarget.binaries.isEmpty()) {
            val settings = newBuildSettings(xcodeTarget)
            target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(UNBUILDABLE_BUILD_CONFIGURATION_NAME).setBuildSettings(settings)
        }

        return target
    }

    private fun configureBuildSettings(xcodeTarget: TargetSpec, target: PBXNativeTarget): Consumer<BinarySpec?> {
        return Consumer { xcodeBinary: BinarySpec? ->
            val settings = newBuildSettings(xcodeTarget)
            settings.put("ARCHS", toXcodeArchitecture(xcodeBinary!!.architectureName))
            settings.put("VALID_ARCHS", xcodeTarget.binaries.stream().map<String?> { it: BinarySpec? -> toXcodeArchitecture(it!!.architectureName) }.distinct().collect(Collectors.joining(" ")))
            target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(xcodeBinary.buildConfigurationName).setBuildSettings(settings)
        }
    }

    private fun newSourceBuildPhase(sourceFiles: FileCollection): PBXSourcesBuildPhase {
        val result = PBXSourcesBuildPhase()
        for (file in sourceFiles) {
            val fileReference = pathToFileReference.get(file.getAbsolutePath())
            result.getFiles().add(PBXBuildFile(fileReference))
        }
        return result
    }

    private fun newBuildSettings(xcodeTarget: TargetSpec): NSDictionary {
        val result = NSDictionary()
        result.put("SWIFT_VERSION", toXcodeSwiftVersion(xcodeTarget.swiftSourceCompatibility))
        result.put("PRODUCT_NAME", xcodeTarget.productName) // Mandatory

        if (!xcodeTarget.headerSearchPaths.isEmpty()) {
            result.put("HEADER_SEARCH_PATHS", XcodeUtils.toSpaceSeparatedList(xcodeTarget.headerSearchPaths))
        }

        if (!xcodeTarget.compileModules.isEmpty()) {
            result.put("SWIFT_INCLUDE_PATHS", XcodeUtils.toSpaceSeparatedList(parentDirs(xcodeTarget.compileModules)))
        }
        return result
    }

    @Internal
    fun getXcodeProject(): XcodeProject {
        return xcodeProject!!
    }

    fun setXcodeProject(xcodeProject: XcodeProject?) {
        this.xcodeProject = xcodeProject as DefaultXcodeProject
    }

    private class BinarySpec(
        val buildConfigurationName: String,
        val architectureName: String,
        val outputFile: Provider<out FileSystemLocation?>
    )

    private class TargetSpec(
        val name: String?,
        val id: String?,
        val buildable: Boolean,
        val unitTest: Boolean,
        val sources: FileCollection,
        val compileModules: FileCollection,
        val headerSearchPaths: FileCollection,
        val taskName: String?,
        val outputFileType: String,
        val productName: String?,
        val productType: PBXTarget.ProductType?,
        val debugOutputFile: Provider<out FileSystemLocation?>,
        val gradleCommand: String?,
        val swiftSourceCompatibility: Provider<SwiftVersion?>,
        val binaries: MutableList<BinarySpec?>
    )

    private class ProjectSpec(
        val projectPath: String?,
        val sources: FileCollection,
        val headers: FileCollection,
        val tests: FileCollection,
        val root: FileCollection,
        val targets: MutableList<TargetSpec>
    )

    companion object {
        private const val PRODUCTS_GROUP_NAME = "Products"
        private const val UNBUILDABLE_BUILD_CONFIGURATION_NAME = "unbuildable"
        private fun toXcodeArchitecture(architectureName: String): String {
            if (architectureName == MachineArchitecture.X86) {
                return "i386"
            } else if (architectureName == MachineArchitecture.X86_64) {
                return "x86_64"
            } else if (architectureName == MachineArchitecture.ARM64) {
                return "arm64e"
            }

            return architectureName
        }

        private fun toXcodeSwiftVersion(swiftVersion: Provider<SwiftVersion?>): String? {
            if (swiftVersion.isPresent()) {
                return String.format("%d.0", swiftVersion.get()!!.getVersion())
            }
            return null
        }

        private fun parentDirs(files: Iterable<File>): Iterable<File?> {
            val parents: MutableList<File?> = ArrayList<File?>()
            for (file in files) {
                if (file.isDirectory()) {
                    parents.add(file)
                } else {
                    parents.add(file.getParentFile())
                }
            }
            return parents
        }
    }
}
