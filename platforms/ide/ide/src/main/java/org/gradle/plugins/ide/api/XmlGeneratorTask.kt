/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.api

import org.gradle.api.tasks.Internal
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObject
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObjectGenerator
import org.gradle.work.DisableCachingByDefault

/**
 * A convenience superclass for those tasks which generate XML configuration files from a domain object of type T.
 *
 * @param <T> The domain object type.
</T> */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class XmlGeneratorTask<T : PersistableConfigurationObject?> : GeneratorTask<T?>() {
    @get:Internal
    open val xmlTransformer: XmlTransformer = XmlTransformer()

    init {
        generator = object : PersistableConfigurationObjectGenerator<T?>() {
            override fun create(): T? {
                return this@XmlGeneratorTask.create()
            }

            override fun configure(`object`: T?) {
                this@XmlGeneratorTask.configure(`object`)
            }
        }
    }

    protected abstract fun configure(`object`: T?)

    protected abstract fun create(): T?
}
