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
package org.gradle.internal.nativeintegration.jna

import org.gradle.internal.nativeintegration.EnvironmentModificationResult
import org.gradle.internal.nativeintegration.NativeIntegrationException
import org.gradle.internal.nativeintegration.NativeIntegrationUnavailableException
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.internal.os.OperatingSystem
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory

class UnsupportedEnvironment : ProcessEnvironment {
    private val pid: Long?

    init {
        pid = extractPIDFromRuntimeMXBeanName()
    }

    /**
     * The default format of the name of the Runtime MX bean is PID@HOSTNAME.
     * The PID is parsed assuming that is the format.
     *
     * This works on Solaris and should work with any Java VM
     */
    private fun extractPIDFromRuntimeMXBeanName(): Long? {
        var pid: Long? = null
        val runtimeMXBeanName = ManagementFactory.getRuntimeMXBean().getName()
        val separatorPos = runtimeMXBeanName.indexOf('@')
        if (separatorPos > -1) {
            try {
                pid = runtimeMXBeanName.substring(0, separatorPos).toLong()
            } catch (e: NumberFormatException) {
                LOGGER.debug("Native-platform process: failed to parse PID from Runtime MX bean name: " + runtimeMXBeanName)
            }
        } else {
            LOGGER.debug("Native-platform process: failed to parse PID from Runtime MX bean name")
        }
        return pid
    }

    override fun maybeSetEnvironment(source: MutableMap<String?, String?>?): EnvironmentModificationResult {
        return EnvironmentModificationResult.UNSUPPORTED_ENVIRONMENT
    }

    @Throws(NativeIntegrationException::class)
    override fun removeEnvironmentVariable(name: String?) {
        throw notSupported()
    }

    override fun maybeRemoveEnvironmentVariable(name: String?): EnvironmentModificationResult {
        return EnvironmentModificationResult.UNSUPPORTED_ENVIRONMENT
    }

    @Throws(NativeIntegrationException::class)
    override fun setEnvironmentVariable(name: String?, value: String?) {
        throw notSupported()
    }

    override fun maybeSetEnvironmentVariable(name: String?, value: String?): EnvironmentModificationResult {
        return EnvironmentModificationResult.UNSUPPORTED_ENVIRONMENT
    }

    @Throws(NativeIntegrationException::class)
    override fun getProcessDir(): File? {
        throw notSupported()
    }

    @Throws(NativeIntegrationException::class)
    override fun setProcessDir(processDir: File?) {
        throw notSupported()
    }

    override fun maybeSetProcessDir(processDir: File?): Boolean {
        return false
    }

    @Throws(NativeIntegrationException::class)
    override fun getPid(): Long {
        if (pid != null) {
            return pid
        }
        throw notSupported()
    }

    override fun maybeGetPid(): Long? {
        return pid
    }

    override fun maybeDetachProcess(): Boolean {
        return false
    }

    override fun detachProcess() {
        throw notSupported()
    }

    private fun notSupported(): NativeIntegrationException {
        return NativeIntegrationUnavailableException("We don't support this operating system: " + OperatingSystem.current())
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(UnsupportedEnvironment::class.java)
    }
}
