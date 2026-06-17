/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.detection

import org.gradle.api.file.EmptyFileVisitor
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.ReproducibleFileVisitor
import org.gradle.api.internal.file.RelativeFile
import org.gradle.api.internal.tasks.testing.ClassTestDefinition
import org.gradle.api.internal.tasks.testing.DirectoryBasedTestDefinition
import org.gradle.api.internal.tasks.testing.TestDefinition
import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor
import java.io.File
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 * The default test definition scanner.
 *
 *
 * Depending on the availability of a test framework detector,
 * a detection or filename scan is performed to find test definitions.
 *
 *
 * Test definitions include both class-based and non-class-based tests.
 */
class DefaultTestScanner(
    private val candidateClassFiles: FileTree,
    private val candidateDefinitionDirs: MutableSet<File?>,
    private val testFrameworkDetector: TestFrameworkDetector?,
    private val testDefinitionProcessor: TestDefinitionProcessor<TestDefinition?>
) : TestDetector {
    override fun detect() {
        if (testFrameworkDetector == null) {
            filenameScan()
        } else {
            detectionScan()
        }
    }

    private fun detectionScan() {
        testFrameworkDetector!!.startDetection(testDefinitionProcessor)
        candidateClassFiles.visit(object : ClassFileVisitor() {
            public override fun visitClassFile(fileDetails: FileVisitDetails) {
                testFrameworkDetector.processTestClass(RelativeFile(fileDetails.getFile(), fileDetails.getRelativePath()))
            }
        })
    }

    private fun filenameScan() {
        candidateClassFiles.visit(object : ClassFileVisitor() {
            public override fun visitClassFile(fileDetails: FileVisitDetails) {
                val testDefinition: TestDefinition = ClassTestDefinition(getClassName(fileDetails))
                testDefinitionProcessor.processTestDefinition(testDefinition)
            }
        })
        candidateDefinitionDirs.forEach(Consumer { dir: File? ->
            val testDefinition: TestDefinition = DirectoryBasedTestDefinition(dir!!)
            testDefinitionProcessor.processTestDefinition(testDefinition)
        })
    }

    private abstract inner class ClassFileVisitor : EmptyFileVisitor(), ReproducibleFileVisitor {
        override fun visitFile(fileDetails: FileVisitDetails) {
            if (isClass(fileDetails) && !isAnonymousClass(fileDetails)) {
                visitClassFile(fileDetails)
            }
        }

        abstract fun visitClassFile(fileDetails: FileVisitDetails?)

        fun isAnonymousClass(fileVisitDetails: FileVisitDetails): Boolean {
            return ANONYMOUS_CLASS_NAME.matcher(getClassName(fileVisitDetails)).matches()
        }

        fun isClass(fileVisitDetails: FileVisitDetails): Boolean {
            val fileName = fileVisitDetails.getFile().getName()
            return fileName.endsWith(".class") && "module-info.class" != fileName
        }

        override fun isReproducibleFileOrder(): Boolean {
            return true
        }
    }

    private fun getClassName(fileDetails: FileVisitDetails): String {
        return fileDetails.getRelativePath().getPathString().replace("\\.class".toRegex(), "").replace('/', '.')
    }

    companion object {
        private val ANONYMOUS_CLASS_NAME: Pattern = Pattern.compile(".*\\$\\d+")
    }
}
