/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.tooling

import org.gradle.api.Incubating
import kotlin.reflect.KClass

/**
 * Annotation used to mark TAPI model interfaces. On the client side such interfaces
 * are instantiated via Java dynamic proxies, and we use this annotation when we want
 * these proxies to have richer behaviour than just implementing the base interface.
 *
 *
 * For example. Let's say the TAPI model interface is `Animal`, but which also
 * has child interfaces such as `Dog`, `Cat`, `Bird`. When we
 * request a model typed as `Animal` on the TAPI client side, we will get
 * a Java dynamic proxy, on which we can call any method from the `Animal`
 * interface, but even if that model was originally let's say a `Dog` on the
 * Gradle daemon side, the proxy will not support any methods specific to `Dog`,
 * like let's say `Dog.bark()`.
 *
 *
 * Basically polymorphism isn't supported by default in TAPI client-side model instances.
 * This is what this annotation can be used to fix. We can use it to mark the base model
 * interface (`Animal` in the above example) and specify which of its subtypes
 * should be polymorpically handled on the TAPI client-side.
 *
 *
 * Let's say we define the `Animal` model interface like this:
 * <pre>
 * &#64;ToolingModelContract(subTypes = [Dog.class, Cat.class])
 * interface Animal {}
</pre> *
 *
 *
 * On the client-side we will be able to write code such as:
 * <pre>
 * if (model instanceof Dog) {
 * ((Dog) model).bark();
 * } else if (model instanceof Cat) {
 * ((Cat) model).meow();
 * } else if (model instance of Bird) {
 * // will never be true, Bird is not specified as a model contract type
 * }
</pre> *
 *
 * @since 8.9
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Incubating
annotation class ToolingModelContract(
    /**
     * Child types of a TAPI model interface (marked by this annotation), which will be
     * handled polymorpically on the TAPI client side.
     *
     * @since 8.9
     */
    val subTypes: Array<KClass<*>> = []
)
