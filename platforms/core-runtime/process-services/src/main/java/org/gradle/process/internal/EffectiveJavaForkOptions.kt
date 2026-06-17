/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.process.internal

import com.google.common.collect.Maps
import org.gradle.internal.file.PathToFileResolver
import org.gradle.process.ProcessForkOptions
import java.io.File

open class DefaultProcessForkOptions(protected val resolver: PathToFileResolver) : ProcessForkOptions {
    private var executable: Any? = null
    private var workingDir: File? = null
    private var environment: MutableMap<String?, Any?>? = null

    override fun getExecutable(): String? {
        return if (executable == null) null else executable.toString()
    }

    override fun setExecutable(executable: String?) {
        this.executable = executable
    }

    override fun setExecutable(executable: Any?) {
        this.executable = executable
    }

    override fun executable(executable: Any?): ProcessForkOptions {
        setExecutable(executable)
        return this
    }

    override fun getWorkingDir(): File? {
        if (workingDir == null) {
            workingDir = resolver.resolve(".")
        }
        return workingDir
    }

    override fun setWorkingDir(dir: File?) {
        this.workingDir = resolver.resolve(dir)
    }

    override fun setWorkingDir(dir: Any?) {
        this.workingDir = resolver.resolve(dir)
    }

    override fun workingDir(dir: Any?): ProcessForkOptions {
        setWorkingDir(dir)
        return this
    }

    override fun getEnvironment(): MutableMap<String?, Any?>? {
        if (environment == null) {
            setEnvironment(this.inheritableEnvironment!!)
        }
        return environment
    }

    protected open val inheritableEnvironment: MutableMap<String?, *>?
        get() = System.getenv()

    val actualEnvironment: MutableMap<String?, String?>
        get() = getActualEnvironment(this)

    override fun setEnvironment(environmentVariables: MutableMap<String?, *>) {
        environment = Maps.newHashMap<String?, Any?>(environmentVariables)
    }

    override fun environment(name: String?, value: Any?): ProcessForkOptions {
        getEnvironment()!!.put(name, value)
        return this
    }

    override fun environment(environmentVariables: MutableMap<String?, *>): ProcessForkOptions {
        getEnvironment()!!.putAll(environmentVariables)
        return this
    }

    override fun copyTo(target: ProcessForkOptions): ProcessForkOptions {
        target.setExecutable(executable)
        target.setWorkingDir(getWorkingDir())
        target.setEnvironment(getEnvironment())
        return this
    }

    companion object {
        @JvmStatic
        fun getActualEnvironment(forkOptions: ProcessForkOptions): MutableMap<String?, String?> {
            val actual: MutableMap<String?, String?> = HashMap<String?, String?>()
            for (entry in forkOptions.getEnvironment().entries) {
                actual.put(entry.key, entry.value.toString())
            }
            return actual
        }
    }
}
