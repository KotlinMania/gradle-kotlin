/*
 * Copyright 2024 the original author or authors.
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

import com.sun.source.util.JavacTask
import com.sun.tools.javac.util.Context
import java.io.Writer
import javax.tools.DiagnosticListener
import javax.tools.JavaCompiler
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject

/**
 * This interface extends the standard [JavaCompiler] interface with [com.sun.tools.javac.api.JavacTool]'s internal method
 * [com.sun.tools.javac.api.JavacTool.getTask], which has an additional [Context] parameter.
 */
interface ContextAwareJavaCompiler : JavaCompiler {
    fun getTask(
        out: Writer?,
        fileManager: JavaFileManager?,
        diagnosticListener: DiagnosticListener<in JavaFileObject?>?,
        options: Iterable<String?>?,
        classes: Iterable<String?>?,
        compilationUnits: Iterable<out JavaFileObject?>?,
        context: Context?
    ): JavacTask?
}
