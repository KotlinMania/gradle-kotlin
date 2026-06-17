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

import org.apache.commons.lang3.StringUtils
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder
import org.gradle.language.base.LanguageSourceSet
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.SourceComponentSpec
import org.gradle.platform.base.VariantComponentSpec
import org.gradle.reporting.ReportRenderer
import org.gradle.util.internal.CollectionUtils.sort

class ComponentRenderer(private val sourceSetRenderer: ReportRenderer<LanguageSourceSet?, TextReportBuilder?>?, private val binaryRenderer: ReportRenderer<BinarySpec?, TextReportBuilder?>?) :
    ReportRenderer<ComponentSpec?, TextReportBuilder?>() {
    public override fun render(component: ComponentSpec, builder: TextReportBuilder) {
        builder.heading(StringUtils.capitalize(component.getDisplayName()))
        if (component is SourceComponentSpec) {
            val sourceComponentSpec = component
            builder.getOutput().println()
            builder.collection<LanguageSourceSet?>(
                "Source sets",
                sort<LanguageSourceSet?>(sourceComponentSpec.getSources().values(), SourceSetRenderer.Companion.SORT_ORDER),
                sourceSetRenderer,
                "source sets"
            )
        }
        if (component is VariantComponentSpec) {
            val variantComponentSpec = component
            builder.getOutput().println()
            builder.collection<BinarySpec?>("Binaries", sort<BinarySpec?>(variantComponentSpec.getBinaries().values(), TypeAwareBinaryRenderer.Companion.SORT_ORDER), binaryRenderer, "binaries")
        }
    }
}
