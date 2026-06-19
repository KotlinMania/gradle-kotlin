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
package org.gradle.internal.service

import org.gradle.internal.collect.PersistentArray
import org.gradle.internal.collect.PersistentList
import org.gradle.internal.collect.PersistentMap
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.util.internal.CollectionUtils
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Arrays
import java.util.Formatter
import java.util.HashSet
import java.util.Queue
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.concurrent.Volatile

/**
 * A hierarchical [ServiceRegistry] implementation.
 *
 * Service instances are closed when the registry that created them is closed using [.close].
 * If a service instance implements [java.io.Closeable] or [org.gradle.internal.concurrent.Stoppable]
 * then the appropriate [java.io.Closeable.close] or [Stoppable.stop] method is called.
 * Instances are closed in reverse dependency order.
 *
 * Service registries are arranged in a hierarchy. If a service of a given type cannot be located, the registry uses its parent registry, if any, to locate the service.
 *
 * Service interfaces should be annotated with [org.gradle.internal.service.scopes.ServiceScope] to indicate their intended usage.
 *
 * Service interfaces can be annotated with [org.gradle.internal.service.scopes.StatefulListener] to indicate that services instances that implement the interface should
 * be registered as a listener of that type. Alternatively, service implementations can be annotated with [org.gradle.internal.service.scopes.ListenerService] to indicate that the should be
 * registered as a listener.
 */
