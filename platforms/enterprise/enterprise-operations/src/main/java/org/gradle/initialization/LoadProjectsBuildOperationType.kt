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
package org.gradle.initialization

import org.gradle.internal.operations.BuildOperationType

/**
 * An operation to load the project structure from the processed settings.
 * Provides details of the project structure without projects being configured.
 *
 * @since 4.2
 */
class LoadProjectsBuildOperationType : BuildOperationType<LoadProjectsBuildOperationType.Details?, LoadProjectsBuildOperationType.Result?> {
    interface Details {
        /**
         * @since 4.6
         */
        val buildPath: String?
    }

    interface Result {
        /**
         * The path of the build configuration that contains these projects.
         * This will be ':' for top-level builds. Nested builds will have a sub-path.
         *
         * See `org.gradle.api.internal.GradleInternal#getIdentityPath()`.
         */
        val buildPath: String?

        /**
         * A description of the root Project for this build.
         *
         * See `org.gradle.api.initialization.Settings#getRootProject()`.
         */
        val rootProject: Project?

        interface Project {
            /**
             * The name of the project.
             *
             * See `org.gradle.api.Project#getName()`.
             */
            val name: String?

            /**
             * The path of the project.
             *
             * See `org.gradle.api.Project#getPath()`.
             */
            val path: String?

            /**
             * The path of the project within the entire build execution.
             * For top-level builds this will be the same as [.getPath].
             * For nested builds the project path will be prefixed with a build path.
             *
             * See `org.gradle.api.internal.project.ProjectInternal#getIdentityPath()`.
             */
            val identityPath: String?

            /**
             * The absolute file path of the project directory.
             *
             * See `org.gradle.api.Project#getProjectDir()`.
             */
            val projectDir: String?

            /**
             * The absolute file path of the projects build file.
             *
             * See `org.gradle.api.Project#getBuildFile()`.
             */
            val buildFile: String?

            /**
             * The child projects of this project.
             * No null values.
             * Ordered by project name lexicographically.
             *
             * See `org.gradle.api.Project#getChildProjects()`.
             */
            val children: MutableSet<out Project?>?
        }
    }
}
