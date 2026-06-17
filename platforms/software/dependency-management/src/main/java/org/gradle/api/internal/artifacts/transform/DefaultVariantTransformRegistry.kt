/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.NonExtensible
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.TransformRegistration
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.instantiation.InstantiationScheme
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolated.IsolationScheme
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.service.ServiceLookup
import java.util.function.Function
import javax.inject.Inject

class DefaultVariantTransformRegistry(
    private val instantiatorFactory: InstantiatorFactory,
    private val attributesFactory: AttributesFactory,
    private val services: ServiceLookup,
    private val registrationFactory: TransformRegistrationFactory,
    private val parametersInstantiationScheme: InstantiationScheme,
    private val documentationRegistry: DocumentationRegistry
) : VariantTransformRegistry {
    private val registeredTransforms: MutableSet<TransformRegistration> = LinkedHashSet<TransformRegistration>()

    private val isolationScheme =
        IsolationScheme<TransformAction<*>, TransformParameters>(TransformAction::class.java as Class<*>, TransformParameters::class.java, TransformParameters.None::class.java)

    override fun <T : TransformParameters?> registerTransform(actionType: Class<out TransformAction<T?>>, registrationAction: Action<in TransformSpec<T?>>) {
        doRegisterTransform<T?>(actionType, registrationAction)
    }

    val registrations: MutableSet<TransformRegistration>
        get() = ImmutableSet.copyOf<TransformRegistration>(registeredTransforms)

    private fun <T : TransformParameters?> doRegisterTransform(actionType: Class<out TransformAction<T?>>, registrationAction: Action<in TransformSpec<T?>>) {
        validateActionType(actionType as Class<out TransformAction<*>?>)

        var registration: TypedRegistration<T?>? = null
        try {
            val parameterType: Class<T?> = isolationScheme.parameterTypeFor(actionType)
            val parameterObject =
                isolationScheme.instantiateParameters<T?>(parameterType, Function { type: Class<T?>? -> parametersInstantiationScheme.withServices(services).instantiator().newInstance(type) })
            registration = uncheckedNonnullCast<TypedRegistration<T?>?>(
                instantiatorFactory.decorateLenient(services).newInstance<TypedRegistration<*>>(TypedRegistration::class.java, parameterObject, attributesFactory)
            )
            registrationAction.execute(registration)
            registration!!.validateAttributes()

            val finalizedRegistration = registrationFactory.create(registration.from.asImmutable(), registration.to.asImmutable(), actionType, parameterObject!!)
            registeredTransforms.add(finalizedRegistration)
        } catch (e: Exception) {
            throw VariantTransformConfigurationException(buildFailureToRegisterMsg(registration, actionType), e, documentationRegistry)
        }
    }

    private fun buildFailureToRegisterMsg(registration: TypedRegistration<*>?, actionType: Class<out TransformAction<*>>): String {
        val formatter = TreeFormatter()
        formatter.node("Could not register artifact transform ")
        formatter.appendType(actionType)
        if (registration != null && !(registration.from.isEmpty() && registration.to.isEmpty())) {
            formatter.append(" (")
            if (!registration.from.isEmpty()) {
                formatter.append("from ")
                formatter.appendValue(registration.from)
            }
            if (!registration.to.isEmpty()) {
                if (!registration.from.isEmpty()) {
                    formatter.append(" ")
                }
                formatter.append("to ")
                formatter.appendValue(registration.to)
            }
            formatter.append(")")
        }
        formatter.append(".")
        return formatter.toString()
    }

    private fun <T> validateActionType(actionType: Class<T?>?) {
        requireNotNull(actionType) { "An artifact transform action type must be provided." }
    }

    @NonExtensible
    abstract class TypedRegistration<T : TransformParameters?>(private val parameterObject: T?, attributesFactory: AttributesFactory) : TransformSpec<T?> {
        private val from: AttributeContainerInternal
        private val to: AttributeContainerInternal

        @Inject
        protected abstract fun getDocumentationRegistry(): DocumentationRegistry?

        init {
            this.from = attributesFactory.mutable()
            this.to = attributesFactory.mutable()
        }

        override fun getFrom(): AttributeContainer {
            return from
        }

        override fun getTo(): AttributeContainer {
            return to
        }

        override fun getParameters(): T? {
            return parameterObject
        }

        override fun parameters(action: Action<in T?>) {
            action.execute(parameterObject)
        }

        fun validateAttributes() {
            if (to.isEmpty()) {
                throw VariantTransformConfigurationException("At least one 'to' attribute must be provided.", getDocumentationRegistry()!!)
            }
            if (from.isEmpty()) {
                throw VariantTransformConfigurationException("At least one 'from' attribute must be provided.", getDocumentationRegistry()!!)
            }
            if (!from.keySet().containsAll(to.keySet())) {
                throw VariantTransformConfigurationException("Each 'to' attribute must be included as a 'from' attribute.", getDocumentationRegistry()!!)
            }
        }
    }
}
