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
package org.gradle.internal.resource

import org.gradle.internal.file.RelativeFilePathResolver
import java.io.File

class DefaultTextFileResourceLoader(private val resolver: RelativeFilePathResolver) : TextFileResourceLoader {
    override fun loadFile(description: String, sourceFile: File?): TextResource {
        if (sourceFile == null) {
            return StringTextResource(description, "")
        }
        return UriTextResource.from(description, sourceFile, resolver)
    }
}
