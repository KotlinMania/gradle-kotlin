/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.MavenDependencyKey
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomDependencyMgt

class RootPomParent : PomParent {
    private val properties = mutableMapOf<String?, String?>()
    private val dependencies = mutableMapOf<MavenDependencyKey?, PomReader.PomDependencyData?>()
    private val dependencyMgts = mutableMapOf<MavenDependencyKey?, PomDependencyMgt?>()

    override fun getProperties(): MutableMap<String?, String?> {
        return properties
    }

    override fun getDependencies(): MutableMap<MavenDependencyKey?, PomReader.PomDependencyData?> {
        return dependencies
    }

    override fun getDependencyMgt(): MutableMap<MavenDependencyKey?, PomDependencyMgt?> {
        return dependencyMgts
    }

    override fun findDependencyDefaults(dependencyKey: MavenDependencyKey?): PomDependencyMgt? {
        return null
    }
}
