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
package org.gradle.language.base.internal.compile

import org.gradle.language.base.compile.CompilerVersion
import org.gradle.util.internal.VersionNumber
import org.jspecify.annotations.NullMarked

@NullMarked
class DefaultCompilerVersion(private val type: String, private val vendor: String, private val version: VersionNumber) : CompilerVersion {
    override fun getType(): String {
        return type
    }

    override fun getVendor(): String {
        return vendor
    }

    override fun getVersion(): String {
        return version.toString()
    }

    override fun toString(): String {
        return "CompilerVersion{" + "type='" + type + '\'' + ", vendor='" + vendor + '\'' + ", version=" + version + '}'
    }
}
