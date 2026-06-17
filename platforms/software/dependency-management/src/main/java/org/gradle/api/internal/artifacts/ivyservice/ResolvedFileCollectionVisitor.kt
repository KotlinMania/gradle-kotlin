/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedFileVisitor
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import java.io.File

class ResolvedFileCollectionVisitor(private val visitor: FileCollectionStructureVisitor) : ResolvedFileVisitor {
    private val files: MutableSet<File> = LinkedHashSet<File>()
    @JvmField
    val failures: MutableSet<Throwable> = LinkedHashSet<Throwable>()

    override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
        return visitor.prepareForVisit(source)
    }

    override fun visitFile(file: File) {
        files.add(file)
    }

    override fun visitFailure(failure: Throwable) {
        failures.add(failure)
    }

    override fun endVisitCollection(source: FileCollectionInternal.Source) {
        visitor.visitCollection(source, files)
        files.clear()
    }
}
