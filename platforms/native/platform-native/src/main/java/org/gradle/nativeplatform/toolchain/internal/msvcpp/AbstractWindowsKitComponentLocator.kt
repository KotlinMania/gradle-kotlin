/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.msvcpp

import com.google.common.collect.HashMultimap
import com.google.common.collect.Lists
import com.google.common.collect.SetMultimap
import net.rubygrapefruit.platform.MissingRegistryEntryException
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.FileUtils
import org.gradle.platform.base.internal.toolchain.ComponentFound
import org.gradle.platform.base.internal.toolchain.ComponentNotFound
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.VersionNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileFilter
import java.util.SortedSet
import java.util.TreeSet
import java.util.function.Function
import java.util.regex.Pattern

abstract class AbstractWindowsKitComponentLocator<T : WindowsKitInstall?> internal constructor(private val windowsRegistry: WindowsRegistry) : WindowsComponentLocator<T?> {
    private val foundComponents: SetMultimap<File?, T?> = HashMultimap.create<File?, T?>()
    private val brokenComponents: MutableSet<File?> = LinkedHashSet<File?>()
    private var initialised = false

    protected enum class DiscoveryType {
        REGISTRY, USER
    }

    private val windowsKitVersionPattern: Pattern = Pattern.compile("[0-9]+(\\.[0-9]+)*")
    private val windowsKitVersionFilter: FileFilter = object : FileFilter {
        override fun accept(pathname: File): Boolean {
            val matcher = windowsKitVersionPattern.matcher(pathname.getName())
            return pathname.isDirectory() && matcher.matches()
        }
    }

    override fun locateComponent(candidate: File?): SearchResult<T?> {
        initializeComponents()

        if (candidate != null) {
            return locateUserSpecifiedComponent(candidate)
        }

        return locateDefaultComponent()
    }

    override fun locateAllComponents(): MutableList<T?>? {
        initializeComponents()

        return Lists.newArrayList<T?>(foundComponents.values())
    }

    private fun initializeComponents() {
        if (!initialised) {
            locateComponentsInRegistry()
            initialised = true
        }
    }

    private fun locateDefaultComponent(): SearchResult<T?> {
        val selected = this.bestComponent
        if (selected != null) {
            return ComponentFound<T?>(selected)
        }
        if (brokenComponents.isEmpty()) {
            return ComponentNotFound<T?>("Could not locate a " + this.displayName + " installation using the Windows registry.")
        }
        return ComponentNotFound<T?>(
            "Could not locate a " + this.displayName + " installation. None of the following locations contain a valid installation",
            CollectionUtils.collect<String?, File?>(brokenComponents, Function { obj: File? -> obj!!.getAbsolutePath() })
        )
    }

    private val bestComponent: T?
        get() {
            val candidates: SortedSet<T?> =
                TreeSet<T?>(AbstractWindowsKitComponentLocator.DescendingComponentVersionComparator())
            candidates.addAll(foundComponents.values())
            return if (candidates.isEmpty()) null else candidates.iterator().next()
        }

    private fun locateComponentsInRegistry() {
        for (baseKey in REGISTRY_BASEPATHS) {
            locateComponentsInRegistry(baseKey)
        }
    }

