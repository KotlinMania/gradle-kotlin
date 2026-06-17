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
package org.gradle.jvm.tasks

import com.google.common.collect.ImmutableList
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.internal.CustomManifestInternalWrapper
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.java.archives.internal.ManifestInternal
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.serialization.Cached
import org.gradle.util.internal.ConfigureUtil
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.OutputStream
import java.nio.charset.Charset
import javax.inject.Inject

/**
 * Assembles a JAR archive.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class Jar @Inject constructor() : Zip() {
    /**
     * Returns the manifest for this JAR archive.
     *
     * @return The manifest
     */
    /**
     * Sets the manifest for this JAR archive.
     *
     * @param manifest The manifest. May be null.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var manifest: Manifest?
    private val metaInf: CopySpecInternal
    private val manifestContentCharset: Property<String>

    init {
        getArchiveExtension().set(DEFAULT_EXTENSION)
        setMetadataCharset("UTF-8")

        this.manifestContentCharset = getObjectFactory().property<String?>(String::class.java).convention(ManifestInternal.Companion.DEFAULT_CONTENT_CHARSET)
        manifest = DefaultManifest(getFileResolver(), manifestContentCharset)
        // Add these as separate specs, so they are not affected by the changes to the main spec
        metaInf = getRootSpec().addFirst().into("META-INF") as CopySpecInternal
        metaInf.addChild().from(manifestFileTree())
        getMainSpec().appendCachingSafeCopyAction(ExcludeManifestAction())
    }

    private fun manifestFileTree(): FileTreeInternal {
        val manifest = Cached.of({ this.computeManifest() })
        val outputChangeListener = outputChangeListener()
        return fileCollectionFactory()!!.generated(
            getTemporaryDirFactory(),
            "MANIFEST.MF",
            SerializableLambdas.action<File?>(SerializableLambdas.SerializableAction { file: File? -> outputChangeListener.invalidateCachesFor(ImmutableList.of<String?>(file!!.getAbsolutePath())) }),
            SerializableLambdas.action<OutputStream?>(SerializableLambdas.SerializableAction { outputStream: OutputStream? -> manifest.get()!!.writeTo(outputStream) })
        )
    }

    private fun computeManifest(): ManifestInternal {
        var manifest = this.manifest
        if (manifest == null) {
            manifest = DefaultManifest(getFileResolver(), manifestContentCharset)
        }
        val manifestInternal: ManifestInternal
        if (manifest is ManifestInternal) {
            // If the manifest is already charset-aware, just use the existing manifest
            manifestInternal = manifest
        } else {
            // If the manifest is not charset-aware, wrap it in a charset-aware wrapper
            manifestInternal = CustomManifestInternalWrapper(manifest, manifestContentCharset)
        }
        return manifestInternal
    }

    private fun fileCollectionFactory(): FileCollectionFactory? {
        return getServices().get<FileCollectionFactory?>(FileCollectionFactory::class.java)
    }

    private fun outputChangeListener(): OutputChangeListener {
        return getServices().get<OutputChangeListener?>(OutputChangeListener::class.java)!!
    }

    /**
     * The character set used to encode JAR metadata like file names.
     * Defaults to UTF-8.
     * You can change this property but it is not recommended as JVMs expect JAR metadata to be encoded using UTF-8
     *
     * @return the character set used to encode JAR metadata like file names
     * @since 2.14
     */
    @ToBeReplacedByLazyProperty
    override fun getMetadataCharset(): String? {
        return super.getMetadataCharset()
    }

    /**
     * The character set used to encode JAR metadata like file names.
     * Defaults to UTF-8.
     * You can change this property but it is not recommended as JVMs expect JAR metadata to be encoded using UTF-8
     *
     * @param metadataCharset the character set used to encode JAR metadata like file names
     * @since 2.14
     */
    override fun setMetadataCharset(metadataCharset: String) {
        super.setMetadataCharset(metadataCharset)
    }

    /**
     * The character set used to encode the manifest content.
     * Defaults to UTF-8.
     * You can change this property but it is not recommended as JVMs expect manifests content to be encoded using UTF-8.
     *
     * @return the character set used to encode the manifest content
     * @since 2.14
     */
    @Input
    @ToBeReplacedByLazyProperty
    fun getManifestContentCharset(): String {
        return manifestContentCharset.get()
    }

    /**
     * The character set used to encode the manifest content.
     *
     * @param manifestContentCharset the character set used to encode the manifest content
     * @see .getManifestContentCharset
     * @since 2.14
     */
    fun setManifestContentCharset(manifestContentCharset: String) {
        if (manifestContentCharset == null) {
            throw InvalidUserDataException("manifestContentCharset must not be null")
        }
        if (!Charset.isSupported(manifestContentCharset)) {
            throw InvalidUserDataException(String.format("Charset for manifestContentCharset '%s' is not supported by your JVM", manifestContentCharset))
        }
        this.manifestContentCharset.set(manifestContentCharset)
    }

    /**
     * Configures the manifest for this JAR archive.
     *
     *
     * The given closure is executed to configure the manifest. The [Manifest] is passed to the closure as its delegate.
     *
     * @param configureClosure The closure.
     * @return This.
     */
    open fun manifest(@DelegatesTo(Manifest::class) configureClosure: Closure<*>?): Jar {
        ConfigureUtil.configure<Manifest?>(configureClosure, forceManifest())
        return this
    }

    /**
     * Configures the manifest for this JAR archive.
     *
     *
     * The given action is executed to configure the manifest.
     *
     * @param configureAction The action.
     * @return This.
     * @since 3.5
     */
    fun manifest(configureAction: Action<in Manifest?>): Jar {
        configureAction.execute(forceManifest())
        return this
    }

    private fun forceManifest(): Manifest {
        if (manifest == null) {
            manifest = DefaultManifest(getFileResolver(), manifestContentCharset)
        }
        return manifest!!
    }

    @Internal
    @ToBeReplacedByLazyProperty(comment = "This should probably stay eager")
    fun getMetaInf(): CopySpec {
        return metaInf.addChild()
    }

    /**
     * Adds content to this JAR archive's META-INF directory.
     *
     *
     * The given closure is executed to configure a `CopySpec`. The [CopySpec] is passed to the closure as its delegate.
     *
     * @param configureClosure The closure.
     * @return The created `CopySpec`
     */
    fun metaInf(@DelegatesTo(CopySpec::class) configureClosure: Closure<*>?): CopySpec? {
        return ConfigureUtil.configure<CopySpec?>(configureClosure, getMetaInf())
    }

    /**
     * Adds content to this JAR archive's META-INF directory.
     *
     *
     * The given action is executed to configure a `CopySpec`.
     *
     * @param configureAction The action.
     * @return The created `CopySpec`
     * @since 3.5
     */
    fun metaInf(configureAction: Action<in CopySpec?>): CopySpec {
        val metaInf = getMetaInf()
        configureAction.execute(metaInf)
        return metaInf
    }

    private class ExcludeManifestAction : Action<FileCopyDetails?> {
        override fun execute(details: FileCopyDetails) {
            if (details.getPath().equals("META-INF/MANIFEST.MF", ignoreCase = true)) {
                details.exclude()
            }
        }
    }

    companion object {
        const val DEFAULT_EXTENSION: String = "jar"
    }
}
