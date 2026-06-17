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
package org.gradle.internal

import com.google.common.base.Objects
import com.google.common.collect.Lists
import org.gradle.TaskExecutionRequest
import java.io.File
import java.io.Serializable

class DefaultTaskExecutionRequest @JvmOverloads constructor(args: Iterable<String?>, private val projectPath: String? = null, private val rootDir: File? = null) : TaskExecutionRequest, Serializable {
    private val args: MutableList<String?>

    init {
        this.args = Lists.newArrayList<String?>(args)
        // Use RunDefaultTasksExecutionRequest instead
        assert(!this.args.isEmpty())
    }

    override fun getArgs(): MutableList<String?> {
        return args
    }

    override fun getProjectPath(): String? {
        return projectPath
    }

    override fun getRootDir(): File? {
        return rootDir
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultTaskExecutionRequest
        if (!Objects.equal(projectPath, that.projectPath)) {
            return false
        }
        if (!Objects.equal(args, that.args)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = getArgs().hashCode()
        result = 31 * result + (if (projectPath != null) projectPath.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        return ("DefaultTaskExecutionRequest{"
                + "args=" + args
                + ",projectPath='" + projectPath + '\''
                + ",rootDir='" + rootDir + '\''
                + '}')
    }

    companion object {
        @JvmOverloads
        fun of(args: Iterable<String?>, projectPath: String? = null, rootDir: File? = null): TaskExecutionRequest {
            if (args.iterator().hasNext()) {
                return DefaultTaskExecutionRequest(args, projectPath, rootDir)
            } else {
                return RunDefaultTasksExecutionRequest(projectPath, rootDir)
            }
        }
    }
}
