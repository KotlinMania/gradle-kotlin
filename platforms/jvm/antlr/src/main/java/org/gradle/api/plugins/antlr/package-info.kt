/*
 * Copyright 2010 the original author or authors.
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
/**
 * A [org.gradle.api.Plugin] for generating parsers from Antlr grammars.
 */
package org.gradle.api.plugins.antlr

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.os.OperatingSystem.isWindows
import org.gradle.internal.deprecation.DeprecationLogger.deprecateAction
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBecomeAnErrorInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withDslReference
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.internal.file.FilePathUtil.maybeRemoveTrailingSegments
import org.gradle.internal.file.Deleter.ensureEmptyDirectory
import org.gradle.process.internal.JavaExecHandleBuilder.maxHeapSize
import org.gradle.process.internal.JavaExecHandleBuilder.systemProperty
import org.gradle.process.internal.JavaExecHandleBuilder.redirectErrorStream
import antlr.collections.impl.IndexedVector
import antlr.preprocessor.GrammarFile

