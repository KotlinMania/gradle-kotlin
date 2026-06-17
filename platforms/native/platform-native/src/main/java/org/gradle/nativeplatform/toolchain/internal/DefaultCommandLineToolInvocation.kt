/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal

import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.logging.BuildOperationLogger
import java.io.File

internal class DefaultCommandLineToolInvocation(
    private val description: String,
    private val workDirectory: File?,
    private val args: Iterable<String?>?,
    private val context: CommandLineToolContext,
    private val oplogger: BuildOperationLogger?
) : CommandLineToolInvocation {
    override fun getArgs(): Iterable<String?>? {
        return args
    }

    override fun getLogger(): BuildOperationLogger? {
        return oplogger
    }

    override fun getWorkDirectory(): File? {
        return workDirectory
    }

    override fun getPath(): MutableList<File?>? {
        return context.getPath()
    }

    override fun getEnvironment(): MutableMap<String?, String?>? {
        return context.getEnvironment()
    }

    override fun description(): BuildOperationDescriptor.Builder {
        return BuildOperationDescriptor.displayName(description)
    }
}
