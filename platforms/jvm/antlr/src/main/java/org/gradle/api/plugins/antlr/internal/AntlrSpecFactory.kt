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
package org.gradle.api.plugins.antlr.internal

import com.google.common.collect.Lists
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.antlr.AntlrTask
import org.gradle.internal.deprecation.DeprecationLogger.deprecateAction
import java.io.File

class AntlrSpecFactory {
    fun create(antlrTask: AntlrTask, grammarFiles: MutableSet<File?>?, sourceSetDirectories: FileCollection?): AntlrSpec {
        var outputDirectory = antlrTask.getOutputDirectory()
        val arguments: MutableList<String?> = Lists.newLinkedList<String?>(antlrTask.getArguments())

        if (antlrTask.isTrace() && !arguments.contains("-trace")) {
            arguments.add("-trace")
        }
        if (antlrTask.isTraceLexer() && !arguments.contains("-traceLexer")) {
            arguments.add("-traceLexer")
        }
        if (antlrTask.isTraceParser() && !arguments.contains("-traceParser")) {
            arguments.add("-traceParser")
        }
        if (antlrTask.isTraceTreeWalker() && !arguments.contains("-traceTreeWalker")) {
            arguments.add("-traceTreeWalker")
        }
        if (antlrTask.getArguments().contains(AntlrSpec.Companion.PACKAGE_ARG)) {
            deprecateAction("Setting the '" + AntlrSpec.Companion.PACKAGE_ARG + "' argument directly on AntlrTask")
                .withAdvice("Use the 'packageName' property of the AntlrTask to specify the package name instead of using the '" + org.gradle.api.plugins.antlr.internal.AntlrSpec.Companion.PACKAGE_ARG + "' argument.")!!
                .willBecomeAnErrorInGradle10()
                .withDslReference(org.gradle.api.plugins.antlr.AntlrTask::class.java, "packageName")!!
                .nagUser()
        }
        if (antlrTask.getPackageName().isPresent()) {
            if (!arguments.contains(AntlrSpec.Companion.PACKAGE_ARG)) {
                arguments.add(AntlrSpec.Companion.PACKAGE_ARG)
                arguments.add(antlrTask.getPackageName().get())
                outputDirectory = File(outputDirectory, antlrTask.getPackageName().get().replace('.', '/'))
            } else {
                throw IllegalStateException("The package has been set both in the arguments (i.e. '" + AntlrSpec.Companion.PACKAGE_ARG + "') and via the 'packageName' property.  Please set the package only using the 'packageName' property.")
            }
        }
        val sourceSetDirectoriesFiles: MutableSet<File?>?
        if (sourceSetDirectories == null) {
            sourceSetDirectoriesFiles = mutableSetOf<File?>()
        } else {
            sourceSetDirectoriesFiles = sourceSetDirectories.getFiles()
        }

        return AntlrSpec(arguments, grammarFiles, sourceSetDirectoriesFiles, outputDirectory, antlrTask.getMaxHeapSize())
    }
}
