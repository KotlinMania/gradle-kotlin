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
package org.gradle.nativeplatform.toolchain.internal

import org.gradle.api.Action
import org.gradle.internal.Actions
import org.gradle.internal.operations.logging.BuildOperationLogger
import java.io.File

class DefaultMutableCommandLineToolContext : MutableCommandLineToolContext {
    private var postArgsAction: Action<MutableList<String?>?>? = Actions.doNothing<MutableList<String?>?>()
    private val environment: MutableMap<String?, String?> = HashMap<String?, String?>()
    private val path: MutableList<File?> = ArrayList<File?>()

    override fun setArgAction(argAction: Action<MutableList<String?>?>?) {
        postArgsAction = argAction
    }

    override fun addPath(pathEntry: File?) {
        this.path.add(pathEntry)
    }

    override fun addPath(path: MutableList<File?>) {
        this.path.addAll(path)
    }

    override fun getPath(): MutableList<File?> {
        return path
    }

    override fun getEnvironment(): MutableMap<String?, String?> {
        return environment
    }

    override fun getArgAction(): Action<MutableList<String?>?>? {
        return postArgsAction
    }

    override fun addEnvironmentVar(key: String?, value: String?) {
        this.environment.put(key, value)
    }

    override fun createInvocation(description: String?, workDirectory: File?, args: Iterable<String?>?, oplogger: BuildOperationLogger?): CommandLineToolInvocation {
        return DefaultCommandLineToolInvocation(description, workDirectory, args, this, oplogger)
    }

    override fun createInvocation(description: String?, args: Iterable<String?>?, oplogger: BuildOperationLogger?): CommandLineToolInvocation {
        val currentWorkingDirectory: File? = null
        return createInvocation(description, currentWorkingDirectory, args, oplogger)
    }
}
