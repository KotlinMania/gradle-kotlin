/*
 * Copyright 2014 the original author or authors.
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
import java.io.File
import java.io.Serializable

class AntlrSpec(val arguments: MutableList<String?>, val grammarFiles: MutableSet<File>?, val inputDirectories: MutableSet<File?>?, val outputDirectory: File?, val maxHeapSize: String?) :
    Serializable {
    fun asArgumentsWithFiles(): MutableList<String?> {
        val commandLine: MutableList<String?> = Lists.newLinkedList<String?>(arguments)
        commandLine.add("-o")
        commandLine.add(this.outputDirectory!!.getAbsolutePath())
        for (file in this.grammarFiles!!) {
            commandLine.add(file.getAbsolutePath())
        }

        return commandLine
    }

    companion object {
        const val PACKAGE_ARG: String = "-package"
    }
}
