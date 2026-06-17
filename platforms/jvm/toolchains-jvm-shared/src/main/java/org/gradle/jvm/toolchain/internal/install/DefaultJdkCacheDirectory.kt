/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.jvm.toolchain.internal.install

import com.google.common.annotations.VisibleForTesting
import org.apache.commons.io.FilenameUtils
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.cache.FileLock
import org.gradle.cache.FileLockManager
import org.gradle.cache.internal.filelock.DefaultLockOptions
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.internal.RenderingUtils
import org.gradle.internal.jvm.inspection.JavaInstallationCapability
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmMetadataDetector
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.InstallationLocation.Companion.autoProvisioned
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Arrays
import java.util.stream.Collectors

class DefaultJdkCacheDirectory(
    homeDirProvider: GradleUserHomeDirProvider,
    private val operations: FileOperations,
    private val lockManager: FileLockManager,
    private val detector: JvmMetadataDetector,
// Specifically requesting the GradleUserHomeTemporaryFileProvider to ensure that the temporary files are created on the same file system as the target directory
    // This is a prerequisite for atomic moves in most cases, which are used in the provisionFromArchive method
    private val temporaryFileProvider: GradleUserHomeTemporaryFileProvider
) : JdkCacheDirectory {
    private class UnpackedRoot(private val dir: File, private val metadata: JvmInstallationMetadata)

    val downloadLocation: File

    override fun listJavaHomes(): MutableSet<File> {
        val candidates = downloadLocation.listFiles()
        if (candidates != null) {
            return Arrays.stream<File>(candidates)
                .filter { candidate: File? -> this.isMarkedLocation(candidate!!) }
                .map<File> { location: File? -> this.getJavaHome(location!!) }
                .collect(Collectors.toSet())
        }
        return mutableSetOf<File>()
    }

    private fun isMarkedLocation(candidate: File): Boolean {
        return File(candidate, MARKER_FILE).exists()
    }

    private fun getJavaHome(location: File): File {
        if (current()!!.isMacOsX) {
            if (File(location, MAC_OS_JAVA_HOME_FOLDER).exists()) {
                return File(location, MAC_OS_JAVA_HOME_FOLDER)
            }

            val subfolders = location.listFiles(FileFilter { obj: File? -> obj!!.isDirectory() })
            if (subfolders != null) {
                for (subfolder in subfolders) {
                    if (File(subfolder, MAC_OS_JAVA_HOME_FOLDER).exists()) {
                        return File(subfolder, MAC_OS_JAVA_HOME_FOLDER)
                    }
                }
            }
        }

        return location
    }

    /**
     * Unpacks and installs the given JDK archive. Returns a file pointing to the java home directory.
     */
    @Throws(IOException::class)
    fun provisionFromArchive(spec: JavaToolchainSpec, jdkArchive: File, uri: URI): File {
        // Unpack into temporary directory (but on same file system as our target directory location)
        val unpackFolder = unpack(jdkArchive)
        try {
            // Get the folder that is the real root of the unpacked JDK, skipping any archive root folder
            val unpackedRoot = determineUnpackedRoot(unpackFolder)

            validateMetadataMatchesSpec(spec, uri, unpackedRoot.metadata)

            // Check our target directory, to see if anything exists there, and if so, is it marked as ready?
            val installFolder = File(this.downloadLocation, getInstallFolderName(unpackedRoot.metadata))
            if (!installFolder.getParentFile().mkdirs() && !installFolder.getParentFile().isDirectory()) {
                throw IOException("Failed to create install parent directory: " + installFolder.getParentFile())
            }
            acquireWriteLock(File(installFolder.getParentFile(), installFolder.getName() + ".reserved"), "Provisioning JDK from " + uri).use { ignored ->
                if (installFolder.exists()) {
                    if (isMarkedLocation(installFolder)) {
                        LOGGER.info("Toolchain from {} already installed at {}", uri, installFolder)
                        return getJavaHome(installFolder)
                    } else {
                        // This can happen if atomic moves are unsupported, and the JVM is forcibly killed during the copy
                        LOGGER.info("Found partially installed toolchain at {}, overwriting with toolchain from {}", installFolder, uri)
                        operations.delete(installFolder)
                    }
                }
                // Move the unpacked root to the install location, atomically if possible
                try {
                    Files.move(unpackedRoot.dir.toPath(), installFolder.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (e: AtomicMoveNotSupportedException) {
                    // In theory, we should never hit this code, but some more obscure file systems or OSes may not support atomic moves
                    LOGGER.info("Failed to use an atomic move for unpacked JDK from {} to {}. Will try to copy instead.", unpackedRoot.dir, installFolder, e)
                    try {
                        operations.copy(Action { copySpec: CopySpec? ->
                            copySpec!!.from(unpackedRoot.dir)
                            copySpec.into(installFolder)
                        })
                    } catch (t: Throwable) {
                        deleteWithoutThrowing(t, installFolder)
                        throw t
                    }
                }

                // Now that the JDK is installed, mark it as ready
                try {
                    markAsReady(installFolder)
                } catch (t: Throwable) {
                    deleteWithoutThrowing(t, installFolder)
                    throw t
                }
            }
            return getJavaHome(installFolder)
        } finally {
            try {
                operations.delete(unpackFolder)
            } catch (t: Throwable) {
                // Prevent Throwables from masking the original exception
                LOGGER.warn("Failed to delete temporary unpack folder: " + unpackFolder, t)
            }
        }
    }

    private fun deleteWithoutThrowing(t: Throwable, installFolder: File) {
        try {
            operations.delete(installFolder)
        } catch (t2: Throwable) {
            t.addSuppressed(t2)
        }
    }

    private fun determineUnpackedRoot(unpackFolder: File): UnpackedRoot {
        var uncheckedMetadata = getUncheckedMetadata(unpackFolder)
        if (uncheckedMetadata.isValidInstallation) {
            return UnpackedRoot(unpackFolder, uncheckedMetadata)
        }
        val subFolders = unpackFolder.listFiles(FileFilter { obj: File? -> obj!!.isDirectory() })
        checkNotNull(subFolders) { "Unpacked JDK archive is not a directory: " + unpackFolder }
        for (subFolder in subFolders) {
            uncheckedMetadata = getUncheckedMetadata(subFolder)
            if (uncheckedMetadata.isValidInstallation) {
                return UnpackedRoot(subFolder, uncheckedMetadata)
            }
        }
        throw IllegalStateException("Unpacked JDK archive does not contain a Java home: " + unpackFolder, uncheckedMetadata.errorCause)
    }

    private fun getUncheckedMetadata(root: File): JvmInstallationMetadata {
        val javaHome = getJavaHome(root)
        return detector.getMetadata(autoProvisioned(javaHome, "provisioned toolchain"))!!
    }

    /**
     * Creates a new JDK cache directory manager.
     *
     * @param homeDirProvider provider for the Gradle user home directory information
     * @param operations file operations
     * @param lockManager lock manager
     * @param detector JVM metadata detector, an instance of [org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector] should be passed to avoid logging of any kind
     * @param temporaryFileProvider temporary file provider
     */
    init {
        this.downloadLocation = File(homeDirProvider.getGradleUserHomeDirectory(), "jdks")
        this.temporaryFileProvider = temporaryFileProvider
    }

    private fun unpack(jdkArchive: File): File {
        val fileTree = asFileTree(jdkArchive)
        val unpackFolderName: String = getNameWithoutExtension(jdkArchive)
        val unpackFolder = temporaryFileProvider.createTemporaryDirectory(unpackFolderName, null, "jdks")
        unpackFolder.deleteOnExit()
        operations.copy(Action { spec: CopySpec? ->
            spec!!.from(fileTree)
            spec.into(unpackFolder)
            spec.setDuplicatesStrategy(DuplicatesStrategy.WARN)
        })

        return unpackFolder
    }

    private fun markAsReady(root: File) {
        try {
            // Create the legacy marker so that older Gradle versions can use this JDK as well.
            File(root, LEGACY_MARKER_FILE).createNewFile()
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to create " + LEGACY_MARKER_FILE + " file", e)
        }
        try {
            File(root, MARKER_FILE).createNewFile()
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to create " + MARKER_FILE + " file", e)
        }
    }

    private fun asFileTree(jdkArchive: File): FileTree {
        val extension = FilenameUtils.getExtension(jdkArchive.getName())
        if (extension == "zip") {
            return operations.zipTree(jdkArchive)
        }
        return operations.tarTree(operations.getResources().gzip(jdkArchive))
    }

    fun acquireWriteLock(destinationFile: File, operationName: String): FileLock {
        return lockManager.lock(destinationFile, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), destinationFile.getName(), operationName)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultJdkCacheDirectory::class.java)

        /**
         * Marker file used by Gradle 8.8 and earlier to indicate that a JDK has been provisioned. This is a flaky marker, as it may appear
         * before the JDK is fully provisioned, causing faulty detection of the JDK. It is replaced by {@value #MARKER_FILE}.
         */
        @VisibleForTesting
        const val LEGACY_MARKER_FILE: String = "provisioned.ok"

        @VisibleForTesting
        const val MARKER_FILE: String = ".ready"
        private const val MAC_OS_JAVA_HOME_FOLDER = "Contents/Home"

        private val JDK_CAPABILITIES_DISPLAY: String = JavaInstallationCapability.JDK_CAPABILITIES.stream()
            .map<String> { cap: JavaInstallationCapability? -> "the " + cap!!.toDisplayName() }
            .collect(RenderingUtils.oxfordJoin("and"))

        /**
         * Validates that the metadata of the provisioned JDK matches the specification. This also requires [JavaInstallationCapability.JDK_CAPABILITIES] to be present.
         *
         * @param spec the specification to validate against
         * @param uri the URI of the JDK archive
         * @param metadata the metadata of the provisioned JDK
         */
        private fun validateMetadataMatchesSpec(spec: JavaToolchainSpec, uri: URI, metadata: JvmInstallationMetadata) {
            if (!JvmInstallationMetadataMatcher(spec, JavaInstallationCapability.JDK_CAPABILITIES).test(metadata)) {
                // Log the metadata for debugging purposes
                LOGGER.info("Provisioned JDK from '{}' does not satisfy the specification {} with metadata {} and capabilities {}", uri, spec.getDisplayName(), metadata, metadata.capabilities)
                // Make a readable version of the capabilities for the
                throw GradleException("Toolchain provisioned from '" + uri + "' doesn't satisfy the specification: " + spec.getDisplayName() + " and must have " + JDK_CAPABILITIES_DISPLAY + ".")
            }
        }

        fun getInstallFolderName(metadata: JvmInstallationMetadata): String {
            var vendor = metadata.jvmVendor
            if (vendor == null || vendor.isEmpty()) {
                vendor = metadata.vendor!!.rawVendor
            }
            val version = metadata.javaMajorVersion
            val architecture = metadata.architecture
            val os = current()!!.familyName
            return String.format("%s-%d-%s-%s", vendor, version, architecture, os).replace("[^a-zA-Z0-9\\-]".toRegex(), "_")
                .lowercase() + ".2"
        }

        private fun getNameWithoutExtension(file: File): String {
            //remove all extensions, for example for xxx.tar.gz files only xxx should be left
            var output = file.getName()
            var input: String?
            do {
                input = output
                output = com.google.common.io.Files.getNameWithoutExtension(input)
            } while (input != output)
            return output
        }
    }
}
