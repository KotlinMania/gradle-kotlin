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
package org.gradle.api.internal.tasks.testing.junit

import org.gradle.api.internal.tasks.testing.detection.TestClassVisitor
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector
import org.gradle.model.internal.asm.AsmConstants
import org.jspecify.annotations.NullMarked
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.MethodVisitor

@NullMarked
class JUnitTestClassDetector internal constructor(detector: TestFrameworkDetector) : TestClassVisitor(detector) {
    override fun ignoreNonStaticInnerClass(): Boolean {
        return true
    }

    override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
        if ("Lorg/junit/runner/RunWith;" == desc) {
            setTest(true)
        }

        return null
    }

    override fun visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array<String>): MethodVisitor? {
        if (!isTest()) {
            return object : MethodVisitor(AsmConstants.ASM_LEVEL) {
                override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor {
                    if ("Lorg/junit/Test;" == desc) {
                        setTest(true)
                    }
                    return null
                }
            }
        } else {
            return null
        }
    }
}
