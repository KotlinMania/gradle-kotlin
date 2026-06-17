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
package org.gradle.plugins.ide.internal.tooling.model

import org.gradle.tooling.model.GradleModuleVersion
import java.io.Serializable

class DefaultGradleModuleVersion(private val group: String?, private val name: String?, private val version: String?) : GradleModuleVersion, Serializable {
    override fun getGroup(): String? {
        return group
    }

    override fun getName(): String? {
        return name
    }

    override fun getVersion(): String? {
        return version
    }

    override fun toString(): String {
        return ("GradleModuleVersion{"
                + "group='" + group + '\''
                + ", name='" + name + '\''
                + ", version='" + version + '\''
                + '}')
    }
}
