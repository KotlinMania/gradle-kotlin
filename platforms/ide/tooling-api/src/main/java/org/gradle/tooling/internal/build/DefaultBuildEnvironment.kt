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
package org.gradle.tooling.internal.build

import java.io.File
import java.io.Serializable
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.internal.gradle.GradleBuildIdentity
import org.gradle.tooling.internal.protocol.InternalBuildEnvironment
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.GradleEnvironment
import org.gradle.tooling.model.build.JavaEnvironment

class DefaultBuildEnvironment(
    override val buildIdentifier: DefaultBuildIdentifier,
    private val gradleUserHome: File?,
    private val gradleVersion: String?,
    private val javaHome: File?,
    private val jvmArguments: MutableList<String?>?,
    override val versionInfo: String?
) : BuildEnvironment, InternalBuildEnvironment, Serializable, GradleBuildIdentity {
    override val rootDir: File?
        get() = buildIdentifier.rootDir

    override val gradle: GradleEnvironment
        get() = object : GradleEnvironment {
            override val gradleUserHome: File?
                get() = this@DefaultBuildEnvironment.gradleUserHome

            override val gradleVersion: String?
                get() = this@DefaultBuildEnvironment.gradleVersion
        }

    override val java: JavaEnvironment
        get() = object : JavaEnvironment {
            override val javaHome: File?
                get() = this@DefaultBuildEnvironment.javaHome

            override val jvmArguments: MutableList<String?>?
                get() = this@DefaultBuildEnvironment.jvmArguments
        }
}
