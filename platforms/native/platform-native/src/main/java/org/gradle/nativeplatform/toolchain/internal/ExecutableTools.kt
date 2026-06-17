/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal

/**
 * A collection of tools.
 */
interface ExecutableTools {
    /**
     * Returns the implementation version of these tools.
     */
    @JvmField
    val implementationVersion: VersionNumber?

    /**
     * Returns the path entries that must be present in order to use these tools, possibly none.
     */
    @JvmField
    val path: MutableList<File?>?
}
