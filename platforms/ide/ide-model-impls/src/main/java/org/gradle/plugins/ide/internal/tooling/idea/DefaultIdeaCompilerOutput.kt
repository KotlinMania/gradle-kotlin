/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.internal.tooling.idea

import org.gradle.tooling.model.idea.IdeaCompilerOutput
import java.io.File
import java.io.Serializable

class DefaultIdeaCompilerOutput : IdeaCompilerOutput, Serializable {
    private var inheritOutputDirs = false
    private var outputDir: File? = null
    private var testOutputDir: File? = null

    override fun getInheritOutputDirs(): Boolean {
        return inheritOutputDirs
    }

    fun setInheritOutputDirs(inheritOutputDirs: Boolean): DefaultIdeaCompilerOutput {
        this.inheritOutputDirs = inheritOutputDirs
        return this
    }

    override fun getOutputDir(): File? {
        return outputDir
    }

    fun setOutputDir(outputDir: File?): DefaultIdeaCompilerOutput {
        this.outputDir = outputDir
        return this
    }

    override fun getTestOutputDir(): File? {
        return testOutputDir
    }

    fun setTestOutputDir(testOutputDir: File?): DefaultIdeaCompilerOutput {
        this.testOutputDir = testOutputDir
        return this
    }

    override fun toString(): String {
        return ("IdeaCompilerOutput{"
                + "inheritOutputDirs=" + inheritOutputDirs
                + ", outputDir=" + outputDir
                + ", testOutputDir=" + testOutputDir
                + '}')
    }
}
