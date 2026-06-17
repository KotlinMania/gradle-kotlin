/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.jvm.toolchain.internal

import net.rubygrapefruit.platform.MissingRegistryEntryException
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.nativeintegration.NativeIntegrationUnavailableException
import org.gradle.internal.os.OperatingSystem
import java.io.File
import java.util.stream.Collectors
import java.util.stream.Stream

class WindowsInstallationSupplier(private val windowsRegistry: WindowsRegistry, private val os: OperatingSystem) : InstallationSupplier {
    override fun getSourceName(): String {
        return "Windows Registry"
    }

    override fun get(): MutableSet<InstallationLocation?>? {
        if (os.isWindows) {
            return findInstallationsInRegistry()
        }
        return mutableSetOf<InstallationLocation?>()
    }

    private fun findInstallationsInRegistry(): MutableSet<InstallationLocation?> {
        val openJdkInstallations = findOpenJDKs()
        val jvms = Stream.of<String?>(
            "SOFTWARE\\JavaSoft\\JDK",
            "SOFTWARE\\JavaSoft\\Java Development Kit",
            "SOFTWARE\\JavaSoft\\Java Runtime Environment",
            "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Development Kit",
            "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Runtime Environment"
        ).map<MutableList<String?>?> { sdkSubkey: String? -> this.findJvms(sdkSubkey) }.flatMap<String?> { obj: MutableList<String?>? -> obj!!.stream() }
        return Stream.concat<String?>(openJdkInstallations, jvms)
            .map<InstallationLocation?> { javaHome: String? -> InstallationLocation.Companion.autoDetected(File(javaHome), getSourceName()) }
            .collect(Collectors.toSet())
    }

    private fun find(sdkSubkey: String?, path: String?, value: String?): MutableList<String?> {
        try {
            val versions = getVersions(sdkSubkey)
            return versions.stream().map<String?> { version: String? -> getValue(sdkSubkey, path, value, version) }.collect(Collectors.toList())
        } catch (e: MissingRegistryEntryException) {
            // Ignore
            return mutableListOf<String?>()
        } catch (e: NativeIntegrationUnavailableException) {
            return mutableListOf<String?>()
        }
    }

    private fun getVersions(sdkSubkey: String?): MutableList<String?> {
        return windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, sdkSubkey)
    }

    private fun getValue(sdkSubkey: String?, path: String?, value: String?, version: String?): String? {
        return windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, sdkSubkey + '\\' + version + path, value)
    }

    private fun findOpenJDKs(): Stream<String?> {
        return Stream.of<String?>(
            "SOFTWARE\\AdoptOpenJDK\\JDK",
            "SOFTWARE\\Eclipse Adoptium\\JDK",
            "SOFTWARE\\Eclipse Foundation\\JDK"
        ).flatMap<String?> { key: String? -> find(key, "\\hotspot\\MSI", "Path").stream() }
    }

    private fun findJvms(sdkSubkey: String?): MutableList<String?> {
        return find(sdkSubkey, "", "JavaHome")
    }
}
