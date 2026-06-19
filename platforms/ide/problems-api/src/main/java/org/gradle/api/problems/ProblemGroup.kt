/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.problems

import org.gradle.api.Incubating
import org.gradle.api.problems.internal.DefaultProblemGroup
import javax.annotation.concurrent.Immutable

/**
 * Represents a group of problems.
 *
 *
 * Groups are organized in hierarchy where the parent group should represent the more broad problem group.
 *
 *
 * Two problem groups  are considered equal if their [.getName] and their parents are equal.
 *
 * @since 8.8
 * @see ProblemId
 */
@Incubating
@Immutable
abstract class ProblemGroup
/**
 * Constructor.
 *
 * @since 8.13
 */
protected constructor() {
    /**
     * The name of the problem group.
     *
     * @since 8.8
     */
    abstract fun getName(): String?

    /**
     * Returns a human-readable label describing the group.
     *
     * @since 8.8
     */
    abstract fun getDisplayName(): String?

    /**
     * Returns the parent group or `null` for root groups.
     *
     * @since 8.8
     */
    abstract fun getParent(): ProblemGroup?

    companion object {
        /**
         * Creates a new root problem group i.e. a group with no parent.
         *
         * @param name the name of the group. The convention is to use kebab-case (i.e., lower case with hyphens). Cannot be blank (i.e., `null`, empty string, or only whitespaces).
         * @param displayName the user-friendly display name of the group. Cannot be blank (i.e., `null`, empty string, or only whitespaces).
         * @return the new group
         * @since 8.13
         */
        @JvmStatic
        fun create(name: String, displayName: String): ProblemGroup {
            return DefaultProblemGroup(name, displayName, null)
        }

        /**
         * Creates a new problem group.
         *
         * @param name the name of the group. The convention is to use kebab-case (ie lower case with hyphens).  Cannot be blank (i.e., `null`, empty string, or only whitespaces).
         * @param displayName the user-friendly display name of the group. Cannot be blank (i.e., `null`, empty string, or only whitespaces).
         * @param parent the parent group. May be `null` for root groups.
         * @return the new group
         * @since 8.13
         */
        @JvmStatic
        fun create(name: String, displayName: String, parent: ProblemGroup?): ProblemGroup {
            return DefaultProblemGroup(name, displayName, parent)
        }
    }
}
