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
package org.gradle.api.internal.artifacts.repositories.layout

import org.gradle.api.internal.file.FileResolver
import java.net.URI

class ResolvedPattern {
    val scheme: String
    val baseUri: URI?
    val pattern: String?
    val absolutePattern: String

    constructor(rawPattern: String, fileResolver: FileResolver) {
        // get rid of the ivy [] token, as [ ] are not valid URI characters
        val pos = rawPattern.indexOf('[')
        val basePath = if (pos < 0) rawPattern else rawPattern.substring(0, pos)
        if (basePath.isEmpty()) {
            this.baseUri = fileResolver.resolveUri(".")
        } else {
            this.baseUri = fileResolver.resolveUri(basePath)
        }
        this.pattern = if (pos < 0) "" else rawPattern.substring(pos)
        scheme = baseUri.getScheme().lowercase()
        absolutePattern = constructAbsolutePattern(baseUri, pattern)
    }

    constructor(baseUri: URI, pattern: String) {
        this.baseUri = baseUri
        this.pattern = pattern
        scheme = baseUri.getScheme().lowercase()
        absolutePattern = constructAbsolutePattern(baseUri, pattern)
    }

    private fun constructAbsolutePattern(baseUri: URI, patternPart: String): String {
        val uriPart = baseUri.toString()
        val join = if (uriPart.endsWith("/") || patternPart.length == 0) "" else "/"
        return uriPart + join + patternPart
    }
}
