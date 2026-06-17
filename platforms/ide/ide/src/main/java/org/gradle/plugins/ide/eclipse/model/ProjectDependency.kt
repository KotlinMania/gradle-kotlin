/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model

import com.google.common.base.Preconditions
import groovy.util.Node
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.tasks.TaskDependency
import java.util.Objects

/**
 * A classpath entry representing a project dependency.
 */
class ProjectDependency : AbstractClasspathEntry {
    /**
     * Returns the file that can replace this ProjectDependency
     *
     * @since 5.6
     */
    /**
     * Sets the file that can replace this ProjectDependency
     *
     * @since 5.6
     */
    var publication: FileReference? = null
    /**
     * Returns the source artifact of the project publication
     *
     * @see .getPublication
     * @since 5.6
     */
    /**
     * Sets the source artifact of the project publication
     *
     * @see .getPublication
     * @since 5.6
     */
    var publicationSourcePath: FileReference? = null
    /**
     * Returns the javadoc artifact of the project publication
     *
     * @see .getPublication
     * @since 5.6
     */
    /**
     * Sets the javadoc artifact of the project publication
     *
     * @see .getPublication
     * @since 5.6
     */
    var publicationJavadocPath: FileReference? = null
    private val buildDependencies = DefaultTaskDependency()

    constructor(node: Node) : super(node) {
        assertPathIsValid()
    }

    /**
     * Create a dependency on another Eclipse project.
     *
     * @param path The path to the Eclipse project, which is the name of the eclipse project preceded by "/".
     */
    constructor(path: String?) : super(path) {
        assertPathIsValid()
    }

    /**
     * Returns the tasks to be executed to build the file returned by [.getPublication]
     *
     *
     * This property doesn't have a direct effect to the Gradle Eclipse plugin's behaviour. It is used, however, by
     * Buildship to execute the configured tasks each time before the user imports the project or before a project
     * synchronization starts in case this project is closed to build the substitute jar.
     *
     * @since 5.6
     */
    fun getBuildDependencies(): TaskDependency {
        return buildDependencies
    }

    /**
     * Sets the tasks to be executed to build the file returned by [.getPublication]
     *
     * @see .getBuildDependencies
     * @since 5.6
     */
    fun buildDependencies(vararg buildDependencies: Any?) {
        this.buildDependencies.add(*buildDependencies)
    }

    private fun assertPathIsValid() {
        Preconditions.checkArgument(path.startsWith("/"))
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }
        val that = o as ProjectDependency
        return publication == that.publication &&
                publicationSourcePath == that.publicationSourcePath &&
                publicationJavadocPath == that.publicationJavadocPath
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), publication, publicationSourcePath, publicationJavadocPath)
    }

    override fun getKind(): String {
        return "src"
    }

    override fun toString(): String {
        return "ProjectDependency{" +
                "publication=" + publication +
                ", publicationSourcePath=" + publicationSourcePath +
                ", publicationJavadocPath=" + publicationJavadocPath +
                ", buildDependencies=" + buildDependencies +
                ", path='" + path + '\'' +
                ", exported=" + exported +
                ", accessRules=" + accessRules +
                ", entryAttributes=" + entryAttributes +
                '}'
    }
}
