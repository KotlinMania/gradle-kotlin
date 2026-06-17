/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.daemon

import org.gradle.api.tasks.WorkResult
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.workers.WorkAction
import org.gradle.workers.internal.DefaultWorkResult
import org.gradle.workers.internal.ProvidesWorkResult
import javax.inject.Inject

class CompilerWorkAction @Inject constructor(private val parameters: CompilerParameters?, private val instantiator: Instantiator) : WorkAction<CompilerParameters?>, ProvidesWorkResult {
    private var workResult: DefaultWorkResult? = null

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    override fun getParameters(): CompilerParameters? {
        return parameters
    }

    override fun execute() {
        val compilerClass: Class<out Compiler<*>> = org.gradle.internal.Cast.uncheckedCast<Class<out Compiler<*>>?>(
            org.gradle.internal.classloader.ClassLoaderUtils.classFromContextLoader(
                getParameters().getCompilerClassName()
            )
        )!!
        val compiler = instantiator.newInstance(compilerClass, *getParameters()!!.getCompilerInstanceParameters())
        setWorkResult(compiler.execute(uncheckedCast(getParameters()!!.getCompileSpec())))
    }

    private fun setWorkResult(workResult: WorkResult) {
        if (workResult is DefaultWorkResult) {
            this.workResult = workResult
        } else {
            this.workResult = DefaultWorkResult(workResult.getDidWork(), null)
        }
    }

    override fun getWorkResult(): DefaultWorkResult? {
        return workResult
    }
}
