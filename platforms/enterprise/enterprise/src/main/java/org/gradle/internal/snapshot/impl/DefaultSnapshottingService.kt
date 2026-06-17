/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.snapshot.impl

import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.Snapshot
import org.gradle.internal.snapshot.SnapshottingService
import org.gradle.internal.vfs.FileSystemAccess
import java.nio.file.Path
import javax.inject.Inject

class DefaultSnapshottingService @Inject constructor(private val fileSystemAccess: FileSystemAccess) : SnapshottingService {
    override fun snapshotFor(filePath: Path): Snapshot {
        val absolutePath = filePath.toAbsolutePath().toString()
        val hash = fileSystemAccess.read(absolutePath).getHash()

        return DefaultSnapshot(hash)
    }

    private class DefaultSnapshot(private val hashCode: HashCode) : Snapshot {
        override fun getHashValue(): String {
            return hashCode.toString()
        }

        override fun toString(): String {
            return String.format("DefaultSnapshot { hashValue='%s' }", getHashValue())
        }
    }
}
