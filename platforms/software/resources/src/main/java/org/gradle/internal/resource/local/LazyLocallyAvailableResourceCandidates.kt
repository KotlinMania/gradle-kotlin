/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.resource.local

import org.gradle.internal.Factory
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.HashCode
import java.io.File

class LazyLocallyAvailableResourceCandidates(private val filesFactory: Factory<MutableList<File>>, private val checksumService: ChecksumService) : LocallyAvailableResourceCandidates {
    protected var files: MutableList<File>? = null
        get() {
            if (field == null) {
                field = filesFactory.create()
            }
            return field
        }
        private set

    override fun isNone(): Boolean {
        return this.files!!.isEmpty()
    }

    override fun findByHashValue(targetHash: HashCode): LocallyAvailableResource? {
        var thisHash: HashCode?
        for (file in this.files!!) {
            thisHash = checksumService.sha1(file)
            if (thisHash == targetHash) {
                return DefaultLocallyAvailableResource(file, thisHash)
            }
        }

        return null
    }
}
