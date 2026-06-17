/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.tasks.bundling

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.internal.file.copy.RenamingCopyAction
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.internal.Transformers
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.util.internal.ConfigureUtil
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Assembles a WAR archive.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class War : Jar() {
    /**
     * Returns the `web.xml` file to include in the WAR archive. When `null`, no `web.xml` file is included in the WAR.
     *
     * @return The `web.xml` file.
     */
    /**
     * Sets the `web.xml` file to include in the WAR archive. When `null`, no `web.xml` file is included in the WAR.
     *
     * @param webXml The `web.xml` file. Maybe null.
     */
    @get:ToBeReplacedByLazyProperty
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    var webXml: File? = null
    private var classpath: FileCollection? = null
    private val webInf: DefaultCopySpec

    init {
        getArchiveExtension().set(WAR_EXTENSION)
        setMetadataCharset("UTF-8")

        // Add these as separate specs, so they are not affected by the changes to the main spec
        webInf = getRootSpec().addChildBeforeSpec(getMainSpec()).into("WEB-INF") as DefaultCopySpec
        webInf.into("classes", Action { spec: CopySpec? ->
            spec!!.from(Callable {
                val classpath = getClasspath()
                if (classpath != null) classpath.filter(SerializableLambdas.spec<File?>(SerializableLambdas.SerializableSpec { obj: File? -> obj!!.isDirectory() })) else mutableListOf<File?>()
            } as Callable<Iterable<File?>?>)
        })
        webInf.into("lib", Action { spec: CopySpec? ->
            spec!!.from(Callable {
                val classpath = getClasspath()
                if (classpath != null) classpath.filter(SerializableLambdas.spec<File?>(SerializableLambdas.SerializableSpec { obj: File? -> obj!!.isFile() })) else mutableListOf<File?>()
            } as Callable<Iterable<File?>?>)
        })

        val renameSpec: CopySpecInternal = webInf.addChild()
        renameSpec.into("")
        renameSpec.from(Callable { this@War.webXml })
        renameSpec.appendCachingSafeCopyAction(RenamingCopyAction(Transformers.constant<String?, String?>("web.xml")))
    }

    @Inject
    public abstract override fun getObjectFactory(): ObjectFactory

    @Internal
    @ToBeReplacedByLazyProperty(comment = "This should probably stay eager")
    fun getWebInf(): CopySpec {
        return webInf.addChild()
    }

    /**
     * Adds some content to the `WEB-INF` directory for this WAR archive.
     *
     *
     * The given closure is executed to configure a [CopySpec]. The `CopySpec` is passed to the closure as its delegate.
     *
     * @param configureClosure The closure to execute
     * @return The newly created `CopySpec`.
     */
    fun webInf(@DelegatesTo(CopySpec::class) configureClosure: Closure<*>?): CopySpec? {
        return ConfigureUtil.configure<CopySpec?>(configureClosure, getWebInf())
    }

    /**
     * Adds some content to the `WEB-INF` directory for this WAR archive.
     *
     *
     * The given action is executed to configure a [CopySpec].
     *
     * @param configureAction The action to execute
     * @return The newly created `CopySpec`.
     * @since 3.5
     */
    fun webInf(configureAction: Action<in CopySpec?>): CopySpec {
        val webInf = getWebInf()
        configureAction.execute(webInf)
        return webInf
    }

    /**
     * Returns the classpath to include in the WAR archive. Any JAR or ZIP files in this classpath are included in the `WEB-INF/lib` directory. Any directories in this classpath are included in
     * the `WEB-INF/classes` directory.
     *
     * @return The classpath. Returns an empty collection when there is no classpath to include in the WAR.
     */
    @Optional
    @Classpath
    @ToBeReplacedByLazyProperty
    fun getClasspath(): FileCollection? {
        return classpath
    }

    /**
     * Sets the classpath to include in the WAR archive.
     *
     * @param classpath The classpath. Must not be null.
     * @since 4.0
     */
    fun setClasspath(classpath: FileCollection?) {
        setClasspath((classpath as kotlin.Any?)!!)
    }

    /**
     * Sets the classpath to include in the WAR archive.
     *
     * @param classpath The classpath. Must not be null.
     */
    fun setClasspath(classpath: Any) {
        this.classpath = getObjectFactory().fileCollection().from(classpath)
    }

    /**
     * Adds files to the classpath to include in the WAR archive.
     *
     * @param classpath The files to add. These are evaluated as per [org.gradle.api.Project.files]
     */
    fun classpath(vararg classpath: Any?) {
        val oldClasspath = getClasspath()
        this.classpath = getObjectFactory().fileCollection().from(if (oldClasspath != null) oldClasspath else ArrayList<Any?>(), classpath)
    }

    @get:Internal
    abstract val webAppDirectory: DirectoryProperty?

    companion object {
        const val WAR_EXTENSION: String = "war"
    }
}
