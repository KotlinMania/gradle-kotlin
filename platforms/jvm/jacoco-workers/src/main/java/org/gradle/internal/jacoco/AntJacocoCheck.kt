/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.jacoco

import com.google.common.base.Joiner
import com.google.common.base.Predicate
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Sets
import org.gradle.api.GradleException
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.api.plugins.internal.ant.AntWorkAction
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule
import org.gradle.util.internal.GFileUtils
import java.io.File
import java.util.Collections

abstract class AntJacocoCheck : AntWorkAction<JacocoCoverageParameters>() {
    override fun getActionName(): String {
        return "jacoco-coverage"
    }

    public override fun execute(antBuilder: AntBuilderDelegate) {
        val params = getParameters()
        antBuilder.taskdef("jacocoReport", "org.jacoco.ant.ReportTask")
        try {
            antBuilder.createNode("jacocoReport", mutableMapOf<String?, Any?>(), Runnable {
                antBuilder.createNode("executiondata", mutableMapOf<String?, Any?>(), Runnable {
                    antBuilder.addFiles("resources", params.getExecutionData().filter(org.gradle.api.specs.Spec { obj: File? -> obj!!.exists() }))
                })
                val structureArgs: MutableMap<String?, Any?> = ImmutableMap.of<String?, Any?>("name", params.getProjectName().get())
                antBuilder.createNode("structure", structureArgs, Runnable {
                    antBuilder.createNode("classfiles", mutableMapOf<String?, Any?>(), Runnable {
                        antBuilder.addFiles("resources", params.getAllClassesDirs().filter(org.gradle.api.specs.Spec { obj: File? -> obj!!.exists() }))
                    })
                    val sourcefilesArgs: MutableMap<String?, Any?>
                    val encoding = params.getEncoding().getOrNull()
                    if (encoding == null) {
                        sourcefilesArgs = mutableMapOf<String?, Any?>()
                    } else {
                        sourcefilesArgs = Collections.singletonMap<String?, Any?>("encoding", encoding)
                    }
                    antBuilder.createNode("sourcefiles", sourcefilesArgs, Runnable {
                        antBuilder.addFiles("resources", params.getAllSourcesDirs().filter(org.gradle.api.specs.Spec { obj: File? -> obj!!.exists() }))
                    })
                })

                val rules: MutableSet<JacocoViolationRule> = Sets.filter<JacocoViolationRule?>(params.getRules().get(), RULE_ENABLED_PREDICATE)
                if (!rules.isEmpty()) {
                    val checkArgs: MutableMap<String?, Any?> = ImmutableMap.of<String?, Any?>(
                        "failonviolation", params.getFailOnViolation().get(),
                        "violationsproperty", VIOLATIONS_ANT_PROPERTY
                    )

                    antBuilder.createNode("check", checkArgs, Runnable {
                        for (rule in rules) {
                            val ruleArgs: MutableMap<String?, Any?> = ImmutableMap.of<String?, Any?>(
                                "element", rule.getElement(),
                                "includes", Joiner.on(':').join(rule.getIncludes()),
                                "excludes", Joiner.on(':').join(rule.getExcludes())
                            )
                            antBuilder.createNode("rule", ruleArgs, Runnable {
                                for (limit in rule.getLimits()) {
                                    val limitArgs: MutableMap<String?, Any?> = HashMap<String?, Any?>()
                                    limitArgs.put("counter", limit.getCounter())
                                    limitArgs.put("value", limit.getValue())

                                    if (limit.getMinimum() != null) {
                                        limitArgs.put("minimum", limit.getMinimum())
                                    }
                                    if (limit.getMaximum() != null) {
                                        limitArgs.put("maximum", limit.getMaximum())
                                    }

                                    antBuilder.createNode("limit", limitArgs)
                                }
                            })
                        }
                    })
                }
            })
        } catch (e: Exception) {
            val violations = getViolations(antBuilder)
            throw GradleException(if (violations == null) e.message else violations, e)
        }

        GFileUtils.touch(params.getDummyOutputFile().get().getAsFile())
    }

    private fun getViolations(antBuilder: AntBuilderDelegate): String? {
        return uncheckedCast<String?>(antBuilder.getProjectProperties().get(VIOLATIONS_ANT_PROPERTY))
    }

    companion object {
        private const val VIOLATIONS_ANT_PROPERTY = "jacocoViolations"
        private val RULE_ENABLED_PREDICATE: Predicate<JacocoViolationRule?> = object : Predicate<JacocoViolationRule?> {
            override fun apply(rule: JacocoViolationRule): Boolean {
                return rule.isEnabled()
            }
        }
    }
}
