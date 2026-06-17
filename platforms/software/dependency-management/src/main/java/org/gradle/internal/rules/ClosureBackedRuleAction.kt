/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.internal.rules

import groovy.lang.Closure
import java.util.Arrays

class ClosureBackedRuleAction<T>(subjectType: Class<T?>, closure: Closure<*>) : RuleAction<T?> {
    private val closure: Closure<*>
    private val subjectType: Class<in T?>
    private val inputTypes: MutableList<Class<*>?>

    init {
        this.subjectType = subjectType
        this.closure = closure
        this.inputTypes = parseInputTypes(closure)
    }

    override fun getInputTypes(): MutableList<Class<*>?> {
        return inputTypes
    }

    override fun execute(subject: T?, inputs: MutableList<*>) {
        val copy = closure.clone() as Closure<*>
        copy.setResolveStrategy(Closure.DELEGATE_FIRST)
        copy.setDelegate(subject)

        if (closure.getMaximumNumberOfParameters() == 0) {
            copy.call()
        } else {
            val argList = arrayOfNulls<Any>(inputs.size + 1)
            argList[0] = subject
            var i = 1
            for (arg in inputs) {
                argList[i++] = arg
            }
            copy.call(*argList)
        }
    }

    private fun parseInputTypes(closure: Closure<*>): MutableList<Class<*>?> {
        val parameterTypes = closure.getParameterTypes()
        val inputTypes: MutableList<Class<*>?> = ArrayList<Class<*>?>()

        if (parameterTypes.size != 0) {
            if (parameterTypes[0]!!.isAssignableFrom(subjectType)) {
                inputTypes.addAll(Arrays.asList<Class<*>?>(*parameterTypes).subList(1, parameterTypes.size))
            } else {
                throw RuleActionValidationException(String.format("First parameter of rule action closure must be of type '%s'.", subjectType.getSimpleName()))
            }
        }

        return inputTypes
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as ClosureBackedRuleAction<*>
        return closure == that.closure
                && subjectType == that.subjectType
    }

    override fun hashCode(): Int {
        var result = closure.hashCode()
        result = 31 * result + subjectType.hashCode()
        return result
    }
}
