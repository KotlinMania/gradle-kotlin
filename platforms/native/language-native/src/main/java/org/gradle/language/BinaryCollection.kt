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
package org.gradle.language

import org.gradle.api.Action
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.specs.Spec

/**
 * A collection of binaries that are created and configured as they are required.
 *
 *
 * Each element in this collection passes through several states. The element is created and becomes 'known'. The element is passed to any actions registered using [.whenElementKnown]. The element is then configured using any actions registered using [.configureEach] and becomes 'finalized'. The element is passed to any actions registered using [.whenElementFinalized]. Elements are created and configured only when required.
 *
 * @param <T> type of the elements in this container.
 * @since 4.5
</T> */
interface BinaryCollection<T : SoftwareComponent?> {
    /**
     * Returns a [BinaryProvider] that contains the single binary matching the specified type and specification. The binary will be in the finalized state. The provider can be used to apply configuration to the element before it is finalized.
     *
     *
     * Querying the return value will fail when there is not exactly one matching binary.
     *
     * @param type type to match
     * @param spec specification to satisfy. The spec is applied to each binary *prior* to configuration.
     * @param <S> type of the binary to return
     * @return a binary from the collection in a finalized state
    </S> */
    fun <S> get(type: Class<S?>?, spec: Spec<in S?>?): BinaryProvider<S?>?

    /**
     * Returns a [BinaryProvider] that contains the single binary with the given name. The binary will be in the finalized state. The provider can be used to apply configuration to the element before it is finalized.
     *
     *
     * Querying the return value will fail when there is not exactly one matching binary.
     *
     * @param name The name of the binary
     * @return a binary from the collection in a finalized state
     */
    fun getByName(name: String?): BinaryProvider<T?>?

    /**
     * Returns a [Provider] that contains the single binary matching the given specification. The binary will be in the finalized state. The provider can be used to apply configuration to the element before it is finalized.
     *
     *
     * Querying the return value will fail when there is not exactly one matching binary.
     *
     * @param spec specification to satisfy. The spec is applied to each binary prior to configuration.
     * @return a binary from the collection in a finalized state
     */
    fun get(spec: Spec<in T?>?): BinaryProvider<T?>?

    /**
     * Registers an action to execute when an element becomes known. The action is only executed for those elements that are required. Fails if any element has already been finalized.
     *
     * @param action The action to execute for each element becomes known.
     */
    fun whenElementKnown(action: Action<in T?>?)

    /**
     * Registers an action to execute when an element of the given type becomes known. The action is only executed for those elements that are required. Fails if any matching element has already been finalized.
     *
     * @param type The type of element to select.
     * @param action The action to execute for each element becomes known.
     */
    fun <S> whenElementKnown(type: Class<S?>?, action: Action<in S?>?)

    /**
     * Registers an action to execute when an element is finalized. The action is only executed for those elements that are required. Fails if any element has already been finalized.
     *
     * @param action The action to execute for each element when finalized.
     */
    fun whenElementFinalized(action: Action<in T?>?)

    /**
     * Registers an action to execute when an element of the given type is finalized. The action is only executed for those elements that are required. Fails if any matching element has already been finalized.
     *
     * @param type The type of element to select.
     * @param action The action to execute for each element when finalized.
     */
    fun <S> whenElementFinalized(type: Class<S?>?, action: Action<in S?>?)

    /**
     * Registers an action to execute to configure each element in the collection. The action is only executed for those elements that are required. Fails if any element has already been finalized.
     *
     * @param action The action to execute on each element for configuration.
     */
    fun configureEach(action: Action<in T?>?)

    /**
     * Registers an action to execute to configure each element of the given type in the collection. The action is only executed for those elements that are required. Fails if any matching element has already been finalized.
     *
     * @param type The type of element to select.
     * @param action The action to execute on each element for configuration.
     */
    fun <S> configureEach(type: Class<S?>?, action: Action<in S?>?)

    /**
     * Returns the set of binaries from this collection. Elements are in a finalized state.
     */
    fun get(): MutableSet<T?>?
}
