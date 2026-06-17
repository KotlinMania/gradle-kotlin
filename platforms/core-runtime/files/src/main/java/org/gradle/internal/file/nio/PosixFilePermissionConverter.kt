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
package org.gradle.internal.file.nio

import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet

object PosixFilePermissionConverter {
    fun convertToPermissionsSet(mode: Int): MutableSet<PosixFilePermission> {
        val result: MutableSet<PosixFilePermission> = EnumSet.noneOf<PosixFilePermission>(PosixFilePermission::class.java)

        if (isSet(mode, 256)) {
            result.add(PosixFilePermission.OWNER_READ)
        }
        if (isSet(mode, 128)) {
            result.add(PosixFilePermission.OWNER_WRITE)
        }
        if (isSet(mode, 64)) {
            result.add(PosixFilePermission.OWNER_EXECUTE)
        }

        if (isSet(mode, 32)) {
            result.add(PosixFilePermission.GROUP_READ)
        }
        if (isSet(mode, 16)) {
            result.add(PosixFilePermission.GROUP_WRITE)
        }
        if (isSet(mode, 8)) {
            result.add(PosixFilePermission.GROUP_EXECUTE)
        }
        if (isSet(mode, 4)) {
            result.add(PosixFilePermission.OTHERS_READ)
        }
        if (isSet(mode, 2)) {
            result.add(PosixFilePermission.OTHERS_WRITE)
        }
        if (isSet(mode, 1)) {
            result.add(PosixFilePermission.OTHERS_EXECUTE)
        }
        return result
    }

    private fun isSet(mode: Int, testbit: Int): Boolean {
        return (mode and testbit) == testbit
    }

    fun convertToInt(permissions: MutableSet<PosixFilePermission>): Int {
        var result = 0
        if (permissions.contains(PosixFilePermission.OWNER_READ)) {
            result = result or 256
        }
        if (permissions.contains(PosixFilePermission.OWNER_WRITE)) {
            result = result or 128
        }
        if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
            result = result or 64
        }
        if (permissions.contains(PosixFilePermission.GROUP_READ)) {
            result = result or 32
        }
        if (permissions.contains(PosixFilePermission.GROUP_WRITE)) {
            result = result or 16
        }
        if (permissions.contains(PosixFilePermission.GROUP_EXECUTE)) {
            result = result or 8
        }
        if (permissions.contains(PosixFilePermission.OTHERS_READ)) {
            result = result or 4
        }
        if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
            result = result or 2
        }
        if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
            result = result or 1
        }
        return result
    }
}
