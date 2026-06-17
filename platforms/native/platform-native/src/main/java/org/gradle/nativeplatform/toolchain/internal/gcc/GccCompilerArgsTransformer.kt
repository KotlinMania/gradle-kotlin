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
package org.gradle.nativeplatform.toolchain.internal.gcc

import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import java.util.Collections

/**
 * Maps common options for C/C++ compiling with GCC
 */
internal abstract class GccCompilerArgsTransformer<T : NativeCompileSpec?> : ArgsTransformer<T?> {
    override fun transform(spec: T?): MutableList<String?> {
        val args: MutableList<String?> = ArrayList<String?>()
        addToolSpecificArgs(spec, args)
        addMacroArgs(spec, args)
        addUserArgs(spec, args)
        addIncludeArgs(spec, args)
        return args
    }

    protected fun addToolSpecificArgs(spec: T?, args: MutableList<String?>) {
        Collections.addAll<String?>(args, "-x", this.language)
        args.add("-c")
        if (spec!!.isPositionIndependentCode) {
            if (!spec.getTargetPlatform().operatingSystem.isWindows) {
                args.add("-fPIC")
            }
        }
        if (spec.isDebuggable) {
            args.add("-g")
        }
        if (spec.isOptimized) {
            args.add("-O3")
        }
    }

    protected fun addIncludeArgs(spec: T?, args: MutableList<String?>) {
        if (!needsStandardIncludes(spec!!.getTargetPlatform())) {
            args.add("-nostdinc")
        }

        for (file in spec.includeRoots!!) {
            args.add("-I")
            args.add(file!!.getAbsolutePath())
        }

        for (file in spec.systemIncludeRoots!!) {
            args.add("-isystem")
            args.add(file!!.getAbsolutePath())
        }
    }

    protected fun addMacroArgs(spec: T?, args: MutableList<String?>) {
        for (macroArg in MacroArgsConverter().transform(spec!!.macros)!!) {
            args.add("-D" + macroArg)
        }
    }

    protected fun addUserArgs(spec: T?, args: MutableList<String?>) {
        args.addAll(spec!!.getAllArgs())
    }

    protected open fun needsStandardIncludes(targetPlatform: NativePlatform): Boolean {
        return targetPlatform.operatingSystem.isMacOsX
    }

    protected abstract val language: String?
}
