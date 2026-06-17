/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.nativeplatform.tasks

import org.gradle.api.Incubating
import org.gradle.api.Task
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.language.base.compile.CompilerVersion
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.VersionAwareCompiler
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.RegexBackedCSourceParser
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PCHUtils
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Compiles native source files into object files.
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class AbstractNativeSourceCompileTask : AbstractNativeCompileTask() {
    /**
     * Returns the pre-compiled header to be used during compilation
     */
    @JvmField
    @get:Incubating
    @get:Nested
    @get:Optional
    @set:Incubating
    var preCompiledHeader: PreCompiledHeader? = null

    override fun configureSpec(spec: NativeCompileSpec) {
        super.configureSpec(spec)
        if (preCompiledHeader != null) {
            val pchObjectFile = preCompiledHeader!!.getObjectFile()
            val pchDir = PCHUtils.generatePCHObjectDirectory(spec.getTempDir(), preCompiledHeader!!.getPrefixHeaderFile(), pchObjectFile)
            spec.setPrefixHeaderFile(File(pchDir, preCompiledHeader!!.getPrefixHeaderFile()!!.getName()))
            spec.setPreCompiledHeaderObjectFile(File(pchDir, pchObjectFile.getName()))
            spec.setPreCompiledHeader(RegexBackedCSourceParser.Companion.parseExpression(preCompiledHeader!!.getIncludeString()).getValue())
        }
    }

    init {
        getOutputs().doNotCacheIf("Pre-compiled headers are used", object : Spec<Task?> {
            override fun isSatisfiedBy(element: Task?): Boolean {
                return this.preCompiledHeader != null
            }
        })
        getOutputs().doNotCacheIf("Could not determine compiler version", object : Spec<Task?> {
            override fun isSatisfiedBy(element: Task?): Boolean {
                val compilerVersion: CompilerVersion? = this.compilerVersion
                return compilerVersion == null
            }
        })
    }

    @get:Nested
    @get:Optional
    protected val compilerVersion: CompilerVersion?
        /**
         * The compiler used, including the type and the version.
         *
         * @since 4.4
         */
        get() {
            val toolChain = getToolChain().get() as NativeToolChainInternal
            val targetPlatform = getTargetPlatform().get() as NativePlatformInternal
            val toolProvider = toolChain.select(targetPlatform)
            val compiler: Compiler<out NativeCompileSpec?> =
                toolProvider.newCompiler(createCompileSpec().javaClass)
            if (compiler !is VersionAwareCompiler<*>) {
                return null
            }
            return (compiler as VersionAwareCompiler<*>).getVersion()
        }
}
