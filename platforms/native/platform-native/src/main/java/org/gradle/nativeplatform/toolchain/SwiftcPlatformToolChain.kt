/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.nativeplatform.toolchain

import org.gradle.api.Incubating

/**
 * Swiftc specific settings for the tools used to build for a particular platform.
 *
 * @since 4.1
 */
@Incubating
interface SwiftcPlatformToolChain : NativePlatformToolChain {
    /**
     * Returns the compiler tool.
     */
    @JvmField
    val swiftCompiler: CommandLineToolConfiguration?

    /**
     * Returns the linker tool.
     */
    @JvmField
    val linker: CommandLineToolConfiguration?

    /**
     * Returns the settings to use for the archiver.
     *
     * @since 4.5
     */
    @JvmField
    val staticLibArchiver: CommandLineToolConfiguration?

    /**
     * Returns the tool for extracting symbols.
     *
     * @since 4.5
     */
    @JvmField
    val symbolExtractor: CommandLineToolConfiguration?

    /**
     * Returns the tool for stripping symbols.
     *
     * @since 4.5
     */
    @JvmField
    val stripper: CommandLineToolConfiguration?
}
