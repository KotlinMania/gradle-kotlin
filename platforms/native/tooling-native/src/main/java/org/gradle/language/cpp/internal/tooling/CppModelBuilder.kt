/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.language.cpp.internal.tooling

import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.language.cpp.CppApplication
import org.gradle.language.cpp.CppComponent
import org.gradle.language.cpp.CppExecutable
import org.gradle.language.cpp.CppLibrary
import org.gradle.language.cpp.CppSharedLibrary
import org.gradle.language.cpp.CppStaticLibrary
import org.gradle.language.cpp.internal.DefaultCppBinary
import org.gradle.language.cpp.internal.DefaultCppLibrary
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.language.nativeplatform.ComponentWithExecutable
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.nativeplatform.test.cpp.CppTestExecutable
import org.gradle.nativeplatform.test.cpp.CppTestSuite
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.plugins.ide.internal.tooling.ToolingModelBuilderSupport.buildFromTask
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.model.cpp.CppProject
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.File

class CppModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean {
        return modelName == CppProject::class.java.getName()
    }

    override fun buildAll(modelName: String, project: Project): Any? {
        val projectIdentifier = DefaultProjectIdentifier(project.getRootDir(), project.getPath())
        val namingSchemeFactory = CompilerOutputFileNamingSchemeFactory((project as ProjectInternal).getFileResolver())
        var mainComponent: DefaultCppComponentModel? = null
        val application = project.getComponents().withType<CppApplication?>(CppApplication::class.java).findByName("main")
        if (application != null) {
            mainComponent =
                DefaultCppApplicationModel(application.getName(), application.baseName.get(), binariesFor(application, application.privateHeaderDirs, projectIdentifier, namingSchemeFactory))
        } else {
            val library = project.getComponents().withType<CppLibrary?>(CppLibrary::class.java).findByName("main") as DefaultCppLibrary?
            if (library != null) {
                mainComponent = DefaultCppLibraryModel(library.getName(), library.baseName.get(), binariesFor(library, library.getAllHeaderDirs(), projectIdentifier, namingSchemeFactory))
            }
        }
        var testComponent: DefaultCppComponentModel? = null
        val testSuite = project.getComponents().withType<CppTestSuite?>(CppTestSuite::class.java).findByName("test")
        if (testSuite != null) {
            testComponent = DefaultCppTestSuiteModel(testSuite.getName(), testSuite.baseName.get(), binariesFor(testSuite, testSuite.privateHeaderDirs, projectIdentifier, namingSchemeFactory))
        }
        return DefaultCppProjectModel(projectIdentifier, mainComponent, testComponent)
    }

    private fun binariesFor(
        component: CppComponent,
        headerDirs: Iterable<File?>,
        projectIdentifier: DefaultProjectIdentifier?,
        namingSchemeFactory: CompilerOutputFileNamingSchemeFactory
    ): MutableList<DefaultCppBinaryModel?> {
        val headerDirsCopy: MutableList<File?> = ImmutableList.copyOf<File?>(headerDirs)
        val binaries: MutableList<DefaultCppBinaryModel?> = ArrayList<DefaultCppBinaryModel?>()
        for (binary in component.getBinaries()!!.get()!!) {
            val cppBinary = binary as DefaultCppBinary?
            val platformToolProvider = cppBinary!!.platformToolProvider
            val compileTask: CppCompile = binary.compileTask.get()
            val sourceFiles = sourceFiles(namingSchemeFactory, platformToolProvider!!, compileTask.objectFileDir.get().getAsFile(), binary.cppSource.getFiles())
            val systemIncludes: MutableList<File?> = ImmutableList.copyOf(compileTask.systemIncludes!!.getFiles())
            val userIncludes: MutableList<File?> = ImmutableList.copyOf(compileTask.includes!!.getFiles())
            val macroDefines = macroDefines(compileTask)
            val additionalArgs = args(compileTask.compilerArgs.get())
            val compilerLookup = platformToolProvider.locateTool(ToolType.CPP_COMPILER)
            val compilerExe: File? = if (compilerLookup!!.isAvailable) compilerLookup.tool else null
            val compileTaskModel: LaunchableGradleTask? = buildLaunchableTask(projectIdentifier, compileTask)
            val compilationDetails = DefaultCompilationDetails(
                compileTaskModel,
                compilerExe,
                compileTask.objectFileDir.get().getAsFile(),
                sourceFiles,
                headerDirsCopy,
                systemIncludes,
                userIncludes,
                macroDefines,
                additionalArgs
            )
            if (binary is CppExecutable || binary is CppTestExecutable) {
                val componentWithExecutable = binary as ComponentWithExecutable
                val linkTask: LinkExecutable = componentWithExecutable.linkTask.get()
                val linkTaskModel: LaunchableGradleTask? = Companion.buildLaunchableTask(projectIdentifier, componentWithExecutable.executableFileProducer.get())
                val linkageDetails = DefaultLinkageDetails(linkTaskModel, componentWithExecutable.executableFile.get().getAsFile(), args(linkTask.linkerArgs.get()))
                binaries.add(DefaultCppExecutableModel(binary.getName(), cppBinary.identity.getName(), binary.baseName.get(), compilationDetails, linkageDetails))
            } else if (binary is CppSharedLibrary) {
                val sharedLibrary = binary as CppSharedLibrary
                val linkTask: LinkSharedLibrary = sharedLibrary.linkTask.get()
                val linkTaskModel: LaunchableGradleTask? = Companion.buildLaunchableTask(projectIdentifier, sharedLibrary.linkFileProducer.get())
                val linkageDetails = DefaultLinkageDetails(linkTaskModel, sharedLibrary.linkFile.get().getAsFile(), args(linkTask.linkerArgs.get()))
                binaries.add(DefaultCppSharedLibraryModel(binary.getName(), cppBinary.identity.getName(), binary.baseName.get(), compilationDetails, linkageDetails))
            } else if (binary is CppStaticLibrary) {
                val staticLibrary = binary as CppStaticLibrary
                val createTaskModel: LaunchableGradleTask? = Companion.buildLaunchableTask(projectIdentifier, staticLibrary.linkFileProducer.get())
                val linkageDetails = DefaultLinkageDetails(createTaskModel, staticLibrary.linkFile.get().getAsFile(), mutableListOf<String?>())
                binaries.add(DefaultCppStaticLibraryModel(binary.getName(), cppBinary.identity.getName(), binary.baseName.get(), compilationDetails, linkageDetails))
            }
        }
        return binaries
    }

    private fun sourceFiles(
        namingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
        platformToolProvider: PlatformToolProvider,
        objDir: File?,
        files: MutableSet<File>
    ): MutableList<DefaultSourceFile?> {
        val namingScheme = namingSchemeFactory.create().withObjectFileNameSuffix(platformToolProvider.objectFileExtension).withOutputBaseFolder(objDir)
        val result: MutableList<DefaultSourceFile?> = ArrayList<DefaultSourceFile?>(files.size)
        for (file in files) {
            result.add(DefaultSourceFile(file, namingScheme.map(file)))
        }
        return result
    }

    private fun args(compilerArgs: MutableList<String?>): MutableList<String?> {
        return ImmutableList.copyOf<String?>(compilerArgs)
    }

    private fun macroDefines(compileTask: CppCompile): MutableList<DefaultMacroDirective?> {
        if (compileTask.getMacros().isEmpty()) {
            return mutableListOf<DefaultMacroDirective?>()
        }
        val macros: MutableList<DefaultMacroDirective?> = ArrayList<DefaultMacroDirective?>(compileTask.getMacros().size)
        for (entry in compileTask.getMacros().entries) {
            macros.add(DefaultMacroDirective(entry.key, entry.value))
        }
        return macros
    }

    companion object {
        private fun buildLaunchableTask(projectIdentifier: DefaultProjectIdentifier?, task: Task): LaunchableGradleTask? {
            return buildFromTask<LaunchableGradleTask?>(LaunchableGradleTask(), projectIdentifier, task)
        }
    }
}
