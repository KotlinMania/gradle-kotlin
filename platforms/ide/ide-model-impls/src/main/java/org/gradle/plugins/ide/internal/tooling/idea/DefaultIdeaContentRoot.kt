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
package org.gradle.plugins.ide.internal.tooling.idea

import java.io.File
import java.io.Serializable

class DefaultIdeaContentRoot : Serializable {
    var rootDirectory: File? = null
    var sourceDirectories: MutableSet<DefaultIdeaSourceDirectory?> = LinkedHashSet<DefaultIdeaSourceDirectory?>()
    var testDirectories: MutableSet<DefaultIdeaSourceDirectory?> = LinkedHashSet<DefaultIdeaSourceDirectory?>()
    var resourceDirectories: MutableSet<DefaultIdeaSourceDirectory?> = LinkedHashSet<DefaultIdeaSourceDirectory?>()
    var testResourceDirectories: MutableSet<DefaultIdeaSourceDirectory?> = LinkedHashSet<DefaultIdeaSourceDirectory?>()
    var excludeDirectories: MutableSet<File?> = LinkedHashSet<File?>()

    fun setRootDirectory(rootDirectory: File?): DefaultIdeaContentRoot {
        this.rootDirectory = rootDirectory
        return this
    }

    fun setSourceDirectories(sourceDirectories: MutableSet<DefaultIdeaSourceDirectory?>): DefaultIdeaContentRoot {
        this.sourceDirectories = sourceDirectories
        return this
    }

    fun setTestDirectories(testDirectories: MutableSet<DefaultIdeaSourceDirectory?>): DefaultIdeaContentRoot {
        this.testDirectories = testDirectories
        return this
    }

    fun setResourceDirectories(resourceDirectories: MutableSet<DefaultIdeaSourceDirectory?>): DefaultIdeaContentRoot {
        this.resourceDirectories = resourceDirectories
        return this
    }

    fun setTestResourceDirectories(testResourceDirectories: MutableSet<DefaultIdeaSourceDirectory?>): DefaultIdeaContentRoot {
        this.testResourceDirectories = testResourceDirectories
        return this
    }


    fun setExcludeDirectories(excludeDirectories: MutableSet<File?>): DefaultIdeaContentRoot {
        this.excludeDirectories = excludeDirectories
        return this
    }

    override fun toString(): String {
        return ("IdeaContentRoot{"
                + "rootDirectory=" + rootDirectory
                + ", sourceDirectories count=" + sourceDirectories.size
                + ", testDirectories count=" + testDirectories.size
                + ", resourceDirectories count=" + resourceDirectories.size
                + ", testResourceDirectories count=" + testResourceDirectories.size
                + ", excludeDirectories count=" + excludeDirectories.size
                + '}')
    }
}
