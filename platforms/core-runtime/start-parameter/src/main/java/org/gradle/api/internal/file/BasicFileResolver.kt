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
package org.gradle.api.internal.file

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Transformer
import org.gradle.internal.FileUtils
import org.gradle.internal.UncheckedException
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

/**
 * A minimal resolver, which does not use any native services. Used during bootstrap only. You should generally use [FileResolver] instead.
 *
 * TODO - share more stuff with AbstractFileResolver.
 */
class BasicFileResolver(private val baseDir: File?) : Transformer<File?, String?> {
    override fun transform(original: String?): File? {
        val original = original!!
        if (original.startsWith("file:")) {
            try {
                return FileUtils.normalize(File(URI(original)))
            } catch (e: URISyntaxException) {
                throw UncheckedException.throwAsUncheckedException(e)
            }
        }

        var file = File(original)
        if (file.isAbsolute()) {
            return FileUtils.normalize(file)
        }

        if (URI_SCHEME.matcher(original).matches()) {
            throw InvalidUserDataException(String.format("Cannot convert URL '%s' to a file.", original))
        }

        file = File(baseDir, original)
        return FileUtils.normalize(file)
    }

    companion object {
        private val URI_SCHEME: Pattern = Pattern.compile("[a-zA-Z][a-zA-Z0-9+-\\.]*:.+")
    }
}
