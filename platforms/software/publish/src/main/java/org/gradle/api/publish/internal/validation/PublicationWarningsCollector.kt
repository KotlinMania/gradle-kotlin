/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.publish.internal.validation

import org.gradle.api.logging.Logger
import org.gradle.internal.logging.text.TreeFormatter
import java.util.TreeMap
import java.util.function.Consumer
import javax.annotation.concurrent.NotThreadSafe

@NotThreadSafe
class PublicationWarningsCollector(
    variantToWarnings: MutableMap<String?, VariantWarningCollector?>?,
    private val logger: Logger,
    private val unsupportedFeature: String,
    private val incompatibleFeature: String,
    private val footer: String,
    private val disableMethod: String?
) {
    private val variantToWarnings: MutableMap<String?, VariantWarningCollector?>

    init {
        this.variantToWarnings = TreeMap<String?, VariantWarningCollector?>(variantToWarnings)
    }

    fun complete(header: String?, silencedVariants: MutableSet<String?>) {
        variantToWarnings.keys.removeAll(silencedVariants)
        variantToWarnings.values.removeIf { obj: VariantWarningCollector? -> obj!!.isEmpty() }
        if (!variantToWarnings.isEmpty()) {
            val treeFormatter = TreeFormatter()
            treeFormatter.node(header + " warnings (silence with '" + disableMethod + "(variant)')")
            treeFormatter.startChildren()
            variantToWarnings.forEach { (key: String?, warnings: VariantWarningCollector?) ->
                treeFormatter.node("Variant " + key + ":")
                treeFormatter.startChildren()
                if (warnings!!.getVariantUnsupported() != null) {
                    warnings.getVariantUnsupported().forEach(Consumer { text: String? -> treeFormatter.node(text) })
                }
                if (warnings.getUnsupportedUsages() != null) {
                    treeFormatter.node(unsupportedFeature)
                    treeFormatter.startChildren()
                    warnings.getUnsupportedUsages().forEach(Consumer { text: String? -> treeFormatter.node(text) })
                    treeFormatter.endChildren()
                }
                if (warnings.getIncompatibleUsages() != null) {
                    treeFormatter.node(incompatibleFeature)
                    treeFormatter.startChildren()
                    warnings.getIncompatibleUsages().forEach(Consumer { text: String? -> treeFormatter.node(text) })
                    treeFormatter.endChildren()
                }
                treeFormatter.endChildren()
            }
            treeFormatter.endChildren()
            treeFormatter.node(footer)
            logger.lifecycle(treeFormatter.toString())
        }
    }
}
