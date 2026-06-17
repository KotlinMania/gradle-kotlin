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
package org.gradle.api.internal.tasks.compile

import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler
import java.io.File
import javax.inject.Inject

class DaemonSideCompiler @Inject constructor(private val objectFactory: ObjectFactory?, private val javaCompilerPlugins: MutableList<File?>?, private val problemsService: ProblemsInternal) :
    Compiler<GroovyJavaJointCompileSpec?> {
    override fun execute(spec: GroovyJavaJointCompileSpec?): WorkResult? {
        val javaCompiler: Compiler<JavaCompileSpec?> = JdkJavaCompiler(JavaHomeBasedJavaCompilerFactory(javaCompilerPlugins), problemsService)
        val groovyCompiler: Compiler<GroovyJavaJointCompileSpec?> = ApiGroovyCompiler(javaCompiler, objectFactory)
        return groovyCompiler.execute(spec)
    }
}
