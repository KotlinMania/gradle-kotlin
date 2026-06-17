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
package org.gradle.internal.component.model

import com.google.common.base.Objects
import com.google.common.io.Files
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.util.internal.GUtil
import java.io.File

class DefaultIvyArtifactName @JvmOverloads constructor(private val name: String, private val type: String, private val extension: String?, private val classifier: String? = null) : IvyArtifactName {
    private val hashCode: Int

    init {
        this.hashCode = computeHashCode()
    }

    override fun getDisplayName(): String {
        val result = StringBuilder()
        result.append(name)
        if (GUtil.isTrue(classifier)) {
            result.append("-")
            result.append(classifier)
        }
        if (GUtil.isTrue(extension) && Files.getFileExtension(name) != extension) {
            result.append(".")
            result.append(extension)
        }
        return result.toString()
    }

    override fun hashCode(): Int {
        return hashCode
    }

    private fun computeHashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (if (extension != null) extension.hashCode() else 0)
        result = 31 * result + (if (classifier != null) classifier.hashCode() else 0)
        return result
    }

    override fun equals(obj: Any): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as DefaultIvyArtifactName
        return Objects.equal(name, other.name)
                && Objects.equal(type, other.type)
                && Objects.equal(extension, other.extension)
                && Objects.equal(classifier, other.classifier)
    }

    override fun getName(): String {
        return name
    }

    override fun getType(): String {
        return type
    }

    override fun getExtension(): String {
        return extension!!
    }

    override fun getClassifier(): String {
        return classifier!!
    }

    companion object {
        @JvmStatic
        fun forPublishArtifact(publishArtifact: PublishArtifact): DefaultIvyArtifactName {
            var name = publishArtifact.getName()
            if (name == null) {
                name = publishArtifact.getFile().getName()
            }
            val classifier = GUtil.elvis<String?>(publishArtifact.getClassifier(), null)
            return DefaultIvyArtifactName(name, publishArtifact.getType(), publishArtifact.getExtension(), classifier)
        }

        @JvmStatic
        fun forFile(file: File, classifier: String?): DefaultIvyArtifactName {
            val fileName = file.getName()
            return forFileName(fileName, classifier)
        }

        @JvmStatic
        fun forFileName(fileName: String, classifier: String?): DefaultIvyArtifactName {
            val name = Files.getNameWithoutExtension(fileName)
            val extension = Files.getFileExtension(fileName)
            return DefaultIvyArtifactName(name, extension, extension, classifier)
        }
    }
}
