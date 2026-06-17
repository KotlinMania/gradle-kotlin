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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data

import com.google.common.base.Objects

class MavenDependencyKey(val groupId: String?, val artifactId: String?, val type: String?, val classifier: String?) {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as MavenDependencyKey
        return Objects.equal(groupId, that.groupId)
                && Objects.equal(artifactId, that.artifactId)
                && Objects.equal(classifier, that.classifier)
                && Objects.equal(type, that.type)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(groupId, artifactId, classifier, type)
    }

    override fun toString(): String {
        val key = StringBuilder()
        key.append(groupId).append(KEY_SEPARATOR).append(artifactId).append(KEY_SEPARATOR).append(type)

        if (classifier != null) {
            key.append(KEY_SEPARATOR).append(classifier)
        }

        return key.toString()
    }

    companion object {
        private const val KEY_SEPARATOR = ":"
    }
}