internal open class DefaultServiceRegistry(
    private val displayName: String?,
    private val parents: Array<out ServiceRegistry>
) : AbstractServiceRegistry(), CloseableServiceRegistry {

    private enum class State {
        INIT, STARTED, CLOSED
    }

    private val inspector: ClassInspector
    private val ownServices: OwnServices
    private val allServices: ServiceProvider
    private val parentServices: ServiceProvider?
    private val thisAsServiceProvider: ServiceProvider

    private val state = AtomicReference<State>(State.INIT)

    constructor() : this(null, NO_PARENTS)

    constructor(displayName: String?) : this(displayName, NO_PARENTS)

    constructor(vararg parents: ServiceRegistry) : this(null, parents)

    init {
        assertAllowedImplementation(javaClass)

        ownServices = OwnServices()
        if (parents.size == 0) {
            parentServices = null
            allServices = ownServices
            inspector = ClassInspector()
        } else {
            parentServices = setupParentServices(parents)
            allServices = CompositeServiceProvider(ownServices, parentServices)
            inspector = if (parents[0] is DefaultServiceRegistry) {
                (parents[0] as DefaultServiceRegistry).inspector
            } else {
                ClassInspector()
            }
        }
        thisAsServiceProvider = allServices
    }

    override fun asServiceProvider(): ServiceProvider {
        return thisAsServiceProvider
    }

    companion object {
        private val NO_PARENTS = arrayOf<ServiceRegistry>()
        private val NO_DEPENDENTS = arrayOfNulls<Service>(0)
        private val NO_PARAMS = arrayOfNulls<Any>(0)

        // Simulation of a sealed class with public constructors in Java 8
        private fun assertAllowedImplementation(impl: Class<out DefaultServiceRegistry>) {
            if (impl != ScopedServiceRegistry::class.java && impl != DefaultServiceRegistry::class.java) {
                throw IllegalArgumentException(
                    String.format(
                        "Inheriting from %s is not allowed. Use ServiceRegistryBuilder instead.",
                        DefaultServiceRegistry::class.java.getSimpleName()
                    )
                )
            }
        }

        private fun setupParentServices(parents: Array<out ServiceRegistry>): ServiceProvider {
            return if (parents.size == 1) {
                toParentServices(parents[0])
            } else {
                val parentServiceProviders = parents.map { toParentServices(it) }.toTypedArray()
                CompositeServiceProvider(*parentServiceProviders)
            }
        }

        private fun toParentServices(serviceRegistry: ServiceRegistry): ServiceProvider {
            if (serviceRegistry is AbstractServiceRegistry) {
                return ParentServices(serviceRegistry.asServiceProvider())
            }
            throw IllegalArgumentException(
                String.format(
                    "Service registry %s cannot be used as a parent for another service registry. Expected an instance of %s but got %s.",
                    serviceRegistry,
                    AbstractServiceRegistry::class.java.getSimpleName(),
                    serviceRegistry.javaClass.getSimpleName()
                )
            )
        }

        /**
         * Creates a service registry that uses the given providers.
         */
        @JvmStatic
        fun create(vararg providers: ServiceRegistrationProvider): ServiceRegistry {
            val registry = DefaultServiceRegistry()
            for (provider in providers) {
                registry.addProvider(provider)
            }
            return registry
        }
    }

    private enum class ServiceState {
        UNBOUND, BINDING, BOUND
    }

    private fun serviceTypesOf(method: ServiceMethod): List<Type> {
        val provides = method.method.getAnnotation(Provides::class.java)
        val explicitServiceTypes = provides?.value ?: emptyArray()
        return if (explicitServiceTypes.isEmpty()) {
            listOf(method.serviceType)
        } else {
            explicitServiceTypes.map { it.java }
        }
    }

    private fun getDisplayName(): String {
        return displayName ?: this.javaClass.getSimpleName()
    }

    override fun toString(): String {
        return getDisplayName()
    }

    private fun findProviderMethods(target: ServiceRegistrationProvider, token: ServiceAccessToken) {
        val type = target.javaClass
        val methods = RelevantMethods.getMethods(type)
        for (method in methods.decorators) {
            if (parentServices == null) {
                throw ServiceLookupException(
                    String.format(
                        "Cannot use decorator method %s.%s() when no parent registry is provided.",
                        type.getSimpleName(),
                        method.name
                    )
                )
            }
            ownServices.add(FactoryMethodService(this, determineAccessScope(method, token), token, target, method))
        }
        for (method in methods.factories) {
            ownServices.add(FactoryMethodService(this, determineAccessScope(method, token), token, target, method))
        }
        for (method in methods.configurers) {
            applyConfigureMethod(token, method, target)
        }
    }

    private fun determineAccessScope(method: ServiceMethod, token: ServiceAccessToken): ServiceAccessScope {
        val privateService = method.method.getAnnotation(PrivateService::class.java)
        return if (privateService != null) ServiceAccess.getPrivateScope(token) else ServiceAccess.publicScope
    }

    private fun applyConfigureMethod(token: ServiceAccessToken, method: ServiceMethod, target: Any?) {
        val params = arrayOfNulls<Any?>(method.parameterTypes.size)
        for (i in method.parameterTypes.indices) {
            val paramType = method.parameterTypes[i]
            if (paramType == ServiceRegistration::class.java) {
                params[i] = newRegistration(token)
            } else {
                val paramProvider = findByType(paramType, token, allServices)
                if (paramProvider == null) {
                    throw ServiceLookupException(
                        String.format(
                            "Cannot configure services using %s.%s() as required service of type %s is not available.",
                            method.owner.getSimpleName(),
                            method.name,
                            format(paramType)
                        )
                    )
                }
                params[i] = paramProvider.get()
            }
        }
        try {
            method.invoke(target, *params)
        } catch (e: Exception) {
            throw ServiceLookupException(
                String.format(
                    "Could not configure services using %s.%s().",
                    method.owner.getSimpleName(),
                    method.name
                ),
                e
            )
        }
    }

    /**
     * Adds services to this container using the given action.
     */
    fun register(action: ServiceRegistrationAction) {
        assertMutable()
        val token = ServiceAccess.createToken(format(action.javaClass))
        action.registerServices(newRegistration(token))
    }

    private fun assertMutable() {
        check(state.get() == State.INIT) { "Cannot add services to service registry $this as it is no longer mutable" }
    }

    private fun newRegistration(token: ServiceAccessToken): ServiceRegistration {
        return object : ServiceRegistration {
            override fun <T> add(serviceType: Class<T?>?, serviceInstance: T?) {
                this@DefaultServiceRegistry.add(ServiceAccess.publicScope, serviceType, serviceInstance)
            }

            override fun add(serviceType: Class<*>?) {
                ownServices.add(ConstructorService(this@DefaultServiceRegistry, ServiceAccess.publicScope, token, requireNotNull(serviceType)))
            }

            override fun <T> add(serviceType: Class<in T?>?, implementationType: Class<T?>?) {
                ownServices.add(
                    ConstructorService(
                        this@DefaultServiceRegistry,
                        ServiceAccess.publicScope,
                        token,
                        serviceType as Class<*>,
                        implementationType as Class<*>
                    )
                )
            }

            override fun <T> add(serviceType1: Class<in T?>?, serviceType2: Class<in T?>?, implementationType: Class<T?>?) {
                val serviceTypes: List<Type> = listOf(requireNotNull(serviceType1), requireNotNull(serviceType2))
                @Suppress("UNCHECKED_CAST")
                val implementationClass = requireNotNull(implementationType) as Class<*>
                ownServices.add(
                    ConstructorService(
                        this@DefaultServiceRegistry,
                        ServiceAccess.publicScope,
                        token,
                        serviceTypes,
                        implementationClass
                    )
                )
            }

            override fun addProvider(provider: ServiceRegistrationProvider?) {
                // The access token is intentionally not forwarded here
                this@DefaultServiceRegistry.addProvider(provider!!)
            }
        }
    }

    /**
     * Adds a service instance to this registry with the given public type. The given object is closed when this registry is closed.
     */
    fun <T> add(serviceType: Class<out T?>?, serviceInstance: T?): DefaultServiceRegistry {
        add(ServiceAccess.publicScope, serviceType, serviceInstance)
        return this
    }

    /**
     * Adds a service instance to this registry. The given object is closed when this registry is closed.
     */
    fun add(serviceInstance: Any): DefaultServiceRegistry {
        @Suppress("UNCHECKED_CAST")
        return add(serviceInstance.javaClass as Class<Any?>?, serviceInstance)
    }

    private fun <T> add(accessScope: ServiceAccessScope, serviceType: Class<out T?>?, serviceInstance: T?) {
        requireNotNull(serviceType)
        requireNotNull(serviceInstance)
        assertMutable()
        ownServices.add(FixedInstanceService(this, accessScope, serviceType, serviceInstance))
    }

    /**
     * Adds a service provider bean to this registry. This provider may define factory and decorator methods.
     */
    fun addProvider(provider: ServiceRegistrationProvider): DefaultServiceRegistry {
        assertMutable()
        val token = ServiceAccess.createToken(format(provider.javaClass))
        findProviderMethods(provider, token)
        return this
    }

    /**
     * Closes all services for this registry. For each service, if the service has a public void close() or stop() method, that method is called to close the service.
     */
    override fun close() {
        noLongerMutable()
        if (state.compareAndSet(State.STARTED, State.CLOSED)) {
            CompositeStoppable.stoppable(allServices).stop()
        }
    }

    private fun serviceRequested() {
        noLongerMutable()
        if (state.get() == State.CLOSED) {
            throw IllegalStateException(String.format("%s has been closed.", getDisplayName()))
        }
    }

    private fun noLongerMutable() {
        state.compareAndSet(State.INIT, State.STARTED)
    }

    val isClosed: Boolean
        get() = state.get() == State.CLOSED

    @Throws(UnknownServiceException::class, ServiceLookupException::class)
    override fun <T> get(serviceType: Class<T?>): T? {
        @Suppress("UNCHECKED_CAST")
        return serviceType.cast(get(serviceType as Type))
    }

    @Throws(UnknownServiceException::class, ServiceLookupException::class)
    override fun get(serviceType: Type): Any? {
        val instance = find(serviceType)
        if (instance == null) {
            throw UnknownServiceException(serviceType, String.format("No service of type %s available in %s.", format(serviceType), getDisplayName()))
        }
        return instance
    }

    @Throws(UnknownServiceException::class, ServiceLookupException::class)
    override fun get(serviceType: Type, annotatedWith: Class<out Annotation>): Any? {
        throw UnknownServiceException(
            serviceType,
            String.format(
                "No service of type %s annotated with @%s available in %s.",
                format(serviceType),
                annotatedWith.getSimpleName(),
                getDisplayName()
            )
        )
    }

    @Throws(ServiceLookupException::class)
    override fun find(serviceType: Type): Any? {
        assertValidServiceType(unwrap(serviceType))
        val provider = getService(serviceType)
        return provider?.get()
    }

    @Throws(ServiceLookupException::class)
    private fun getService(serviceType: Type?): Service? {
        if (serviceType == null) {
            return null
        }
        serviceRequested()
        return findByType(serviceType, null, allServices)
    }

    @Throws(ServiceLookupException::class)
    override fun <T> getAll(serviceType: Class<T?>): MutableList<T?>? {
        assertValidServiceType(serviceType)
        val services = ArrayList<T?>()
        serviceRequested()
        allServices.getAll(serviceType, null, InstanceUnpackingVisitor(serviceType, services))
        return services
    }

    private class InstanceUnpackingVisitor<T>(private val serviceType: Class<T?>, private val delegate: MutableList<T?>) : ServiceProvider.Visitor {
        override fun visit(service: Service) {
            val instance = serviceType.cast(service.get())
            if (!delegate.contains(instance)) {
                delegate.add(instance)
            }
        }
    }

    private class CollectingVisitor(private val delegate: MutableList<Service>) : ServiceProvider.Visitor {
        override fun visit(service: Service) {
            if (!delegate.contains(service)) {
                delegate.add(service)
            }
        }
    }

    private inner class OwnServices : ServiceProvider {
        private val stoppable = CompositeStoppable.stoppable()
        private val services: AtomicReference<ServicesSnapshot>

        init {
            val thisServiceRegistry = ThisAsService(ServiceAccess.publicScope)
            services = AtomicReference(
                ServicesSnapshot.of(
                    PersistentMap.of<Class<*>, PersistentArray<ServiceProvider>>(
                        ServiceRegistry::class.java,
                        PersistentArray.of(thisServiceRegistry)
                    )
                )
            )
        }

        override fun getService(type: Type, token: ServiceAccessToken?): Service? {
            val serviceProviders = getProviders(unwrap(type))
            if (serviceProviders.isEmpty()) {
                return null
            }
            if (serviceProviders.size() == 1) {
                return serviceProviders.get(0).getService(type, token)
            }

            val services = ArrayList<Service>(serviceProviders.size())
            for (serviceProvider in serviceProviders) {
                val service = serviceProvider.getService(type, token)
                if (service != null) {
                    services.add(service)
                }
            }
            if (services.isEmpty()) {
                return null
            }
            if (services.size == 1) {
                return services.get(0)
            }

            val descriptions = TreeSet<String>()
            for (candidate in services) {
                descriptions.add(candidate.getDisplayName())
            }

            val formatter = Formatter()
            formatter.format("Multiple services of type %s available in %s:", format(type), getDisplayName())
            for (description in descriptions) {
                formatter.format("%n   - %s", description)
            }
            throw ServiceLookupException(formatter.toString())
        }

        private fun getProviders(type: Class<*>): PersistentArray<ServiceProvider> {
            val providersByType: PersistentMap<Class<*>, PersistentArray<ServiceProvider>> = services.get().providersByType
            return providersByType.getOrDefault(type, PersistentArray.of())
        }

        override fun getAll(serviceType: Class<*>, token: ServiceAccessToken?, visitor: ServiceProvider.Visitor): ServiceProvider.Visitor {
            var currentVisitor = visitor
            for (serviceProvider in getProviders(serviceType)) {
                currentVisitor = serviceProvider.getAll(serviceType, token, currentVisitor)
            }
            return currentVisitor
        }

        override fun stop() {
            stoppable.stop()
        }

        fun add(serviceProvider: SingletonService) {
            assertMutable()
            stoppable.add(serviceProvider)
            val snapshot = services.updateAndGet { it.addService(serviceProvider, inspector) }
            for (annotationHandler in snapshot.lifecycleHandlers) {
                notifyAnnotationHandler(annotationHandler, serviceProvider)
            }
        }

        fun instanceRealized(declaredServiceTypes: List<Class<*>>, displayName: Supplier<String>, instance: Any) {
            if (instance is AnnotatedServiceLifecycleHandler && !isAssignableFromAnyType(AnnotatedServiceLifecycleHandler::class.java, declaredServiceTypes)) {
                throw IllegalStateException(
                    String.format(
                        "%s implements %s but is not declared as a service of this type. This service is declared as having %s.",
                        displayName.get(),
                        AnnotatedServiceLifecycleHandler::class.java.getSimpleName(),
                        format("type", declaredServiceTypes)
                    )
                )
            }
            if (instance is AnnotatedServiceLifecycleHandler) {
                annotationHandlerCreated(instance)
            }

            val lifecycleHandlers: PersistentArray<AnnotatedServiceLifecycleHandler> = services.get().lifecycleHandlers
            for (lifecycleHandler in lifecycleHandlers) {
                for (annotation in lifecycleHandler.annotations.orEmpty()) {
                    val actual = annotation ?: continue
                    val implementationHasAnnotation = inspector.hasAnnotation(instance.javaClass, actual)
                    val declaredWithAnnotation = anyTypeHasAnnotation(actual, declaredServiceTypes)
                    if (implementationHasAnnotation && !declaredWithAnnotation) {
                        throw IllegalStateException(
                            String.format(
                                "%s is annotated with @%s but is not declared as a service with this annotation. This service is declared as having %s.",
                                displayName.get(),
                                format(actual),
                                format("type", declaredServiceTypes)
                            )
                        )
                    }
                }
            }
        }

        private fun annotationHandlerCreated(annotationHandler: AnnotatedServiceLifecycleHandler) {
            val snapshot = services.updateAndGet { it.addLifecycleHandler(annotationHandler) }
            for (service in snapshot.services) {
                notifyAnnotationHandler(annotationHandler, service)
            }
        }

        private fun notifyAnnotationHandler(annotationHandler: AnnotatedServiceLifecycleHandler, candidate: SingletonService) {
            if (annotationHandler.implicitAnnotation != null) {
                annotationHandler.whenRegistered(
                    annotationHandler.implicitAnnotation,
                    RegistrationWrapper(candidate)
                )
            } else {
                val declaredServiceTypes = candidate.getDeclaredServiceTypes()
                for (annotation in annotationHandler.annotations.orEmpty()) {
                    val actual = annotation ?: continue
                    if (anyTypeHasAnnotation(actual, declaredServiceTypes)) {
                        annotationHandler.whenRegistered(actual, RegistrationWrapper(candidate))
                    }
                }
            }
        }

        private fun anyTypeHasAnnotation(annotation: Class<out Annotation?>?, types: List<Class<*>>): Boolean {
            if (annotation == null) {
                return false
            }
            for (type in types) {
                if (inspector.hasAnnotation(type, annotation)) {
                    return true
                }
            }
            return false
        }

        override fun toString(): String {
            return getDisplayName()
        }
    }

    private inner class RegistrationWrapper(private val serviceProvider: SingletonService) : AnnotatedServiceLifecycleHandler.Registration {
        override val declaredTypes: MutableList<Class<*>?>?
            get() = serviceProvider.getDeclaredServiceTypes().map { it as Class<*>? }.toMutableList()

        override val instance: Any?
            get() {
                serviceRequested()
                return serviceProvider.preparedInstance
            }
    }

    private inner abstract class ManagedObjectServiceProvider(protected val owner: DefaultServiceRegistry) : ServiceProvider, Service {
        private val dependents: Queue<ServiceProvider?> = ArrayDeque<ServiceProvider?>()

        @Volatile
        private var instance: Any? = null

        internal abstract fun getDeclaredServiceTypes(): MutableList<Class<*>>

        protected open fun instanceRealized(instance: Any) {
            owner.ownServices.instanceRealized(getDeclaredServiceTypes(), this::getDisplayName, instance)
        }

        protected fun setInstance(instance: Any) {
            instanceRealized(instance)
            this.instance = instance
        }

        protected fun getInstance(): Any {
            var result = instance
            if (result == null) {
                synchronized(this) {
                    result = instance
                    if (result == null) {
                        val created = createServiceInstance()
                        setInstance(created)
                        result = created
                    }
                }
            }
            return result!!
        }

        /**
         * Subclasses implement this method to create the service instance. It is never called concurrently and may not return null.
         */
        protected abstract fun createServiceInstance(): Any

        override fun requiredBy(serviceProvider: ServiceProvider?) {
            if (fromSameRegistry(serviceProvider)) {
                dependents.add(serviceProvider)
            }
        }

        private fun fromSameRegistry(serviceProvider: ServiceProvider?): Boolean {
            return serviceProvider is ManagedObjectServiceProvider && serviceProvider.owner === owner
        }

        override fun stop() {
            try {
                val theInstance = instance
                if (theInstance != null) {
                    CompositeStoppable.stoppable(dependents).add(theInstance).stop()
                }
            } finally {
                dependents.clear()
                instance = null
            }
        }
    }

    private inner abstract class SingletonService(
        owner: DefaultServiceRegistry,
        protected val accessScope: ServiceAccessScope,
        protected val serviceTypes: List<out Type>
    ) : ManagedObjectServiceProvider(owner) {
        private val serviceTypesAsClasses: List<Class<*>>

        private var state = ServiceState.UNBOUND

        init {
            require(serviceTypes.isNotEmpty()) { "Expected at least one declared service type" }

            serviceTypesAsClasses = serviceTypes.asSequence()
                .map { unwrap(it) }
                .toMutableList()
        }

        override fun getDeclaredServiceTypes(): MutableList<Class<*>> {
            return serviceTypesAsClasses.toMutableList()
        }

        override fun getDisplayName(): String {
            return format("Service", serviceTypes)
        }

        override fun toString(): String {
            return getDisplayName()
        }

        override fun get(): Any {
            return getInstance()
        }

        val preparedInstance: Any
            get() = prepare().get()

        private fun prepare(): Service {
            if (state == ServiceState.BOUND) {
                return this
            }
            synchronized(this) {
                if (state == ServiceState.BINDING) {
                    throw ServiceValidationException("Cycle in dependencies of " + getDisplayName() + " detected")
                }
                if (state == ServiceState.UNBOUND) {
                    state = ServiceState.BINDING
                    try {
                        bind()
                        state = ServiceState.BOUND
                    } catch (e: RuntimeException) {
                        state = ServiceState.UNBOUND
                        throw e
                    }
                }
                return this
            }
        }

        /**
         * Do any preparation work and validation to ensure that [createServiceInstance] can be called later.
         * This method is never called concurrently.
         */
        protected open fun bind() {
        }

        override fun getService(serviceType: Type, token: ServiceAccessToken?): Service? {
            if (!accessScope.contains(token)) {
                return null
            }
            if (!isSatisfiedByAny(serviceType, serviceTypes)) {
                return null
            }
            return prepare()
        }

        override fun getAll(serviceType: Class<*>, token: ServiceAccessToken?, visitor: ServiceProvider.Visitor): ServiceProvider.Visitor {
            if (!accessScope.contains(token)) {
                return visitor
            }
            if (isAssignableFromAnyType(serviceType, serviceTypesAsClasses)) {
                visitor.visit(prepare())
            }
            return visitor
        }
    }

    private inner abstract class FactoryService(
        owner: DefaultServiceRegistry,
        accessScope: ServiceAccessScope,
        private val accessToken: ServiceAccessToken,
        serviceTypes: List<out Type>
    ) : SingletonService(owner, accessScope, serviceTypes) {

        private var paramServices: Array<Service?>? = null
        private var decorates: Service? = null

        protected abstract fun getParameterTypes(): Array<Type>
        protected abstract fun getFactoryDisplayName(): String

    override fun bind() {
        val parameterTypes = getParameterTypes()
        if (parameterTypes.isEmpty()) {
            paramServices = NO_DEPENDENTS
            return
        }
            val parameterServices = arrayOfNulls<Service>(parameterTypes.size)
            paramServices = parameterServices
            for (i in parameterTypes.indices) {
                val paramType = parameterTypes[i]
                if (isEqualToAnyType(paramType, serviceTypes)) {
                    val parentServices = owner.parentServices
                    if (parentServices == null) {
                        throw ServiceCreationException(
                            String.format(
                                "Cannot create service of %s using %s as not parent registry is available to look up required service of type %s for parameter #%s.",
                                format("type", serviceTypes),
                                getFactoryDisplayName(),
                                format(paramType),
                                i + 1
                            )
                        )
                    }
                    val paramProvider = findByType(paramType, accessToken, parentServices)
                    if (paramProvider == null) {
                        throw ServiceCreationException(
                            String.format(
                                "Cannot create service of %s using %s as required service of type %s for parameter #%s is not available in parent registries.",
                                format("type", serviceTypes),
                                getFactoryDisplayName(),
                                format(paramType),
                                i + 1
                            )
                        )
                    }
                    parameterServices[i] = paramProvider
                    decorates = paramProvider
                } else {
                    val paramProvider: Service?
                    try {
                        paramProvider = findByType(paramType, accessToken, owner.allServices)
                    } catch (e: ServiceLookupException) {
                        throw ServiceCreationException(
                            String.format(
                                "Cannot create service of %s using %s as there is a problem with parameter #%s of type %s.",
                                format("type", serviceTypes),
                                getFactoryDisplayName(),
                                i + 1,
                                format(paramType)
                            ),
                            e
                        )
                    }
                    if (paramProvider == null) {
                        throw ServiceCreationException(
                            String.format(
                                "Cannot create service of %s using %s as required service of type %s for parameter #%s is not available.",
                                format("type", serviceTypes),
                                getFactoryDisplayName(),
                                format(paramType),
                                i + 1
                            )
                        )
                    }
                    parameterServices[i] = paramProvider
                    paramProvider.requiredBy(this)
                }
            }
        }

        override fun createServiceInstance(): Any {
            val result = invokeMethod(assembleParameters())
            paramServices = null
            return result
        }

        private fun assembleParameters(): Array<Any?> {
            val services = paramServices
                ?: throw IllegalStateException(String.format("Factory %s is not bound or the instance has been created already", getFactoryDisplayName()))
            if (services === NO_DEPENDENTS) {
                return NO_PARAMS
            }
            return Array<Any?>(services.size) { i -> services[i]?.get() }
        }

        override fun getAll(serviceType: Class<*>, token: ServiceAccessToken?, visitor: ServiceProvider.Visitor): ServiceProvider.Visitor {
            super.getAll(serviceType, token, visitor)
            if (decorates == null) {
                return visitor
            }
            return object : ServiceProvider.Visitor {
                override fun visit(service: Service) {
                    if (service != decorates) {
                        visitor.visit(service)
                    }
                }
            }
        }

        protected abstract fun invokeMethod(params: Array<Any?>): Any
    }

    private inner class FactoryMethodService(
        owner: DefaultServiceRegistry,
        accessScope: ServiceAccessScope,
        token: ServiceAccessToken,
        target: Any,
        method: ServiceMethod
    ) : FactoryService(owner, accessScope, token, serviceTypesOf(method)) {

        private var method: ServiceMethod? = method
        private var target: Any? = target

        init {
            validateImplementationForServiceTypes(serviceTypes, method.serviceType)
        }

        override fun getDisplayName(): String {
            return if (method == null) {
                super.getDisplayName()
            } else {
                format("Service", serviceTypes) + " via " + format(method!!.owner) + "." + method!!.name + "()"
            }
        }

        override fun getParameterTypes(): Array<Type> {
            return getMethod().parameterTypes
        }

        private fun getMethod(): ServiceMethod {
            val method = this.method
            if (method == null) {
                throw IllegalStateException("Method is no longer available for the instance of " + format("service", serviceTypes))
            }
            return method
        }

        override fun getFactoryDisplayName(): String {
            val method = getMethod()
            return String.format("method %s.%s()", format(method.owner), method.name)
        }

        override fun invokeMethod(params: Array<Any?>): Any {
            val target = this.target ?: throw IllegalStateException("The target of the factory method has been discarded after the first service creation attempt")

            val method = getMethod()
            val result = try {
                method.invoke(target, *params)
            } catch (e: Exception) {
                throw ServiceCreationException(
                    String.format(
                        "Could not create service of %s using %s.%s().",
                        format("type", serviceTypes),
                        method.owner.getSimpleName(),
                        method.name
                    ),
                    e
                )
            }
            if (result == null) {
                throw ServiceCreationException(
                    String.format(
                        "Could not create service of %s using %s.%s() as this method returned null.",
                        format("type", serviceTypes),
                        method.owner.getSimpleName(),
                        method.name
                    )
                )
            }
            return result
        }

        override fun createServiceInstance(): Any {
            val result = super.createServiceInstance()
            this.target = null
            this.method = null
            return result
        }
    }

    private inner class FixedInstanceService(owner: DefaultServiceRegistry, accessScope: ServiceAccessScope, serviceType: Class<*>, serviceInstance: Any) : SingletonService(owner, accessScope, listOf(serviceType)) {
        init {
            setInstance(serviceInstance)
        }

        private fun getDisplayNameImpl(serviceInstance: Any): String {
            return format("Service", serviceTypes) + " with implementation " + format(serviceInstance.javaClass)
        }

        override fun instanceRealized(instance: Any) {
            owner.ownServices.instanceRealized(getDeclaredServiceTypes(), Supplier { getDisplayNameImpl(instance) }, instance)
        }

        override fun getDisplayName(): String {
            return getDisplayNameImpl(getInstance())
        }

        override fun createServiceInstance(): Any {
            throw UnsupportedOperationException()
        }
    }

    private inner class ConstructorService : FactoryService {
        private var constructor: Constructor<*>? = null

        constructor(owner: DefaultServiceRegistry, accessScope: ServiceAccessScope, token: ServiceAccessToken, serviceType: Class<*>)
            : this(owner, accessScope, token, serviceType, serviceType)

        constructor(owner: DefaultServiceRegistry, accessScope: ServiceAccessScope, token: ServiceAccessToken, serviceType: Class<*>, implementationType: Class<*>)
            : this(owner, accessScope, token, listOf(serviceType), implementationType)

        constructor(owner: DefaultServiceRegistry, accessScope: ServiceAccessScope, token: ServiceAccessToken, serviceTypes: List<out Type>, implementationType: Class<*>) : super(owner, accessScope, token, serviceTypes) {
            if (implementationType.isInterface) {
                throw ServiceValidationException(String.format("Cannot register an interface (%s) for construction.", implementationType.getCanonicalName()))
            }
            if (Modifier.isAbstract(implementationType.getModifiers())) {
                throw ServiceValidationException(String.format("Cannot register an abstract type (%s) for construction.", implementationType.getCanonicalName()))
            }

            validateImplementationForServiceTypes(serviceTypes, implementationType)
            val match = InjectUtil.selectConstructor(implementationType)
            if (InjectUtil.isPackagePrivate(match.getModifiers()) || Modifier.isPrivate(match.getModifiers())) {
                match.setAccessible(true)
            }
            constructor = match
        }

        override fun getParameterTypes(): Array<Type> {
            return getConstructor().getGenericParameterTypes()
        }

        override fun createServiceInstance(): Any {
            val result = super.createServiceInstance()
            constructor = null
            return result
        }

        override fun getDisplayName(): String {
            return if (constructor == null) {
                super.getDisplayName()
            } else {
                format("Service", serviceTypes) + " via " + format(getConstructor().getDeclaringClass()) + " constructor"
            }
        }

        override fun getFactoryDisplayName(): String {
            return String.format("%s constructor", format(getConstructor().getDeclaringClass()))
        }

        override fun invokeMethod(params: Array<Any?>): Any {
            return try {
                getConstructor().newInstance(*params)
            } catch (e: InvocationTargetException) {
                val cause = e.cause
                throw ServiceCreationException(String.format("Could not create service of %s.", format("type", serviceTypes)), cause ?: e)
            } catch (e: Exception) {
                throw ServiceCreationException(String.format("Could not create service of %s.", format("type", serviceTypes)), e)
            }
        }

        private fun getConstructor(): Constructor<*> {
            val constructor = this.constructor
            if (constructor == null) {
                throw IllegalStateException("Constructor is no longer available for the instance of " + format("service", serviceTypes))
            }
            return constructor
        }
    }

    private fun validateImplementationForServiceTypes(serviceTypes: List<out Type>, implementationType: Type) {
        val implementationClass = unwrap(implementationType)
        for (serviceType in serviceTypes) {
            val serviceClass = unwrap(serviceType)
            if (!serviceClass.isAssignableFrom(implementationClass)) {
                throw ServiceValidationException(
                    String.format(
                        "Cannot register implementation '%s' for service '%s', because it does not implement it",
                        implementationClass.getSimpleName(),
                        serviceClass.getSimpleName()
                    )
                )
            }
        }
    }

    private class CompositeServiceProvider(vararg serviceProviders: ServiceProvider) : ServiceProvider {
        private val serviceProviders: Array<ServiceProvider?>

        init {
            this.serviceProviders = arrayOfNulls(serviceProviders.size)
            for (i in serviceProviders.indices) {
                this.serviceProviders[i] = serviceProviders[i]
            }
        }

        override fun getService(serviceType: Type, token: ServiceAccessToken?): Service? {
            for (serviceProvider in serviceProviders) {
                val candidate = serviceProvider ?: continue
                val service = candidate.getService(serviceType, token)
                if (service != null) {
                    return service
                }
            }
            return null
        }

        override fun getAll(serviceType: Class<*>, token: ServiceAccessToken?, visitor: ServiceProvider.Visitor): ServiceProvider.Visitor {
            var current = visitor
            for (serviceProvider in serviceProviders) {
                val provider = serviceProvider ?: continue
                current = provider.getAll(serviceType, token, current)
            }
            return current
        }

    override fun stop() {
        try {
            CompositeStoppable.stoppable(serviceProviders.toList()).stop()
        } finally {
            for (i in serviceProviders.indices) {
                serviceProviders[i] = null
            }
        }
    }

        override fun toString(): String {
            return Arrays.toString(serviceProviders)
        }
    }

    /**
     * Wraps a parent to ignore stop requests.
     */
    private class ParentServices(private val parent: ServiceProvider) : ServiceProvider {
        override fun getService(serviceType: Type, token: ServiceAccessToken?): Service? {
            return parent.getService(serviceType, token)
        }

        override fun getAll(serviceType: Class<*>, token: ServiceAccessToken?, visitor: ServiceProvider.Visitor): ServiceProvider.Visitor {
            return parent.getAll(serviceType, token, visitor)
        }

        override fun stop() {
        }

        override fun toString(): String {
            return parent.toString()
        }
    }

    private fun findByType(serviceType: Type, token: ServiceAccessToken?, serviceProvider: ServiceProvider): Service? {
        if (serviceType is ParameterizedType) {
            val parameterizedType = serviceType
            val rawType = parameterizedType.rawType
            if (rawType is Class<*>) {
                if (rawType.isAssignableFrom(List::class.java)) {
                    val typeArg = parameterizedType.getActualTypeArguments()[0]
                    return getCollectionService(typeArg, token, serviceProvider)
                }
                assertValidServiceType(rawType)
                return serviceProvider.getService(serviceType, token)
            }
        }
        if (serviceType is Class<*>) {
            assertValidServiceType(serviceType)
            return serviceProvider.getService(serviceType, token)
        }

        throw ServiceValidationException(String.format("Locating services with type %s is not supported.", format(serviceType)))
    }

    private fun getCollectionService(elementType: Type, token: ServiceAccessToken?, serviceProvider: ServiceProvider): Service {
        if (elementType is Class<*>) {
            return getCollectionService(elementType, token, serviceProvider)
        }
        if (elementType is WildcardType) {
            val wildcardType = elementType
            if (wildcardType.getUpperBounds()[0] is Class<*> && wildcardType.getLowerBounds().size == 0) {
                return getCollectionService(wildcardType.getUpperBounds()[0] as Class<*>, token, serviceProvider)
            }
        }
        throw ServiceValidationException(String.format("Locating services with type %s is not supported.", format(elementType)))
    }

    private fun getCollectionService(elementType: Class<*>, token: ServiceAccessToken?, serviceProvider: ServiceProvider): Service {
        assertValidServiceType(elementType)
        val providers = ArrayList<Service>()
        serviceProvider.getAll(elementType, token, CollectingVisitor(providers))
        val services = ArrayList<Any?>(providers.size)
        for (service in providers) {
            services.add(service.get())
        }
        return CollectionService(elementType, services, providers)
    }

    private class CollectionService(private val typeArg: Type, private val services: MutableList<Any?>, private val providers: MutableList<Service>) : Service {
        override fun getDisplayName(): String {
            return "services with type $typeArg"
        }

        override fun get(): Any {
            return services
        }

        override fun requiredBy(serviceProvider: ServiceProvider?) {
            for (service in providers) {
                service.requiredBy(serviceProvider)
            }
        }
    }

    private fun isAssignableFromAnyType(targetType: Class<*>, candidateTypes: List<Class<*>>): Boolean {
        for (candidate in candidateTypes) {
            if (targetType.isAssignableFrom(candidate)) {
                return true
            }
        }
        return false
    }

    private fun isEqualToAnyType(targetType: Type, candidateTypes: List<out Type>): Boolean {
        for (candidate in candidateTypes) {
            if (targetType == candidate) {
                return true
            }
        }
        return false
    }

    private fun isSatisfiedByAny(expected: Type, candidates: List<out Type>): Boolean {
        for (candidate in candidates) {
            if (isSatisfiedBy(expected, candidate)) {
                return true
            }
        }
        return false
    }

    private fun isSatisfiedBy(expected: Type, actual: Type): Boolean {
        if (expected == actual) {
            return true
        }
        if (expected is Class<*>) {
            return isSatisfiedBy(expected, actual)
        }
        if (expected is ParameterizedType) {
            return isSatisfiedBy(expected, actual)
        }
        return false
    }

    private fun isSatisfiedBy(expectedClass: Class<*>, actual: Type): Boolean {
        return when (actual) {
            is ParameterizedType -> {
                val raw = actual.rawType
                raw is Class<*> && expectedClass.isAssignableFrom(raw)
            }
            is Class<*> -> expectedClass.isAssignableFrom(actual)
            else -> false
        }
    }

    private fun isSatisfiedBy(expectedParameterizedType: ParameterizedType, actual: Type): Boolean {
        val expectedRawType = expectedParameterizedType.rawType
        if (!isSatisfiedBy(expectedRawType, actual)) {
            return false
        }
        val expectedTypeArguments = expectedParameterizedType.actualTypeArguments
        if (actual is ParameterizedType) {
            val actualTypeArguments = actual.actualTypeArguments
            for (i in actualTypeArguments.indices) {
                val type = actualTypeArguments[i]
                if (!isSatisfiedBy(expectedTypeArguments[i], type)) {
                    return false
                }
            }
            return true
        }
        return false
    }

    private fun assertValidServiceType(serviceClass: Class<*>) {
        if (serviceClass.isArray) {
            throw ServiceValidationException("Locating services with array type is not supported.")
        }
        if (serviceClass.isAnnotation) {
            throw ServiceValidationException("Locating services with annotation type is not supported.")
        }
        if (serviceClass == Object::class.java) {
            throw ServiceValidationException("Locating services with type Object is not supported.")
        }
    }

    private fun format(type: Type): String {
        return TypeStringFormatter.format(type)
    }

    private fun format(qualifier: String, types: List<out Type>): String {
        return if (types.size == 1) {
            qualifier + " " + format(types)
        } else {
            qualifier + "s " + format(types)
        }
    }

    private fun format(types: List<out Type>): String {
        return if (types.size == 1) {
            format(types[0])
        } else {
            types.joinToString(", ") { type -> format(type) }
        }
    }

    private inner class ThisAsService(private val accessScope: ServiceAccessScope) : ServiceProvider, Service {
        override fun getService(serviceType: Type, token: ServiceAccessToken?): Service? {
            if (!accessScope.contains(token)) {
                return null
            }
            return if (serviceType == ServiceRegistry::class.java) {
                this
            } else {
                null
            }
        }

        override fun getAll(serviceType: Class<*>, token: ServiceAccessToken?, visitor: ServiceProvider.Visitor): ServiceProvider.Visitor {
            if (!accessScope.contains(token)) {
                return visitor
            }
            if (serviceType == ServiceRegistry::class.java) {
                visitor.visit(this)
            }
            return visitor
        }

        override fun stop() {
        }

        override fun getDisplayName(): String {
            return "ServiceRegistry ${getDisplayName()}"
        }

        override fun get(): Any {
            return this@DefaultServiceRegistry
        }

        override fun requiredBy(serviceProvider: ServiceProvider?) {
        }
    }

    private class ClassInspector {
        private val classes = ConcurrentHashMap<Class<*>, ClassDetails>()

        /**
         * Does the given class have the given annotation somewhere in its hierarchy?
         */
        fun hasAnnotation(type: Class<*>, annotationType: Class<out Annotation?>) : Boolean {
            return getDetailsForClass(type).hasAnnotation(annotationType)
        }

        fun getHierarchy(type: Class<*>): Set<Class<*>> {
            return getDetailsForClass(type).types
        }

        private fun getDetailsForClass(type: Class<*>): ClassDetails {
            var classDetails = classes[type]
            if (classDetails == null) {
                val newDetails = ClassDetails(type)
                val old = classes.putIfAbsent(type, newDetails)
                classDetails = old ?: newDetails
            }
            return classDetails
        }

        private class ClassDetails(type: Class<*>) {
            val types: HashSet<Class<*>> = HashSet()
            private val annotations = ConcurrentHashMap<Class<out Annotation?>, Boolean>()

            init {
                collectTypes(type, types)
            }

            private fun collectTypes(type: Class<*>?, types: MutableSet<Class<*>>) {
                if (type == null || !types.add(type)) {
                    return
                }
                collectTypes(type.superclass, types)
                for (serviceInterface in type.interfaces) {
                    collectTypes(serviceInterface, types)
                }
            }

            fun hasAnnotation(annotationType: Class<out Annotation?>): Boolean {
                var present = annotations[annotationType]
                if (present == null) {
                    present = locateAnnotation(annotationType)
                    annotations.putIfAbsent(annotationType, present)
                }
                return present
            }

            private fun locateAnnotation(annotation: Class<out Annotation?>): Boolean {
                @Suppress("UNCHECKED_CAST")
                val annotationType = annotation as Class<Annotation>
                for (type in types) {
                    if (type.getAnnotation(annotationType) != null) {
                        return true
                    }
                }
                return false
            }
        }
    }

    private fun unwrap(type: Type): Class<*> {
        return if (type is Class<*>) {
            type
        } else {
            if (type is WildcardType) {
                val wildcardType = type
                if (wildcardType.upperBounds[0] is Class<*> && wildcardType.lowerBounds.isEmpty()) {
                    wildcardType.upperBounds[0] as Class<*>
                } else {
                    (type as ParameterizedType).rawType as Class<*>
                }
            } else {
                (type as ParameterizedType).rawType as Class<*>
            }
        }
    }

    /**
     * Carries a snapshot of the current set of services and lifecycle handlers so they can change together.
     *
     * Lifecycle handlers are maintained in a persistent array since there are at most 3 lifecycle handler instances
     * per registry, and they are iterated frequently (for every service registration).
     *
     * Services are maintained in a persistent list since there are many, they are frequently written and iterated very
     * rarely (once per lifecycle handler).
     */
    private class ServicesSnapshot {
        private constructor(
            services: PersistentList<SingletonService>,
            lifecycleHandlers: PersistentArray<AnnotatedServiceLifecycleHandler>,
            providersByType: PersistentMap<Class<*>, PersistentArray<ServiceProvider>>
        ) {
            this.services = services
            this.lifecycleHandlers = lifecycleHandlers
            this.providersByType = providersByType
        }

        val services: PersistentList<SingletonService>
        val lifecycleHandlers: PersistentArray<AnnotatedServiceLifecycleHandler>
        val providersByType: PersistentMap<Class<*>, PersistentArray<ServiceProvider>>

        companion object {
            fun of(providersByType: PersistentMap<Class<*>, PersistentArray<ServiceProvider>>): ServicesSnapshot {
                return ServicesSnapshot(
                    PersistentList.of(),
                    PersistentArray.of(),
                    providersByType
                )
            }
        }

        fun addService(service: SingletonService, inspector: ClassInspector): ServicesSnapshot {
            return ServicesSnapshot(
                services.plus(service),
                lifecycleHandlers,
                collectProvidersForClassHierarchyOf(service, inspector, providersByType)
            )
        }

        fun addLifecycleHandler(lifecycleHandler: AnnotatedServiceLifecycleHandler): ServicesSnapshot {
            return ServicesSnapshot(
                services,
                lifecycleHandlers.plus(lifecycleHandler),
                providersByType
            )
        }

        private fun collectProvidersForClassHierarchyOf(
            serviceProvider: SingletonService,
            inspector: ClassInspector,
            providersByType: PersistentMap<Class<*>, PersistentArray<ServiceProvider>>
        ): PersistentMap<Class<*>, PersistentArray<ServiceProvider>> {
            var byType = providersByType
            for (serviceType in serviceProvider.getDeclaredServiceTypes()) {
                for (type in inspector.getHierarchy(serviceType)) {
                    if (type == Object::class.java) {
                        continue
                    }
                    if (type == ServiceRegistry::class.java) {
                        throw IllegalArgumentException("Cannot define a service of type ServiceRegistry: $serviceProvider")
                    }
                    byType = byType.modify(type) { _, providers ->
                        if (providers == null) {
                            PersistentArray.of(serviceProvider)
                        } else {
                            if (providers.contains(serviceProvider)) {
                                providers
                            } else {
                                providers.plus(serviceProvider)
                            }
                        }
                    }
                }
            }
            return byType
        }
    }
}
