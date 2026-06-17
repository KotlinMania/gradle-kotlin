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

import org.gradle.api.specs.Spec
import org.gradle.internal.reflect.JavaMethod
import org.gradle.model.Mutate
import org.gradle.model.internal.inspect.DefaultRuleSourceValidationProblemCollector
import org.gradle.model.internal.inspect.FormattingValidationProblemCollector
import org.gradle.model.internal.inspect.RuleSourceValidationProblemCollector
import org.gradle.model.internal.type.ModelType
import org.gradle.util.internal.CollectionUtils
import java.lang.reflect.Method
import java.util.Arrays
import java.util.LinkedList

class RuleSourceBackedRuleAction<R, T> private constructor(private val instance: R?, private val ruleMethod: JavaMethod<R?, T?>) : RuleAction<T?> {
    private val inputTypes: MutableList<Class<*>?>

    init {
        this.inputTypes = determineInputTypes(ruleMethod.parameterTypes)
    }

    override fun getInputTypes(): MutableList<Class<*>?> {
        return inputTypes
    }

    override fun execute(subject: T?, inputs: MutableList<*>) {
        val args = arrayOfNulls<Any>(inputs.size + 1)
        args[0] = subject
        for (i in inputs.indices) {
            val input: Any? = inputs.get(i)
            args[i + 1] = input
        }
        ruleMethod.invoke(instance, *args)
    }

    private class MultiMap<K, V> : HashMap<K?, MutableList<V?>>() {
        override fun get(key: Any?): MutableList<V?> {
            if (!containsKey(key)) {
                val keyCast = key as K?
                put(keyCast, LinkedList<V?>())
            }

            return super.get(key)!!
        }
    }

    companion object {
        fun <R, T> create(subjectType: ModelType<T?>, ruleSourceInstance: R?): RuleSourceBackedRuleAction<R?, T?> {
            val ruleSourceType = ModelType.typeOf<R?>(ruleSourceInstance)
            val mutateMethods: MutableList<Method> =
                findAllMethods(ruleSourceType.getConcreteClass(), org.gradle.api.specs.Spec { element: Method? -> element!!.isAnnotationPresent(Mutate::class.java) })
            val problemsFormatter = FormattingValidationProblemCollector("rule source", ruleSourceType)
            val problems: RuleSourceValidationProblemCollector = DefaultRuleSourceValidationProblemCollector(problemsFormatter)

            if (mutateMethods.size == 0) {
                problems.add("Must have at exactly one method annotated with @" + Mutate::class.java.getName())
            } else {
                if (mutateMethods.size > 1) {
                    problems.add("More than one method is annotated with @" + Mutate::class.java.getName())
                }

                for (ruleMethod in mutateMethods) {
                    if (ruleMethod.getReturnType() != Void.TYPE) {
                        problems.add(ruleMethod, "A rule method must return void")
                    }
                    val parameterTypes = ruleMethod.getGenericParameterTypes()
                    if (parameterTypes.size == 0 || !subjectType.isAssignableFrom(ModelType.of(parameterTypes[0]))) {
                        problems.add(ruleMethod, String.format("First parameter of a rule method must be of type %s", subjectType))
                    }
                }
            }

            if (problemsFormatter.hasProblems()) {
                throw RuleActionValidationException(problemsFormatter.format())
            }

            return RuleSourceBackedRuleAction<R?, T?>(ruleSourceInstance, JavaMethod<R?, T?>(subjectType.getConcreteClass(), mutateMethods.get(0)))
        }

        fun determineInputTypes(parameterTypes: Array<Class<*>?>): MutableList<Class<*>?> {
            return Arrays.asList<Class<*>?>(*parameterTypes).subList(1, parameterTypes.size)
        }

        private fun findAllMethods(target: Class<*>, predicate: Spec<Method?>): MutableList<Method> {
            return findAllMethodsInternal(target, predicate, MultiMap<String?, Method?>(), ArrayList<Method>(), false)
        }

        private fun findAllMethodsInternal(target: Class<*>, predicate: Spec<Method?>, seen: MultiMap<String?, Method?>, collector: MutableList<Method>, stopAtFirst: Boolean): MutableList<Method> {
            for (method in target.getDeclaredMethods()) {
                val seenWithName: MutableList<Method?> = seen.get(method.getName())!!
                val override: Method? = CollectionUtils.findFirst<Method?>(seenWithName, org.gradle.api.specs.Spec { potentionOverride: Method? ->
                    potentionOverride!!.getName() == method.getName()
                            && potentionOverride.getParameterTypes().contentEquals(method.getParameterTypes())
                })


                if (override == null) {
                    seenWithName.add(method)
                    if (predicate.isSatisfiedBy(method)) {
                        collector.add(method)
                        if (stopAtFirst) {
                            return collector
                        }
                    }
                }
            }

            val parent: Class<*>? = target.getSuperclass()
            if (parent != null) {
                return findAllMethodsInternal(parent, predicate, seen, collector, stopAtFirst)
            }

            return collector
        }
    }
}
