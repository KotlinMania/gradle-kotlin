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
package org.gradle.api

import org.apache.tools.ant.Project
import org.gradle.internal.instrumentation.api.annotations.NotToBeMigratedToLazy

/**
 *
 * An `AntBuilder` allows you to use Ant from your build script.
 */
@NotToBeMigratedToLazy
abstract class AntBuilder : groovy.ant.AntBuilder() {
    /**
     * Returns the properties of the Ant project. This is a live map, you that you can make changes to the map and these
     * changes are reflected in the Ant project.
     *
     * @return The properties. Never returns null.
     */
    abstract val properties: MutableMap<String, Any?>?

    /**
     * Returns the references of the Ant project. This is a live map, you that you can make changes to the map and these
     * changes are reflected in the Ant project.
     *
     * @return The references. Never returns null.
     */
    abstract val references: MutableMap<String, Any?>?

    /**
     * Imports an Ant build into the associated Gradle project.
     *
     * @param antBuildFile The build file. This is resolved as per [Project.file].
     */
    abstract fun importBuild(antBuildFile: Any)

    /**
     * Imports an Ant build into the associated Gradle project, specifying the base directory for Gradle tasks that correspond to Ant targets.
     *
     *
     * By default the base directory is the Ant build file parent directory. The relative paths are relative to [Project.getProjectDir].
     *
     * @param antBuildFile The build file. This is resolved as per [Project.file].
     * @param baseDirectory The base directory. This is resolved as per [Project.file].
     *
     * @since 7.1
     */
    abstract fun importBuild(antBuildFile: Any, baseDirectory: String)

    /**
     * Imports an Ant build into the associated Gradle project, potentially providing alternative names for Gradle tasks that correspond to Ant targets.
     *
     *
     * For each Ant target that is to be converted to a Gradle task, the given `taskNamer` receives the Ant target name as input
     * and is expected to return the desired name for the corresponding Gradle task.
     * The transformer may be called multiple times with the same input.
     * Implementations should ensure uniqueness of the return value for a distinct input.
     * That is, no two inputs should yield the same return value.
     *
     * @param antBuildFile The build file. This is resolved as per [org.gradle.api.Project.file].
     * @param taskNamer A transformer that calculates the name of the Gradle task for a corresponding Ant target.
     */
    abstract fun importBuild(antBuildFile: Any, taskNamer: Transformer<out String, in String>)

    /**
     * Imports an Ant build into the associated Gradle project, specifying the base directory and potentially providing alternative names
     * for Gradle tasks that correspond to Ant targets.
     *
     *
     * By default the base directory is the Ant build file parent directory. The relative paths are relative to [Project.getProjectDir].
     *
     *
     * For each Ant target that is to be converted to a Gradle task, the given `taskNamer` receives the Ant target name as input
     * and is expected to return the desired name for the corresponding Gradle task.
     * The transformer may be called multiple times with the same input.
     * Implementations should ensure uniqueness of the return value for a distinct input.
     * That is, no two inputs should yield the same return value.
     *
     * @param antBuildFile The build file. This is resolved as per [Project.file].
     * @param baseDirectory The base directory. This is resolved as per [Project.file].
     * @param taskNamer A transformer that calculates the name of the Gradle task for a corresponding Ant target.
     *
     * @since 7.1
     */
    abstract fun importBuild(antBuildFile: Any, baseDirectory: String, taskNamer: Transformer<out String, in String>)

    val ant: AntBuilder
        /**
         * Returns this AntBuilder. Useful when you need to pass this builder to methods from within closures.
         *
         * @return this
         */
        get() = this

    /**
     * Sets the Ant message priority that should correspond to the Gradle "lifecycle" log level.  Any messages logged at this
     * priority (or more critical priority) will be logged at least at lifecycle in Gradle's logger.  If the Ant priority already maps to a
     * higher Gradle log level, it will continue to be logged at that level.  Acceptable values are "VERBOSE", "DEBUG", "INFO", "WARN",
     * and "ERROR".
     *
     * @param logLevel The Ant log level to map to the Gradle lifecycle log level
     */
    fun setLifecycleLogLevel(logLevel: String) {
        this.lifecycleLogLevel = AntMessagePriority.valueOf(logLevel)
    }

    /**
     * Returns the Ant message priority that corresponds to the Gradle "lifecycle" log level.
     *
     * @return logLevel The Ant log level that maps to the Gradle lifecycle log level
     */
    /**
     * Sets the Ant message priority that should correspond to the Gradle "lifecycle" log level.  Any messages logged at this
     * priority (or more critical priority) will be logged at least at lifecycle in Gradle's logger.  If the Ant priority already maps to a
     * higher Gradle log level, it will continue to be logged at that level.
     *
     * @param logLevel The Ant log level to map to the Gradle lifecycle log level
     */
    abstract var lifecycleLogLevel: AntMessagePriority?

    /**
     * Represents the normal Ant message priorities.
     */
    enum class AntMessagePriority {
        DEBUG, VERBOSE, INFO, WARN, ERROR;

        companion object {
            fun from(messagePriority: Int): AntMessagePriority {
                when (messagePriority) {
                    Project.MSG_ERR -> return AntMessagePriority.ERROR
                    Project.MSG_WARN -> return AntMessagePriority.WARN
                    Project.MSG_INFO -> return AntMessagePriority.INFO
                    Project.MSG_VERBOSE -> return AntMessagePriority.VERBOSE
                    Project.MSG_DEBUG -> return AntMessagePriority.DEBUG
                    else -> throw IllegalArgumentException()
                }
            }
        }
    }
}
