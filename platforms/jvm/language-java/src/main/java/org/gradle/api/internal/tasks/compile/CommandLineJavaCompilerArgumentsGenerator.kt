/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.collect.Iterables
import org.gradle.internal.process.ArgCollector
import org.gradle.internal.process.ArgWriter
import java.io.File
import java.io.Serializable

class CommandLineJavaCompilerArgumentsGenerator : CompileSpecToArguments<JavaCompileSpec?>, Serializable {
    override fun collectArguments(spec: JavaCompileSpec, collector: ArgCollector) {
        for (arg in generate(spec)) {
            collector.args(arg)
        }
    }

    fun generate(spec: JavaCompileSpec): Iterable<String?> {
        val launcherOptions = JavaCompilerArgumentsBuilder(spec).includeLauncherOptions(true).includeMainOptions(false).includeClasspath(false).build()
        val remainingArgs = JavaCompilerArgumentsBuilder(spec).includeSourceFiles(true).build()
        return Iterables.concat<String?>(launcherOptions, shortenArgs(spec.tempDir, remainingArgs)!!)
    }

    private fun shortenArgs(tempDir: File?, args: MutableList<String?>): Iterable<String?>? {
        // for command file format, see http://docs.oracle.com/javase/6/docs/technotes/tools/windows/javac.html#commandlineargfile
        // use platform character and line encoding
        return ArgWriter.unixStyle().generateArgsFile(args, File(tempDir, "java-compiler-args.txt"))
    }
}
