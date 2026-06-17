/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

/**
 * Base class for ASM test class scanners.
 */
abstract class TestClassVisitor protected constructor(detector: TestFrameworkDetector) : ClassVisitor(AsmConstants.ASM_LEVEL) {
    protected val detector: TestFrameworkDetector
    var isAbstract: Boolean = false
        private set
    var className: String? = null
        private set
    var superClassName: String? = null
        private set
    var isTest: Boolean = false
        protected set

    init {
        requireNotNull(detector) { "detector == null!" }
        this.detector = detector
    }

    override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<String?>?) {
        isAbstract = (access and Opcodes.ACC_ABSTRACT) != 0
        className = name
        superClassName = superName
    }

    override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
        if (ignoreNonStaticInnerClass() && innerClassIsNonStatic(name, access)) {
            isAbstract = true
        }
    }

    protected abstract fun ignoreNonStaticInnerClass(): Boolean

    private fun innerClassIsNonStatic(name: String, access: Int): Boolean {
        return name == this.className && (access and Opcodes.ACC_STATIC) == 0
    }
}