    private fun locateComponentsInRegistry(baseKey: String?) {
        try {
            val windowsKitDir = FileUtils.canonicalize(File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_KIT, REGISTRY_KIT_10)))
            val found = findIn(windowsKitDir, DiscoveryType.REGISTRY)
            if (found.isEmpty()) {
                brokenComponents.add(windowsKitDir)
            }
            for (t in found) {
                foundComponents.put(t!!.baseDir, t)
            }
        } catch (e: MissingRegistryEntryException) {
            // Ignore the version if the string cannot be read
        }
    }

    private fun findIn(windowsKitDir: File?, discoveryType: DiscoveryType?): MutableSet<T?> {
        val found: MutableSet<T?> = LinkedHashSet<T?>()
        val versionDirs = getComponentVersionDirs(windowsKitDir)
        for (versionDir in versionDirs) {
            val version = VersionNumber.withPatchNumber().parse(versionDir)
            LOGGER.debug("Found {} {} at {}", this.displayName, version.toString(), windowsKitDir)
            val binDir = File(windowsKitDir, "bin/" + versionDir)
            val unversionedBinDir = File(windowsKitDir, "bin")
            if (isValidComponentBinDir(binDir)) {
                val component = newComponent(windowsKitDir, binDir, version, discoveryType)
                found.add(component)
            } else if (isValidComponentBinDir(unversionedBinDir)) {
                val component = newComponent(windowsKitDir, unversionedBinDir, version, discoveryType)
                found.add(component)
            }
        }
        if (found.isEmpty()) {
            LOGGER.debug(
                "Ignoring candidate directory {} as it does not look like a {} installation.", windowsKitDir,
                this.displayName
            )
        }
        return found
    }

    private fun locateUserSpecifiedComponent(candidate: File): SearchResult<T?> {
        val windowsKitDir = FileUtils.canonicalize(candidate)
        var candidates = foundComponents.get(windowsKitDir)
        if (candidates.isEmpty()) {
            candidates = findIn(windowsKitDir, DiscoveryType.USER)
        }
        if (candidates.isEmpty()) {
            return ComponentNotFound<T?>(
                String.format(
                    "The specified installation directory '%s' does not appear to contain a %s installation.", candidate,
                    this.displayName
                )
            )
        }

        val found: MutableSet<T?> = TreeSet<T?>(AbstractWindowsKitComponentLocator.DescendingComponentVersionComparator())
        found.addAll(candidates)
        return ComponentFound<T?>(found.iterator().next())
    }

    private fun getComponentVersionDirs(candidate: File?): Array<String?> {
        val includeDir = File(candidate, "Include")
        val libDir = File(candidate, "Lib")
        if (!includeDir.isDirectory() || !libDir.isDirectory()) {
            return arrayOfNulls<String>(0)
        }

        val includeDirs: MutableMap<String?, File?> = HashMap<String?, File?>()
        for (dir in includeDir.listFiles(windowsKitVersionFilter)) {
            includeDirs.put(dir.getName(), dir)
        }
        val libDirs: MutableMap<String?, File?> = HashMap<String?, File?>()
        for (dir in libDir.listFiles(windowsKitVersionFilter)) {
            libDirs.put(dir.getName(), dir)
        }
        val kitVersions: MutableSet<String?> = HashSet<String?>()
        kitVersions.addAll(includeDirs.keys)
        kitVersions.addAll(libDirs.keys)

        val result: MutableList<String?> = ArrayList<String?>()
        for (version in kitVersions) {
            val inc = includeDirs.get(version)
            val lib = libDirs.get(version)
            if (inc != null && lib != null) {
                val componentInc = File(inc, this.componentName)
                val componentLib = File(lib, this.componentName)
                if (isValidComponentIncludeDir(componentInc) && isValidComponentLibDir(componentLib)) {
                    result.add(version)
                }
            }
        }
        return result.toTypedArray<String?>()
    }

    protected fun getVersionedDisplayName(version: VersionNumber, discoveryType: DiscoveryType): String {
        when (discoveryType) {
            DiscoveryType.USER -> return USER_PROVIDED + " " + this.displayName + " " + version.getMajor()
            DiscoveryType.REGISTRY -> return this.displayName + " " + version.getMajor()
            else -> throw IllegalArgumentException("Unknown discovery method for " + this.displayName + ": " + discoveryType)
        }
    }

    abstract val componentName: String?

    abstract val displayName: String?

    abstract fun isValidComponentBinDir(binDir: File?): Boolean

    abstract fun isValidComponentIncludeDir(includeDir: File?): Boolean

    abstract fun isValidComponentLibDir(libDir: File?): Boolean

    abstract fun newComponent(baseDir: File?, binDir: File?, version: VersionNumber?, discoveryType: DiscoveryType?): T?

    private inner class DescendingComponentVersionComparator : Comparator<T?> {
        override fun compare(o1: T?, o2: T?): Int {
            return o2!!.version.compareTo(o1!!.version)
        }
    }

    companion object {
        val PLATFORMS: Array<String?> = arrayOf<String>("x86", "x64")

        private const val USER_PROVIDED = "User-provided"
        private val LOGGER: Logger = LoggerFactory.getLogger(AbstractWindowsKitComponentLocator::class.java)
        private val REGISTRY_BASEPATHS = arrayOf<String?>(
            "SOFTWARE\\",
            "SOFTWARE\\Wow6432Node\\"
        )
        private const val REGISTRY_ROOTPATH_KIT = "Microsoft\\Windows Kits\\Installed Roots"
        private const val REGISTRY_KIT_10 = "KitsRoot10"
    }
}
