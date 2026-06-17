/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.javadoc

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileTree
import org.gradle.api.internal.tasks.GroovydocAntAction
import org.gradle.api.internal.tasks.GroovydocParameters
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.file.Deleter.ensureEmptyDirectory
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.util.Arrays
import java.util.Collections
import java.util.stream.Collectors
import javax.inject.Inject

/**
 *
 * Generates HTML API documentation for Groovy source, and optionally, Java source.
 *
 *
 * This task uses Groovy's Groovydoc tool to generate the API documentation. Please note
 * that the Groovydoc tool has some limitations at the moment. The version of the Groovydoc
 * that is used, is the one from the Groovy dependency defined in the build script.
 */
@CacheableTask
abstract class Groovydoc : SourceTask() {
    /**
     * Returns the classpath containing the Groovy library to be used.
     *
     * @return The classpath containing the Groovy library to be used
     */
    /**
     * Sets the classpath containing the Groovy library to be used.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var groovyClasspath: FileCollection? = null

    /**
     * Returns the classpath used to locate classes referenced by the documented sources.
     *
     * @return The classpath used to locate classes referenced by the documented sources
     */
    /**
     * Sets the classpath used to locate classes referenced by the documented sources.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var classpath: FileCollection? = null

    /**
     * Returns the directory to generate the documentation into.
     *
     * @return The directory to generate the documentation into
     */
    /**
     * Sets the directory to generate the documentation into.
     */
    @get:ToBeReplacedByLazyProperty
    @get:OutputDirectory
    var destinationDir: File? = null

    /**
     * Returns whether to create class and package usage pages.
     */
    /**
     * Sets whether to create class and package usage pages.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isUse: Boolean = false

    /**
     * Returns whether to include timestamp within hidden comment in generated HTML (Groovy &gt;= 2.4.6).
     */
    /**
     * Sets whether to include timestamp within hidden comment in generated HTML (Groovy &gt;= 2.4.6).
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isNoTimestamp: Boolean = true

    /**
     * Returns whether to include version stamp within hidden comment in generated HTML (Groovy &gt;= 2.4.6).
     */
    /**
     * Sets whether to include version stamp within hidden comment in generated HTML (Groovy &gt;= 2.4.6).
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isNoVersionStamp: Boolean = true

    /**
     * Returns the browser window title for the documentation. Set to `null` when there is no window title.
     */
    /**
     * Sets the browser window title for the documentation.
     *
     * @param windowTitle A text for the windows title
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var windowTitle: String? = null

    /**
     * Returns the title for the package index(first) page. Set to `null` when there is no document title.
     */
    /**
     * Sets title for the package index(first) page (optional).
     *
     * @param docTitle the docTitle as HTML
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var docTitle: String? = null

    /**
     * Returns the HTML header for each page. Set to `null` when there is no header.
     */
    /**
     * Sets header text for each page (optional).
     *
     * @param header the header as HTML
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var header: String? = null

    /**
     * Returns the HTML footer for each page. Set to `null` when there is no footer.
     */
    /**
     * Sets footer text for each page (optional).
     *
     * @param footer the footer as HTML
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var footer: String? = null

    /**
     * Returns a HTML text to be used for overview documentation. Set to `null` when there is no overview text.
     */
    /**
     * Sets a HTML text to be used for overview documentation (optional).
     *
     *
     * **Example:** `overviewText = resources.text.fromFile("/overview.html")`
     */
    @get:Nested
    @get:Optional
    var overviewText: TextResource? = null

    private var links: MutableSet<Link?> = LinkedHashSet<Link?>()

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor?

