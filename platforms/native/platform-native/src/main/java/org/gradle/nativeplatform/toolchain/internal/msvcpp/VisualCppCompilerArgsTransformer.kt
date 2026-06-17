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
package org.gradle.nativeplatform.toolchain.internal.msvcpp

import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import java.util.Optional
import java.util.function.Consumer

internal abstract class VisualCppCompilerArgsTransformer<T : NativeCompileSpec?> : ArgsTransformer<T?> {
    override fun transform(spec: T?): MutableList<String?> {
        val args: MutableList<String?> = ArrayList<String?>()
        addToolSpecificArgs(spec, args)
        addMacroArgs(spec, args)
        addUserArgs(spec, args)
        addIncludeArgs(spec, args)
        return args
    }

    private fun addUserArgs(spec: T?, args: MutableList<String?>) {
        args.addAll(EscapeUserArgs.Companion.escapeUserArgs(spec!!.getAllArgs()))
    }

    protected open fun addToolSpecificArgs(spec: T?, args: MutableList<String?>) {
        this.languageOption.ifPresent(Consumer { e: String? -> args.add(e) })
        args.add("/nologo")
        args.add("/c")
        if (spec!!.isDebuggable) {
            args.add("/Zi")
        }
        if (spec.isOptimized) {
            args.add("/O2")
        }
    }

    protected fun addIncludeArgs(spec: T?, args: MutableList<String?>) {
        for (file in spec!!.includeRoots!!) {
            args.add("/I" + file!!.getAbsolutePath())
        }
        for (file in spec.systemIncludeRoots!!) {
            args.add("/I" + file!!.getAbsolutePath())
        }
    }

    protected fun addMacroArgs(spec: T?, args: MutableList<String?>) {
        for (macroArg in MacroArgsConverter().transform(spec!!.macros)!!) {
            args.add(EscapeUserArgs.Companion.escapeUserArg("/D" + macroArg))
        }
    }

    protected open val languageOption: Optional<String?>?
        /**
         * Returns compiler specific language option
         * @return compiler language option or an empty Optional if the language does not require it
         */
        get() = Optional.empty<String?>()
}
