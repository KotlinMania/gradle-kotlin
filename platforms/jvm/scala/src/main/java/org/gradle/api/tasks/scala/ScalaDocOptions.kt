/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.io.Serializable

/**
 * Options for the ScalaDoc tool.
 */
abstract class ScalaDocOptions : Serializable {
    /**
     * Tells whether to generate deprecation information.
     */
    /**
     * Sets whether to generate deprecation information.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isDeprecation: Boolean = true
    /**
     * Tells whether to generate unchecked information.
     */
    /**
     * Sets whether to generate unchecked information.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isUnchecked: Boolean = true
    /**
     * Returns the text to appear in the window title.
     */
    /**
     * Sets the text to appear in the window title.
     */
    @get:Input
    @get:Optional
    @get:ToBeReplacedByLazyProperty
    var windowTitle: String? = null
    /**
     * Returns the HTML text to appear in the main frame title.
     */
    /**
     * Sets the HTML text to appear in the main frame title.
     */
    @get:Input
    @get:Optional
    @get:ToBeReplacedByLazyProperty
    var docTitle: String? = null
    /**
     * Returns the HTML text to appear in the header for each page.
     */
    /**
     * Sets the HTML text to appear in the header for each page.
     */
    @get:Input
    @get:Optional
    @get:ToBeReplacedByLazyProperty
    var header: String? = null
    /**
     * Returns the HTML text to appear in the footer for each page.
     */
    /**
     * Sets the HTML text to appear in the footer for each page.
     */
    @get:Input
    @get:Optional
    @get:ToBeReplacedByLazyProperty
    var footer: String? = null
    /**
     * Returns the HTML text to appear in the top text for each page.
     */
    /**
     * Sets the HTML text to appear in the top text for each page.
     */
    @get:Input
    @get:Optional
    @get:ToBeReplacedByLazyProperty
    var top: String? = null
    /**
     * Returns the HTML text to appear in the bottom text for each page.
     */
    /**
     * Sets the HTML text to appear in the bottom text for each page.
     */
    @get:Input
    @get:Optional
    @get:ToBeReplacedByLazyProperty
    var bottom: String? = null
    /**
     * Returns the additional parameters passed to the compiler.
     * Each parameter starts with '-'.
     */
    /**
     * Sets the additional parameters passed to the compiler.
     * Each parameter must start with '-'.
     */
    @get:Input
    @get:Optional
    @get:ToBeReplacedByLazyProperty
    var additionalParameters: MutableList<String?>? = null
}
