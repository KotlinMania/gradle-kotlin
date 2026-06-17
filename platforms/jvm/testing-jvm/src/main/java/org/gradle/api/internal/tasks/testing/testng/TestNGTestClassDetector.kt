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
package org.gradle.api.internal.tasks.testing.testng

import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.tasks.testing.detection.TestClassVisitor
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector
import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.MethodVisitor

internal class TestNGTestClassDetector(detector: TestFrameworkDetector?) : TestClassVisitor(detector!!) {
    override fun ignoreNonStaticInnerClass(): Boolean {
        return false
    }

    override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
        if ("Lorg/testng/annotations/Test;" == desc) {
            setTest(true)
        }
        return null
    }

    override fun visitMethod(access: Int, name: String?, desc: String?, signature: String?, exceptions: Array<String?>?): MethodVisitor? {
        if (!isAbstract() && !isTest()) {
            return TestNGTestClassDetector.TestNGTestMethodDetector()
        } else {
            return null
        }
    }

    private inner class TestNGTestMethodDetector : MethodVisitor(AsmConstants.ASM_LEVEL) {
        override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
            if (TEST_METHOD_ANNOTATIONS.contains(desc)) {
                setTest(true)
            }
            return null
        }
    }

    companion object {
        private val TEST_METHOD_ANNOTATIONS: MutableSet<String?> = ImmutableSet.builder<String?>()
            .add("Lorg/testng/annotations/Test;")
            .add("Lorg/testng/annotations/BeforeSuite;")
            .add("Lorg/testng/annotations/AfterSuite;")
            .add("Lorg/testng/annotations/BeforeTest;")
            .add("Lorg/testng/annotations/AfterTest;")
            .add("Lorg/testng/annotations/BeforeGroups;")
            .add("Lorg/testng/annotations/AfterGroups;")
            .add("Lorg/testng/annotations/Factory;")
            .build()
    }
}
