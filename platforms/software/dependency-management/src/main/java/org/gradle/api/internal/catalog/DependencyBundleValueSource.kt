/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.util.stream.Collectors

abstract class DependencyBundleValueSource : ValueSource<MutableList<DependencyModel>, DependencyBundleValueSource.Params> {
    internal interface Params : ValueSourceParameters {
        val bundleName: Property<String>?

        val config: Property<DefaultVersionCatalog>?
    }

    override fun obtain(): MutableList<DependencyModel> {
        val bundle = getParameters().bundleName.get()
        val config = getParameters().config.get()
        val bundleModel = config.getBundle(bundle)
        return bundleModel.getComponents().stream()
            .map<DependencyModel> { alias: String? -> config.getDependencyData(alias!!) }
            .collect(Collectors.toList())
    }
}
