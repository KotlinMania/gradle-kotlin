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
package org.gradle.ide.xcode.internal.xcodeproj

import com.google.common.collect.Sets
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Generator for Global ID (GID) which are present on every xcode project object.
 *
 * The GID is a 96 bit identifier that's unique on a per-project basis.
 */
@ServiceScope(Scope.Build::class)
class GidGenerator(reservedIds: MutableSet<String?>) {
    private val generatedAndReservedIds: MutableSet<String?>

    init {
        generatedAndReservedIds = Sets.newHashSet<String?>(reservedIds)
    }

    /**
     * Generate a stable GID based on the class name and hash of some object info.
     *
     * GIDs generated this way will be in the form of
     * `<class-name-hash-32> <obj-hash-32> <counter-32>`
     */
    fun generateGid(pbxClassName: String, hash: Int): String {
        var counter = 0
        var gid: String
        do {
            gid = String.format("%08X%08X%08X", pbxClassName.hashCode(), hash, counter++)
        } while (generatedAndReservedIds.contains(gid))
        generatedAndReservedIds.add(gid)
        return gid
    }
}