    @TaskAction
    protected fun generate() {
        checkGroovyClasspathNonEmpty(this.groovyClasspath!!.getFiles())
        val destinationDir = this.destinationDir!!
        try {
            this.deleter.ensureEmptyDirectory(destinationDir)
        } catch (ex: IOException) {
            throw throwAsUncheckedException(ex)
        }
        val fsOperations = getServices().get<FileSystemOperations?>(FileSystemOperations::class.java)

        // Copy all sources into one place
        val tmpDir = getTemporaryDir()
        fsOperations!!.delete(Action { spec: DeleteSpec? -> spec!!.delete(tmpDir) })
        fsOperations.copy(Action { spec: CopySpec? -> spec!!.from(getSource()).into(tmpDir) })

        this.workerExecutor.classLoaderIsolation().submit<GroovydocParameters>(GroovydocAntAction::class.java, Action { parameters: GroovydocParameters ->
            parameters.antLibraryClasspath.from(this.classpath)
            parameters.antLibraryClasspath.from(this.groovyClasspath)
            parameters.source.convention(getSource())
            parameters.destinationDirectory.fileValue(destinationDir)
            parameters.use.convention(this.isUse)
            parameters.noTimestamp.convention(this.isNoTimestamp)
            parameters.noVersionStamp.convention(this.isNoVersionStamp)
            parameters.windowTitle.convention(this.windowTitle)
            parameters.docTitle.convention(this.docTitle)
            parameters.header.convention(this.header)
            parameters.footer.convention(this.footer)
            parameters.overview.convention(this.pathToOverview)
            parameters.access.convention(this.access)
            parameters.links.convention(
                getLinks().stream()
                    .map<GroovydocParameters.Link?> { link: Link? ->
                        GroovydocParameters.Link(
                            link!!.getPackages(),
                            link.url
                        )
                    }
                    .collect(Collectors.toList())
            )
            parameters.tmpDir.fileValue(getTemporaryDir())
            parameters.includeAuthor.convention(this.includeAuthor)
            parameters.processScripts.convention(this.processScripts)
            parameters.includeMainForScripts.convention(this.includeMainForScripts)
        })
    }

    private val pathToOverview: String?
        get() {
            val overview = this.overviewText
            if (overview != null) {
                return overview.asFile().getAbsolutePath()
            }
            return null
        }

    private fun checkGroovyClasspathNonEmpty(classpath: MutableCollection<File?>) {
        if (classpath.isEmpty()) {
            throw InvalidUserDataException("You must assign a Groovy library to the groovy configuration!")
        }
    }

    /**
     * {@inheritDoc}
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @ToBeReplacedByLazyProperty
    override fun getSource(): FileTree {
        return super.getSource()
    }

    @JvmField
    @get:Input
    abstract val access: Property<GroovydocAccess?>?

    @JvmField
    @get:Input
    abstract val includeAuthor: Property<Boolean?>?

    @JvmField
    @get:Input
    abstract val processScripts: Property<Boolean?>?

    @JvmField
    @get:Input
    abstract val includeMainForScripts: Property<Boolean?>?

    /**
     * Returns the links to groovydoc/javadoc output at the given URL.
     */
    @Input
    @ToBeReplacedByLazyProperty
    fun getLinks(): MutableSet<Link?> {
        return Collections.unmodifiableSet<Link?>(links)
    }

    /**
     * Sets links to groovydoc/javadoc output at the given URL.
     *
     * @param links The links to set
     * @see .link
     */
    fun setLinks(links: MutableSet<Link?>) {
        this.links = links
    }

    /**
     * Add links to groovydoc/javadoc output at the given URL.
     *
     * @param url Base URL of external site
     * @param packages list of package prefixes
     */
    fun link(url: String, vararg packages: String) {
        links.add(Link(url, *packages))
    }

    /**
     * A Link class represent a link between groovydoc/javadoc output and url.
     */
    class Link(url: String, vararg packages: String) : Serializable {
        private var packages: MutableList<String?>? = ArrayList<String?>()

        /**
         * Returns the base url for the external site.
         */
        val url: String?

        /**
         * Constructs a `Link`.
         *
         * @param url Base URL of external site
         * @param packages list of package prefixes
         */
        init {
            throwExceptionIfNull(url, "Url must not be null")
            if (packages.size == 0) {
                throw InvalidUserDataException("You must specify at least one package!")
            }
            for (aPackage in packages) {
                throwExceptionIfNull(aPackage, "A package must not be null")
            }
            this.packages = Arrays.asList<String?>(*packages)
            this.url = url
        }

        private fun throwExceptionIfNull(value: String, message: String?) {
            if (value == null) {
                throw InvalidUserDataException(message)
            }
        }

        /**
         * Returns a list of package prefixes to be linked with an external site.
         */
        fun getPackages(): MutableList<String?> {
            return Collections.unmodifiableList<String?>(packages)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val link = o as Link

            if (if (packages != null) (packages != link.packages) else link.packages != null) {
                return false
            }
            if (if (url != null) (url != link.url) else link.url != null) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = if (packages != null) packages.hashCode() else 0
            result = 31 * result + (if (url != null) url.hashCode() else 0)
            return result
        }
    }

    @get:Inject
    protected abstract val deleter: Deleter?
}
