/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.tasks.compile

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.io.Serializable

/**
 * Debug options for Java compilation.
 */
class DebugOptions : Serializable {
    /**
     * Get a comma-separated list of debug information to be generated during compilation.
     * The list may contain any of the following keywords (without spaces in between):
     *
     * <dl>
     * <dt>`source`
    </dt> * <dd>Source file debugging information
    </dd> * <dt>`lines`
    </dt> * <dd>Line number debugging information
    </dd> * <dt>`vars`
    </dt> * <dd>Local variable debugging information
    </dd></dl> *
     *
     *
     * Alternatively, a value of `none` means debug information will not be generated.
     *
     *
     * When the value is null, only source and line debugging information will be generated.
     */
    /**
     * Sets which debug information is to be generated during compilation. The value must be a
     * comma-separated list containing any of the following keywords (without spaces in between):
     *
     * <dl>
     * <dt>`source`
    </dt> * <dd>Source file debugging information
    </dd> * <dt>`lines`
    </dt> * <dd>Line number debugging information
    </dd> * <dt>`vars`
    </dt> * <dd>Local variable debugging information
    </dd></dl> *
     *
     *
     * For example `source,lines,vars` is a valid value.
     *
     *
     * Alternatively setting the value to `none` will disable debug information generation.
     *
     *
     * Setting this value to null will reset the property to its default value of only
     * generating line and source debug information.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var debugLevel: String? = null

    companion object {
        private const val serialVersionUID: Long = 0
    }
}
