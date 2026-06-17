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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

/**
 * A parsed version.
 *
 * This should be synced with [VersionNumber] and [org.gradle.util.GradleVersion] at some point.
 */
interface Version {
    /**
     * Returns the original [String] representation of the version.
     */
    val source: String?

    /**
     * Returns all the parts of this version. e.g. 1.2.3 returns [1,2,3] or 1.2-beta4 returns [1,2,beta,4].
     */
    val parts: Array<String?>?

    /**
     * Returns all the numeric parts of this version as [Long], with nulls in non-numeric positions. eg. 1.2.3 returns [1,2,3] or 1.2-beta4 returns [1,2,null,4].
     */
    val numericParts: Array<Long?>?

    /**
     * Returns the base version for this version, which removes any qualifiers. Generally this is the first '.' separated parts of this version.
     * e.g. 1.2.3-beta-4 returns 1.2.3, or 7.0.12beta5 returns 7.0.12.
     */
    val baseVersion: Version?

    /**
     * Returns true if this version is qualified in any way. For example, 1.2.3 is not qualified, 1.2-beta-3 is.
     */
    val isQualified: Boolean
}
