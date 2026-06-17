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
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.language.base.LanguageSourceSet
import org.gradle.model.internal.manage.schema.ModelSchema
import org.gradle.model.internal.manage.schema.ModelSchemaStore
import org.gradle.model.internal.manage.schema.StructSchema
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.internal.BinarySpecInternal
import org.gradle.platform.base.internal.VariantAspect
import org.gradle.reporting.ReportRenderer
import org.gradle.util.internal.GUtil
import java.util.TreeMap

// TODO - bust up this hierarchy and compose using interfaces instead
@ServiceScope(Scope.Global::class)
abstract class AbstractBinaryRenderer<T : BinarySpec?> protected constructor(private val schemaStore: ModelSchemaStore) : ReportRenderer<BinarySpec?, TextReportBuilder?>() {
    public override fun render(binary: BinarySpec, builder: TextReportBuilder) {
        var heading = StringUtils.capitalize(binary.getDisplayName())
        if (!binary.isBuildable()) {
            heading += " (not buildable)"
        }
        builder.heading(heading)

        if (binary.getBuildTask() != null) {
            builder.item("build using task", binary.getBuildTask()!!.getPath())
        }

        val specialized = this.targetType!!.cast(binary)

        renderTasks(specialized, builder)

        renderVariants(specialized, builder)

        renderDetails(specialized, builder)

        renderOutputs(specialized, builder)

        renderBuildAbility(specialized!!, builder)

        renderOwnedSourceSets(specialized, builder)
    }

    abstract val targetType: Class<T?>?

    protected open fun renderOutputs(binary: T?, builder: TextReportBuilder?) {
    }

    protected fun renderVariants(binary: T?, builder: TextReportBuilder) {
        val schema: ModelSchema<*>? = schemaStore.getSchema((binary as BinarySpecInternal).getPublicType())
        if (schema !is StructSchema<*>) {
            return
        }
        val variants: MutableMap<String?, Any?> = TreeMap<String?, Any?>()
        val variantAspect = schema.getAspect<VariantAspect?>(VariantAspect::class.java)
        if (variantAspect != null) {
            for (property in variantAspect.getDimensions()) {
                variants.put(property.getName(), property.getPropertyValue<T?>(binary))
            }
        }

        for (variant in variants.entries) {
            val variantName = GUtil.toWords(variant.key)
            builder.item(variantName, RendererUtils.displayValueOf(variant.value))
        }
    }

    protected open fun renderDetails(binary: T?, builder: TextReportBuilder?) {
    }

    protected open fun renderTasks(binary: T?, builder: TextReportBuilder?) {
    }

    private fun renderBuildAbility(binary: BinarySpec, builder: TextReportBuilder) {
        val buildAbility = (binary as BinarySpecInternal).getBuildAbility()
        if (!buildAbility.isBuildable()) {
            val formatter = TreeFormatter()
            buildAbility.explain(formatter)
            builder.item(formatter.toString())
        }
    }

    protected fun renderOwnedSourceSets(binary: T?, builder: TextReportBuilder) {
        if ((binary as BinarySpecInternal).isLegacyBinary()) {
            return
        }
        val sources = binary.getSources()
        if (!sources.isEmpty()) {
            val sourceSetRenderer = SourceSetRenderer()
            builder.collection<LanguageSourceSet?>("source sets", sources.values(), sourceSetRenderer, "source sets")
        }
    }
}
