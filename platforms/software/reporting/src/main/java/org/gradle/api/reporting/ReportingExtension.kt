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
package org.gradle.api.reporting

import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.reporting.internal.ReportUtilities
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty
import java.io.File
import javax.inject.Inject

/**
 * A project extension named "reporting" that provides basic reporting settings and utilities.
 *
 *
 * Example usage:
 * <pre class='autoTested'>
 * plugins {
 * id("org.gradle.reporting-base")
 * }
 *
 * reporting {
 * // change the base directory where all reports are generated
 * baseDirectory = layout.buildDirectory.dir("our-reports")
 * }
 *
 * // A directory for test reports
 * reporting.baseDirectory.dir("test-reports")
 *
 * // A report file
 * reporting.baseDirectory.file("index.html")
</pre> *
 *
 *
 * When implementing a task that produces reports, the location of where to generate reports should be obtained from [.getBaseDirectory].
 */
abstract class ReportingExtension @Inject constructor(private val project: Project) {
    /**
     * Returns base directory property to use for all reports.
     *
     * @since 4.4
     */
    abstract val baseDirectory: DirectoryProperty?

    /**
     * Creates a file object for the given path, relative to [.getBaseDirectory].
     *
     *
     * The reporting base dir can be changed, so users of this method should use it on demand where appropriate.
     *
     * @param path the relative path
     * @return a file object at the given path relative to [.getBaseDirectory].
     *
     * @see DirectoryProperty.file
     * @see DirectoryProperty.dir
     */
    @Deprecated(
        """Use {@code getBaseDirectory().file(path)} or {@code getBaseDirectory().dir(path)} instead.

      """
    )
    fun file(path: String): File {
        deprecateMethod(ReportingExtension::class.java, "file(String)")
            .replaceWith("getBaseDirectory().file(String) or getBaseDirectory().dir(String)")!!
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "reporting_extension_file")!!
            .nagUser()
        return this.baseDirectory.file(path).get().getAsFile()
    }

    @get:Deprecated("Use your own way of generating a title for API documentation.")
    @get:NotToBeReplacedByLazyProperty(because = "this method is deprecated")
    val apiDocTitle: String
        /**
         * Provides a default title for API documentation based on the project's name and version.
         *
         */
        get() {
            deprecateMethod(ReportingExtension::class.java, "getApiDocTitle()")
                .willBeRemovedInGradle10()
                .withUpgradeGuideSection(9, "reporting_extension_api_doc_title")!!
                .nagUser()
            return ReportUtilities.Companion.getApiDocTitleFor(project)
        }

    @get:Incubating
    abstract val reports: ExtensiblePolymorphicDomainObjectContainer<ReportSpec>?

    /**
     * Add more reports or configure the available reports.
     *
     * @param action configuration action for the reports container
     * @since 9.1.0
     */
    @Incubating
    fun reports(action: Action<in ExtensiblePolymorphicDomainObjectContainer<ReportSpec>>) {
        action.execute(this.reports)
    }

    companion object {
        /**
         * The name of this extension ("{@value}")
         */
        const val NAME: String = "reporting"

        /**
         * The default name of the base directory for all reports, relative to [org.gradle.api.file.ProjectLayout.getBuildDirectory] ({@value}).
         */
        const val DEFAULT_REPORTS_DIR_NAME: String = "reports"
    }
}
