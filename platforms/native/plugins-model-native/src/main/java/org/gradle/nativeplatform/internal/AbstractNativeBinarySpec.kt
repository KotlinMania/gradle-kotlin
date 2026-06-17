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
package org.gradle.nativeplatform.internal

import com.google.common.collect.ImmutableMap
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.language.nativeplatform.DependentSourceSet
import org.gradle.language.nativeplatform.internal.DependentSourceSetInternal
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.NativeComponentSpec
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.PreprocessingTool
import org.gradle.nativeplatform.Tool
import org.gradle.nativeplatform.internal.resolve.NativeBinaryRequirementResolveResult
import org.gradle.nativeplatform.internal.resolve.NativeBinaryResolveResult
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary.source
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader
import org.gradle.platform.base.binary.BaseBinarySpec
import org.gradle.platform.base.internal.BinaryBuildAbility
import org.gradle.platform.base.internal.ToolSearchBuildAbility
import java.io.File

abstract class AbstractNativeBinarySpec : BaseBinarySpec(), NativeBinarySpecInternal {
    private val libs: MutableSet<in Any?> = LinkedHashSet<Any?>()
    private val linker: Tool = DefaultTool()
    private val staticLibArchiver: Tool = DefaultTool()

    // TODO:HH Use managed views for this, only applied when the respective language is applied
    private val assembler: Tool = DefaultTool()
    private val cCompiler: PreprocessingTool = DefaultPreprocessingTool()
    private val cppCompiler: PreprocessingTool = DefaultPreprocessingTool()
    private val objcCompiler: PreprocessingTool = DefaultPreprocessingTool()
    private val objcppCompiler: PreprocessingTool = DefaultPreprocessingTool()
    private val rcCompiler: PreprocessingTool = DefaultPreprocessingTool()
    private val toolsByName: MutableMap<String?, Tool?> = ImmutableMap.builder<String?, Tool?>()
        .put("assembler", assembler)
        .put("cCompiler", cCompiler)
        .put("cppCompiler", cppCompiler)
        .put("objcCompiler", objcCompiler)
        .put("objcppCompiler", objcppCompiler)
        .put("rcCompiler", rcCompiler)
        .build()

    private var toolProvider: PlatformToolProvider? = null
    private var flavor: Flavor? = null
    private var toolChain: NativeToolChain? = null
    private var targetPlatform: NativePlatform? = null
    private var buildType: BuildType? = null
    private var resolver: NativeDependencyResolver? = null
    private val prefixFileToPCH: MutableMap<File?, PreCompiledHeader?> = HashMap<File?, PreCompiledHeader?>()
    private var fileCollectionFactory: FileCollectionFactory? = null

    override fun getDisplayName(): String? {
        return getNamingScheme().getDescription()
    }

    override fun getComponent(): NativeComponentSpec? {
        return getComponentAs<NativeComponentSpec?>(NativeComponentSpec::class.java)
    }

    override fun getFlavor(): Flavor? {
        return flavor
    }

    override fun setFlavor(flavor: Flavor?) {
        this.flavor = flavor
    }

    override fun getToolChain(): NativeToolChain? {
        return toolChain
    }

    override fun setToolChain(toolChain: NativeToolChain?) {
        this.toolChain = toolChain
    }

    override fun getTargetPlatform(): NativePlatform? {
        return targetPlatform
    }

    override fun setTargetPlatform(targetPlatform: NativePlatform?) {
        this.targetPlatform = targetPlatform
    }

    override fun getBuildType(): BuildType? {
        return buildType
    }

    override fun setBuildType(buildType: BuildType?) {
        this.buildType = buildType
    }

    override fun getLinker(): Tool {
        return linker
    }

    override fun getStaticLibArchiver(): Tool {
        return staticLibArchiver
    }

    override fun getAssembler(): Tool {
        return assembler
    }

    override fun getcCompiler(): PreprocessingTool {
        return cCompiler
    }

    override fun getCppCompiler(): PreprocessingTool {
        return cppCompiler
    }

    override fun getObjcCompiler(): PreprocessingTool {
        return objcCompiler
    }

    override fun getObjcppCompiler(): PreprocessingTool {
        return objcppCompiler
    }

    override fun getRcCompiler(): PreprocessingTool {
        return rcCompiler
    }

    override fun getToolByName(name: String?): Tool? {
        return toolsByName.get(name)
    }

    override fun getLibs(): MutableCollection<NativeDependencySet?>? {
        return resolve(getInputs().withType<DependentSourceSet?>(DependentSourceSet::class.java)).getAllResults()
    }

    override fun getLibs(sourceSet: DependentSourceSet?): MutableCollection<NativeDependencySet?>? {
        return resolve(mutableSetOf<DependentSourceSet?>(sourceSet)).getAllResults()
    }

    override fun lib(notation: Any?) {
        libs.add(notation)
    }

    override fun getDependentBinaries(): MutableCollection<NativeLibraryBinary?>? {
        return resolve(getInputs().withType<DependentSourceSet?>(DependentSourceSet::class.java)).getAllLibraryBinaries()
    }

    override fun getAllResolutions(): MutableCollection<NativeBinaryRequirementResolveResult?>? {
        return resolve(getInputs().withType<DependentSourceSet?>(DependentSourceSet::class.java)).getAllResolutions()
    }

    override fun getPrefixFileToPCH(): MutableMap<File?, PreCompiledHeader?> {
        return prefixFileToPCH
    }

    override fun addPreCompiledHeaderFor(sourceSet: DependentSourceSet) {
        prefixFileToPCH.put(
            (sourceSet as DependentSourceSetInternal).prefixHeaderFile,
            PreCompiledHeader(getIdentifier().child("pch"))
        )
    }

    private fun resolve(sourceSets: Iterable<out DependentSourceSet>): NativeBinaryResolveResult {
        val allLibs: MutableSet<in Any?> = LinkedHashSet<Any?>(libs)
        for (dependentSourceSet in sourceSets) {
            allLibs.addAll(dependentSourceSet.libs!!)
        }
        val resolution = NativeBinaryResolveResult(this, allLibs)
        resolver!!.resolve(resolution)
        return resolution
    }

    override fun getPlatformToolProvider(): PlatformToolProvider? {
        return toolProvider
    }

    override fun setPlatformToolProvider(toolProvider: PlatformToolProvider?) {
        this.toolProvider = toolProvider
    }

    override fun setResolver(resolver: NativeDependencyResolver) {
        this.resolver = resolver
    }

    protected fun getFileCollectionFactory(): FileCollectionFactory? {
        return fileCollectionFactory
    }

    override fun setFileCollectionFactory(fileCollectionFactory: FileCollectionFactory?) {
        this.fileCollectionFactory = fileCollectionFactory
    }

    override fun getBinaryBuildAbility(): BinaryBuildAbility {
        val toolChainInternal = getToolChain() as NativeToolChainInternal
        val platformInternal = getTargetPlatform() as NativePlatformInternal?
        return ToolSearchBuildAbility(toolChainInternal.select(platformInternal)!!)
    }

    override fun binaryInputs(files: FileCollection?) {
        // TODO - should split this up, so that the inputs are attached to an object that represents the binary, which is then later used to configure the link/assemble tasks
        this.createOrLink.source(files)
    }

    protected abstract val createOrLink: ObjectFilesToBinary?
}
