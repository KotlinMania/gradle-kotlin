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
package org.gradle.internal.nativeintegration.processenvironment

import com.google.common.collect.Lists
import com.google.common.collect.Sets
import org.gradle.internal.nativeintegration.EnvironmentModificationResult
import org.gradle.internal.nativeintegration.NativeIntegrationException
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.internal.nativeintegration.ReflectiveEnvironment
import java.io.File

abstract class AbstractProcessEnvironment : ProcessEnvironment {
    //for updates to private JDK caches of the environment state
    private val reflectiveEnvironment = ReflectiveEnvironment()

    override fun maybeSetEnvironment(source: MutableMap<String?, String?>): EnvironmentModificationResult {
        // need to take copy to prevent ConcurrentModificationException
        val keysToRemove: MutableList<String?> = Lists.newArrayList<String?>(Sets.difference<String?>(System.getenv().keys, source.keys))
        for (key in keysToRemove) {
            removeEnvironmentVariable(key)
        }
        for (entry in source.entries) {
            setEnvironmentVariable(entry.key, entry.value)
        }
        return EnvironmentModificationResult.SUCCESS
    }

    @Throws(NativeIntegrationException::class)
    override fun removeEnvironmentVariable(name: String?) {
        removeNativeEnvironmentVariable(name)
        reflectiveEnvironment.unsetenv(name)
    }

    protected abstract fun removeNativeEnvironmentVariable(name: String?)

    override fun maybeRemoveEnvironmentVariable(name: String?): EnvironmentModificationResult {
        removeEnvironmentVariable(name)
        return EnvironmentModificationResult.SUCCESS
    }

    @Throws(NativeIntegrationException::class)
    override fun setEnvironmentVariable(name: String?, value: String?) {
        if (value == null) {
            removeEnvironmentVariable(name)
            return
        }

        setNativeEnvironmentVariable(name, value)
        reflectiveEnvironment.setenv(name, value)
    }

    protected abstract fun setNativeEnvironmentVariable(name: String?, value: String?)

    override fun maybeSetEnvironmentVariable(name: String?, value: String?): EnvironmentModificationResult {
        setEnvironmentVariable(name, value)
        return EnvironmentModificationResult.SUCCESS
    }

    @Throws(NativeIntegrationException::class)
    override fun setProcessDir(processDir: File) {
        setNativeProcessDir(processDir)
        System.setProperty("user.dir", processDir.getAbsolutePath())
    }

    protected abstract fun setNativeProcessDir(processDir: File?)

    override fun maybeSetProcessDir(processDir: File): Boolean {
        setProcessDir(processDir)
        return true
    }

    override fun maybeGetPid(): Long? {
        return pid
    }

    override fun maybeDetachProcess(): Boolean {
        detachProcess()
        return true
    }
}
