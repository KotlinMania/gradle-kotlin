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
package org.gradle.language

/**
 * Represents a component that targets multiple target machines.
 *
 * @since 5.2
 */
interface ComponentWithTargetMachines {
    /**
     * Specifies the target machines this component should be built for.  The "machines" extension property (see [org.gradle.nativeplatform.TargetMachineFactory]) can be used to construct common operating system and architecture combinations.
     *
     *
     * For example:
     * <pre>
     * targetMachines = [machines.linux.x86_64, machines.windows.x86_64]
    </pre> *
     *
     * @since 5.2
     */
    @JvmField
    val targetMachines: SetProperty<TargetMachine?>?
}
