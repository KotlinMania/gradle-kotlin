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

import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.gradle.api.internal.file.RelativeFile
import org.gradle.api.internal.tasks.testing.ClassTestDefinition
import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor
import org.gradle.internal.Factories
import org.gradle.internal.Factory
import org.gradle.internal.FileUtils
import org.gradle.internal.IoActions
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files

abstract class AbstractTestFrameworkDetector<T : TestClassVisitor?> protected constructor(classFileExtractionManager: ClassFileExtractionManager) : TestFrameworkDetector {
    private var testClassDirectories: MutableList<File?>? = null
    private val classFileExtractionManager: ClassFileExtractionManager
    private val superClasses: MutableMap<File?, Boolean?>
    private var testDefinitionProcessor: TestDefinitionProcessor<in ClassTestDefinition?>? = null

    private var testClassesDirectories: MutableList<File?>? = null
    private var testClasspath: MutableList<File>? = null

    init {
        checkNotNull(classFileExtractionManager)
        this.classFileExtractionManager = classFileExtractionManager
        this.superClasses = HashMap<File?, Boolean?>()
    }

    protected abstract fun createClassVisitor(): T?

    private fun getSuperTestClassFile(superClassName: String?): File? {
        prepareClasspath()
        require(!StringUtils.isEmpty(superClassName)) { "superClassName is empty!" }

        var superTestClassFile: File? = null
        for (testClassDirectory in testClassDirectories!!) {
            val candidate = File(testClassDirectory, superClassName + ".class")
            if (candidate.exists()) {
                superTestClassFile = candidate
            }
        }

        if (superTestClassFile != null) {
            return superTestClassFile
        } else if (JAVA_LANG_OBJECT == superClassName) {
            // java.lang.Object found, which is not a test class
            return null
        } else {
            // super test class file not in test class directories
            return classFileExtractionManager.getLibraryClassFile(superClassName!!)
        }
    }

    private fun prepareClasspath() {
        if (testClassDirectories != null) {
            return
        }

        testClassDirectories = ArrayList<File?>()

        if (testClassesDirectories != null) {
            testClassDirectories!!.addAll(testClassesDirectories!!)
        }
        if (testClasspath != null) {
            for (file in testClasspath) {
                if (file.isDirectory()) {
                    testClassDirectories!!.add(file)
                } else if (file.isFile() && FileUtils.hasExtension(file, ".jar")) {
                    classFileExtractionManager.addLibraryJar(file)
                }
            }
        }
    }

    override fun setTestClasses(testClassesDirectories: MutableList<File?>?) {
        this.testClassesDirectories = testClassesDirectories
    }

    override fun setTestClasspath(testClasspath: MutableList<File>?) {
        this.testClasspath = testClasspath
    }

    private fun readClassFile(testClassFile: File, fallbackClassNameProvider: Factory<String?>): TestClass {
        val classVisitor: TestClassVisitor = createClassVisitor()!!

        var classStream: InputStream? = null
        try {
            classStream = BufferedInputStream(Files.newInputStream(testClassFile.toPath()))
            val classReader = ClassReader(IOUtils.toByteArray(classStream))
            classReader.accept(classVisitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES)
            return TestClass.Companion.forParseableFile(classVisitor)
        } catch (e: Throwable) {
            LOGGER.debug("Failed to read class file " + testClassFile.getAbsolutePath() + "; assuming it's a test class and continuing", e)
            return TestClass.Companion.forUnparseableFile(fallbackClassNameProvider.create())
        } finally {
            IoActions.closeQuietly(classStream)
        }
    }

    override fun processTestClass(testClassFile: RelativeFile): Boolean {
        return processTestClass(testClassFile.getFile(), false, org.gradle.internal.Factory { testClassFile.getRelativePath().getPathString().replace(".class", "") })
    }

    /**
     * Uses a TestClassVisitor to detect whether the class in the testClassFile is a test class.
     *
     *
     * If the class is not a test, this function will go up the inheritance tree to check if a parent
     * class is a test class. First the package of the parent class is checked, if it is a java.lang or groovy.lang the class can't be a test class, otherwise the parent class is scanned.
     *
     *
     * When a parent class is a test class all the extending classes are marked as test classes.
     */
    private fun processTestClass(testClassFile: File, superClass: Boolean, fallbackClassNameProvider: Factory<String?>): Boolean {
        val testClass = readClassFile(testClassFile, fallbackClassNameProvider)

        var isTest = testClass.isTest

        if (!isTest) { // scan parent class
            val superClassName = testClass.superClassName

            if (isKnownTestCaseClassName(superClassName)) {
                isTest = true
            } else {
                val superClassFile: File? = getSuperTestClassFile(superClassName)

                if (superClassFile != null) {
                    isTest = processSuperClass(superClassFile, superClassName)
                } else {
                    LOGGER.debug(
                        "test-class-scan : failed to scan parent class {}, could not find the class file",
                        superClassName
                    )
                }
            }
        }

        maybePublishTestClass(isTest, testClass, superClass)

        return isTest
    }

    protected abstract fun isKnownTestCaseClassName(testCaseClassName: String?): Boolean

    private fun processSuperClass(testClassFile: File, superClassName: String): Boolean {
        val isTest: Boolean

        val isSuperTest = superClasses.get(testClassFile)

        if (isSuperTest == null) {
            isTest = processTestClass(testClassFile, true, Factories.constant<String?>(superClassName))

            superClasses.put(testClassFile, isTest)
        } else {
            isTest = isSuperTest
        }

        return isTest
    }

    /**
     * In none super class mode a test class is published when the class is a test and it is not abstract. In super class mode it must not publish the class otherwise it will get published multiple
     * times (for each extending class).
     */
    private fun maybePublishTestClass(isTest: Boolean, testClass: TestClass, superClass: Boolean) {
        if (isTest && !testClass.isAbstract && !superClass) {
            val className = Type.getObjectType(testClass.className).getClassName()
            testDefinitionProcessor!!.processTestDefinition(ClassTestDefinition(className))
        }
    }

    override fun startDetection(testDefinitionProcessor: TestDefinitionProcessor<in ClassTestDefinition?>) {
        this.testDefinitionProcessor = testDefinitionProcessor
    }

    private class TestClass(val isTest: Boolean, val isAbstract: Boolean, val className: String?, val superClassName: String) {
        companion object {
            fun forParseableFile(testClassVisitor: TestClassVisitor): TestClass {
                return TestClass(testClassVisitor.isTest(), testClassVisitor.isAbstract(), testClassVisitor.getClassName(), testClassVisitor.getSuperClassName())
            }

            fun forUnparseableFile(className: String?): TestClass {
                return AbstractTestFrameworkDetector.TestClass(true, false, className, null)
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(AbstractTestFrameworkDetector::class.java)
        private const val JAVA_LANG_OBJECT = "java/lang/Object"
    }
}
