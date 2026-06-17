/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.problems.internal

import com.google.common.base.Objects
import com.google.common.base.Preconditions

class DefaultDocLink(url: String) : DocLinkInternal {
    private val url: String

    init {
        this.url = Preconditions.checkNotNull<String>(url)
    }

    override fun getUrl(): String {
        return url
    }

    override fun getConsultDocumentationMessage(): String {
        return "For more information, please refer to " + url + "."
    }

    override fun equals(o: Any): Boolean {
        if (o !is DefaultDocLink) {
            return false
        }
        val that = o
        return Objects.equal(url, that.url)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(url)
    }

    override fun toString(): String {
        return "DefaultDocLink{" +
                "url='" + url + '\'' +
                '}'
    }
}
