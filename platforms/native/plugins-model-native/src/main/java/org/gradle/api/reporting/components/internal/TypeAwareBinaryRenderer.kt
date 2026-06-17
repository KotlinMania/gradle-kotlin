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
package org.gradle.api.reporting.components.internal

import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.platform.base.BinarySpec
import org.gradle.reporting.ReportRenderer
import java.io.IOException
import java.util.function.Function

@ServiceScope(Scope.Global::class)
class TypeAwareBinaryRenderer : ReportRenderer<BinarySpec?, TextReportBuilder?>() {
    private val renderers: MutableMap<Class<*>?, ReportRenderer<BinarySpec?, TextReportBuilder?>?> = HashMap<Class<*>?, ReportRenderer<BinarySpec?, TextReportBuilder?>?>()

    fun register(renderer: AbstractBinaryRenderer<*>) {
        renderers.put(renderer.getTargetType(), renderer)
    }

    @Throws(IOException::class)
    public override fun render(model: BinarySpec, output: TextReportBuilder?) {
        val renderer = getRendererForType(model.javaClass)
        renderer.render(model, output)
    }

    private fun getRendererForType(type: Class<out BinarySpec?>): ReportRenderer<BinarySpec?, TextReportBuilder?> {
        var renderer = renderers.get(type)
        if (renderer == null) {
            var bestType: Class<*>? = null
            for (entry in renderers.entries) {
                if (!entry.key!!.isAssignableFrom(type)) {
                    continue
                }
                if (bestType == null || bestType.isAssignableFrom(entry.key)) {
                    bestType = entry.key
                    renderer = entry.value
                }
            }
            renderers.put(type, renderer)
        }
        return renderer!!
    }

    companion object {
        val SORT_ORDER: Comparator<BinarySpec?> = Comparator.comparing<BinarySpec?, String?>(Function { obj: BinarySpec? -> obj!!.getName() })
    }
}
