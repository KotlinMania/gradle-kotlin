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

import com.google.common.collect.ImmutableMap
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.api.plugins.internal.ant.AntWorkAction
import java.io.File
import java.util.Collections

abstract class AntJacocoReport : AntWorkAction<JacocoReportParameters>() {
    override fun getActionName(): String {
        return "jacoco-report"
    }

    public override fun execute(antBuilder: AntBuilderDelegate) {
        val params = getParameters()
        antBuilder.taskdef("jacocoReport", "org.jacoco.ant.ReportTask")
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
            if (params.getGenerateHtml().get()) {
                antBuilder.createNode(
                    "html",
                    ImmutableMap.of<String?, Any?>("destdir", params.getHtmlDestination().getAsFile().get())
                )
            }
            if (params.getGenerateXml().get()) {
                antBuilder.createNode(
                    "xml",
                    ImmutableMap.of<String?, Any?>("destfile", params.getXmlDestination().getAsFile().get())
                )
            }
            if (params.getGenerateCsv().get()) {
                antBuilder.createNode(
                    "csv",
                    ImmutableMap.of<String?, Any?>("destfile", params.getCsvDestination().getAsFile().get())
                )
            }
        })
    }
}
