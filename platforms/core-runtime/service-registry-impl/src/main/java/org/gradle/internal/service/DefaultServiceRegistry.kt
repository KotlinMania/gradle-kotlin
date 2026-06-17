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
import java.util.Arrays
import java.util.Formatter
import java.util.Queue
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Supplier
import java.util.function.UnaryOperator
import kotlin.concurrent.Volatile

/**
 * A hierarchical [ServiceRegistry] implementation.
 *
 *
 * Service instances are closed when the registry that created them is closed using [.close].
 * If a service instance implements [java.io.Closeable] or [org.gradle.internal.concurrent.Stoppable]
 * then the appropriate [Closeable.close] or [Stoppable.stop] method is called.
 * Instances are closed in reverse dependency order.
 *
 *
 * Service registries are arranged in a hierarchy. If a service of a given type cannot be located, the registry uses its parent registry, if any, to locate the service.
 *
 *
 * Service interfaces should be annotated with [org.gradle.internal.service.scopes.ServiceScope] to indicate their intended usage.
 *
 *
 * Service interfaces can be annotated with [org.gradle.internal.service.scopes.StatefulListener] to indicate that services instances that implement the interface should
 * be registered as a listener of that type. Alternatively, service implementations can be annotated with [org.gradle.internal.service.scopes.ListenerService] to indicate that the should be
 * registered as a listener.
 */
open class DefaultServiceRegistry(displayName: String?, vararg parents: ServiceRegistry?) : AbstractServiceRegistry(), CloseableServiceRegistry {
    private enum class State {
        INIT, STARTED, CLOSED
    }

    private val inspector: ClassInspector
    private val ownServices: OwnServices
    private val allServices: ServiceProvider
    private val parentServices: ServiceProvider?
    private val displayName: String?
    private val thisAsServiceProvider: ServiceProvider

    private val state = AtomicReference<State?>(State.INIT)

    constructor() : this(null, *NO_PARENTS)

    constructor(displayName: String?) : this(displayName, *NO_PARENTS)

    constructor(vararg parents: ServiceRegistry?) : this(null, *parents)

    init {
        assertAllowedImplementation(javaClass)

        this.displayName = displayName
        this.ownServices = DefaultServiceRegistry.OwnServices()
        if (parents.size == 0) {
            this.parentServices = null
            this.allServices = ownServices
            this.inspector = ClassInspector()
        } else {
            this.parentServices = Companion.setupParentServices(parents)
            this.allServices = CompositeServiceProvider(ownServices, parentServices)
            this.inspector = if (parents[0] is DefaultServiceRegistry) (parents[0] as DefaultServiceRegistry).inspector else ClassInspector()
        }
        this.thisAsServiceProvider = allServices
    }

    override fun asServiceProvider(): ServiceProvider {
        return thisAsServiceProvider
    }

    private fun getDisplayName(): String {
        return if (displayName == null) javaClass.getSimpleName() else displayName
    }

    override fun toString(): String {
        return getDisplayName()
    }

    private fun findProviderMethods(target: ServiceRegistrationProvider, token: ServiceAccessToken?) {
        val type: Class<out ServiceRegistrationProvider?> = target.javaClass
        val methods: RelevantMethods = RelevantMethods.Companion.getMethods(type)
        for (method in methods.decorators) {
            if (parentServices == null) {
                throw ServiceLookupException(String.format("Cannot use decorator method %s.%s() when no parent registry is provided.", type.getSimpleName(), method.getName()))
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

    private fun applyConfigureMethod(token: ServiceAccessToken?, method: ServiceMethod, target: Any?) {
        val params = arrayOfNulls<Any>(method.getParameterTypes().size)
        for (i in method.getParameterTypes().indices) {
            val paramType = method.getParameterTypes()[i]
            if (paramType == ServiceRegistration::class.java) {
                params[i] = newRegistration(token)
            } else {
                val paramProvider: Service? = find(paramType, token, allServices)
                if (paramProvider == null) {
                    throw ServiceLookupException(
                        String.format(
                            "Cannot configure services using %s.%s() as required service of type %s is not available.",
                            method.getOwner().getSimpleName(),
                            method.getName(),
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
                    method.getOwner().getSimpleName(),
                    method.getName()
                ), e
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
        check(state.get() == State.INIT) { "Cannot add services to service registry " + this + " as it is no longer mutable" }
    }

    private fun newRegistration(token: ServiceAccessToken?): ServiceRegistration {
        return object : ServiceRegistration {
            override fun <T> add(serviceType: Class<T?>?, serviceInstance: T?) {
                this@DefaultServiceRegistry.add<T?>(ServiceAccess.getPublicScope(), serviceType, serviceInstance)
            }

            override fun add(serviceType: Class<*>) {
                ownServices.add(DefaultServiceRegistry.ConstructorService(this@DefaultServiceRegistry, ServiceAccess.getPublicScope(), token, serviceType))
            }

            override fun <T> add(serviceType: Class<in T?>?, implementationType: Class<T?>) {
                ownServices.add(DefaultServiceRegistry.ConstructorService(this@DefaultServiceRegistry, ServiceAccess.getPublicScope(), token, serviceType, implementationType))
            }

            override fun <T> add(serviceType1: Class<in T?>, serviceType2: Class<in T?>?, implementationType: Class<T?>) {
                ownServices.add(ConstructorService(this@DefaultServiceRegistry, ServiceAccess.getPublicScope(), token, Arrays.asList<Class<*>?>(serviceType1, serviceType2), implementationType))
            }

            override fun addProvider(provider: ServiceRegistrationProvider) {
                // The access token is intentionally not forwarded here
                this@DefaultServiceRegistry.addProvider(provider)
            }
        }
    }

    /**
     * Adds a service instance to this registry with the given public type. The given object is closed when this registry is closed.
     */
    fun <T> add(serviceType: Class<out T?>?, serviceInstance: T?): DefaultServiceRegistry {
        add<T?>(ServiceAccess.getPublicScope(), serviceType, serviceInstance)
        return this
    }

    /**
     * Adds a service instance to this registry. The given object is closed when this registry is closed.
     */
    fun add(serviceInstance: Any): DefaultServiceRegistry {
        return add<Any?>(serviceInstance.javaClass, serviceInstance)
    }

    private fun <T> add(accessScope: ServiceAccessScope, serviceType: Class<out T?>?, serviceInstance: T?) {
        assertMutable()
        ownServices.add(DefaultServiceRegistry.FixedInstanceService(this, accessScope, serviceType, serviceInstance!!))
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
        check(state.get() != State.CLOSED) { String.format("%s has been closed.", getDisplayName()) }
    }

    private fun noLongerMutable() {
        state.compareAndSet(State.INIT, State.STARTED)
    }

    val isClosed: Boolean
        get() = state.get() == State.CLOSED

    @Throws(UnknownServiceException::class, ServiceLookupException::class)
    override fun <T> get(serviceType: Class<T?>): T? {
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
    override fun get(serviceType: Type, annotatedWith: Class<out Annotation?>): Any? {
        throw UnknownServiceException(serviceType, String.format("No service of type %s annotated with @%s available in %s.", format(serviceType), annotatedWith.getSimpleName(), getDisplayName()))
    }

    @Throws(ServiceLookupException::class)
    override fun find(serviceType: Type): Any? {
        assertValidServiceType(unwrap(serviceType))
        val provider = getService(serviceType)
        return if (provider == null) null else provider.get()
    }

    private fun getService(serviceType: Type?): Service? {
        serviceRequested()
        return find(serviceType, null, allServices)
    }

    @Throws(ServiceLookupException::class)
    override fun <T> getAll(serviceType: Class<T?>): MutableList<T?> {
        assertValidServiceType(serviceType)
        val services: MutableList<T?> = ArrayList<T?>()
        serviceRequested()
        allServices.getAll(serviceType, null, InstanceUnpackingVisitor<T?>(serviceType, services))
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
        override fun visit(service: Service?) {
            if (!delegate.contains(service)) {
                delegate.add(service!!)
            }
        }
    }

    private inner class OwnServices : ServiceProvider {
        private val stoppable = CompositeStoppable.stoppable()
        private val services: AtomicReference<ServicesSnapshot>

        init {
            val thisServiceRegistry: ThisAsService = DefaultServiceRegistry.ThisAsService(ServiceAccess.getPublicScope())
            services = AtomicReference<ServicesSnapshot>(
                ServicesSnapshot.Companion.of(
                    PersistentMap.of<Class<*>?, PersistentArray<ServiceProvider?>?>(
                        ServiceRegistry::class.java,
                        PersistentArray.of<ServiceProvider?>(thisServiceRegistry)
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

            val services: MutableList<Service> = ArrayList<Service>(serviceProviders.size())
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

            val descriptions: MutableSet<String?> = TreeSet<String?>()
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

        fun getProviders(type: Class<*>): PersistentArray<ServiceProvider> {
            val providersByType:  // TODO(https://github.com/uber/NullAway/issues/681) Can't infer that AtomicReference holds non-nullable type
                    PersistentMap<Class<*>?, PersistentArray<ServiceProvider?>> = services.get().providersByType

            return providersByType.getOrDefault(type, PersistentArray.of<ServiceProvider?>())
        }

        override fun getAll(serviceType: Class<*>, token: ServiceAccessToken?, visitor: ServiceProvider.Visitor?): ServiceProvider.Visitor? {
            var visitor = visitor
            for (serviceProvider in getProviders(serviceType)) {
                visitor = serviceProvider.getAll(serviceType, token, visitor)
            }
            return visitor
        }

        override fun stop() {
            stoppable.stop()
        }

        fun add(serviceProvider: SingletonService) {
            assertMutable()
            stoppable.add(serviceProvider)
            val snapshot = services.updateAndGet(UnaryOperator { it: ServicesSnapshot -> it.addService(serviceProvider, inspector) })
            for (annotationHandler in snapshot.lifecycleHandlers) {
                notifyAnnotationHandler(annotationHandler, serviceProvider)
            }
        }

        fun instanceRealized(declaredServiceTypes: MutableList<Class<*>>, displayName: Supplier<String?>, instance: Any) {
            check(!(instance is AnnotatedServiceLifecycleHandler && !isAssignableFromAnyType(AnnotatedServiceLifecycleHandler::class.java, declaredServiceTypes))) {
                String.format(
                    "%s implements %s but is not declared as a service of this type. This service is declared as having %s.",
                    displayName.get(), AnnotatedServiceLifecycleHandler::class.java.getSimpleName(), format("type", declaredServiceTypes)
                )
            }
            if (instance is AnnotatedServiceLifecycleHandler) {
                annotationHandlerCreated(instance)
            }

            val lifecycleHandlers// TODO(https://github.com/uber/NullAway/issues/681) Can't infer that AtomicReference holds non-nullable type
                    = services.get().lifecycleHandlers

            for (lifecycleHandler in lifecycleHandlers) {
                for (annotation in lifecycleHandler.annotations!!) {
                    val implementationHasAnnotation = inspector.hasAnnotation(instance.javaClass, annotation!!)
                    val declaredWithAnnotation = anyTypeHasAnnotation(annotation, declaredServiceTypes)
                    check(!(implementationHasAnnotation && !declaredWithAnnotation)) {
                        String.format(
                            "%s is annotated with @%s but is not declared as a service with this annotation. This service is declared as having %s.",
                            displayName.get(), format(annotation), format("type", declaredServiceTypes)
                        )
                    }
                }
            }
        }

        fun annotationHandlerCreated(annotationHandler: AnnotatedServiceLifecycleHandler) {
            val snapshot = services.updateAndGet(UnaryOperator { it: ServicesSnapshot -> it.addLifecycleHandler(annotationHandler) })
            for (service in snapshot.services) {
                notifyAnnotationHandler(annotationHandler, service)
            }
        }

        fun notifyAnnotationHandler(annotationHandler: AnnotatedServiceLifecycleHandler, candidate: SingletonService) {
            if (annotationHandler.implicitAnnotation != null) {
                annotationHandler.whenRegistered(annotationHandler.implicitAnnotation, DefaultServiceRegistry.RegistrationWrapper(candidate))
            } else {
                val declaredServiceTypes = candidate.getDeclaredServiceTypes()
                for (annotation in annotationHandler.annotations!!) {
                    if (anyTypeHasAnnotation(annotation!!, declaredServiceTypes)) {
                        annotationHandler.whenRegistered(annotation, DefaultServiceRegistry.RegistrationWrapper(candidate))
                    }
                }
            }
        }

        fun anyTypeHasAnnotation(annotation: Class<out Annotation?>, types: MutableList<Class<*>>): Boolean {
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
        val declaredTypes: MutableList<Class<*>>
            get() = serviceProvider.getDeclaredServiceTypes()

        val instance: Any?
            get() {
                serviceRequested()
                return serviceProvider.preparedInstance
            }
    }

    private abstract class ManagedObjectServiceProvider protected constructor(protected val owner: DefaultServiceRegistry) : ServiceProvider, Service {
        private val dependents: Queue<ServiceProvider?> = ConcurrentLinkedQueue<ServiceProvider?>()

        @Volatile
        private var instance: Any? = null

        abstract val declaredServiceTypes: MutableList<Class<*>>?

        protected open fun instanceRealized(instance: Any) {
            owner.ownServices.instanceRealized(this.declaredServiceTypes!!, Supplier { this.getDisplayName() }, instance)
        }

        protected fun setInstance(instance: Any) {
            instanceRealized(instance)
            // Only expose the instance after we're done with initialization.
            this.instance = instance
        }

        fun getInstance(): Any? {
            var result = instance
            if (result == null) {
                synchronized(this) {
                    result = instance
                    if (result == null) {
                        setInstance(createServiceInstance().also { result = it }!!)
                    }
                }
            }
            return result
        }

        /**
         * Subclasses implement this method to create the service instance. It is never called concurrently and may not return null.
         */
        protected abstract fun createServiceInstance(): Any?

        override fun requiredBy(serviceProvider: ServiceProvider?) {
            if (fromSameRegistry(serviceProvider)) {
                dependents.add(serviceProvider)
            }
        }

        fun fromSameRegistry(serviceProvider: ServiceProvider?): Boolean {
            return serviceProvider is ManagedObjectServiceProvider && serviceProvider.owner === owner
        }

        @Synchronized
        override fun stop() {
            try {
                val theInstance = this.instance
                if (theInstance != null) {
                    CompositeStoppable.stoppable(dependents).add(theInstance).stop()
                }
            } finally {
                dependents.clear()
                instance = null
            }
        }
    }

    private abstract class SingletonService(owner: DefaultServiceRegistry, accessScope: ServiceAccessScope, serviceTypes: MutableList<out Type>) : ManagedObjectServiceProvider(owner) {
        private enum class BindState {
            UNBOUND, BINDING, BOUND
        }

        protected val accessScope: ServiceAccessScope
        protected val serviceTypes: MutableList<out Type>
        private val serviceTypesAsClasses: MutableList<Class<*>>

        var state: BindState = BindState.UNBOUND

        init {
            require(!serviceTypes.isEmpty()) { "Expected at least one declared service type" }

            this.accessScope = accessScope
            this.serviceTypes = serviceTypes
            serviceTypesAsClasses = CollectionUtils.collect(serviceTypes) { type: Type -> unwrap(type) }
        }

        override fun getDeclaredServiceTypes(): MutableList<Class<*>> {
            return serviceTypesAsClasses
        }

        override fun getDisplayName(): String {
            return format("Service", serviceTypes)
        }

        override fun toString(): String {
            return getDisplayName()
        }

        override fun get(): Any? {
            return getInstance()
        }

        val preparedInstance: Any?
            get() = prepare().get()

        fun prepare(): Service {
            if (state == BindState.BOUND) {
                return this
            }
            synchronized(this) {
                if (state == BindState.BINDING) {
                    throw ServiceValidationException("Cycle in dependencies of " + getDisplayName() + " detected")
                }
                if (state == BindState.UNBOUND) {
                    state = BindState.BINDING
                    try {
                        bind()
                        state = BindState.BOUND
                    } catch (e: RuntimeException) {
                        state = BindState.UNBOUND
                        throw e
                    }
                }
                return this
            }
        }

        /**
         * Do any preparation work and validation to ensure that [.createServiceInstance] can be called later.
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

    private abstract class FactoryService protected constructor(
        owner: DefaultServiceRegistry,
        accessScope: ServiceAccessScope,
        private val accessToken: ServiceAccessToken?,
        serviceTypes: MutableList<out Type>
    ) : SingletonService(owner, accessScope, serviceTypes) {
        private var paramServices: Array<Service>?
        private var decorates: Service? = null

        protected abstract val parameterTypes: Array<Type>

        protected abstract val factoryDisplayName: String?

        override fun bind() {
            val parameterTypes = this.parameterTypes
            if (parameterTypes.size == 0) {
                paramServices = NO_DEPENDENTS
                return
            }
            paramServices = arrayOfNulls<Service>(parameterTypes.size)
            for (i in parameterTypes.indices) {
                val paramType = parameterTypes[i]
                if (isEqualToAnyType(paramType, serviceTypes)) {
                    val parentServices = owner.parentServices
                    if (parentServices == null) {
                        throw ServiceCreationException(
                            String.format(
                                "Cannot create service of %s using %s as not parent registry is available to look up required service of type %s for parameter #%s.",
                                format("type", serviceTypes),
                                this.factoryDisplayName,
                                format(paramType),
                                i + 1
                            )
                        )
                    }
                    // A decorating factory
                    val paramProvider: Service? = find(paramType, accessToken, parentServices)
                    if (paramProvider == null) {
                        throw ServiceCreationException(
                            String.format(
                                "Cannot create service of %s using %s as required service of type %s for parameter #%s is not available in parent registries.",
                                format("type", serviceTypes),
                                this.factoryDisplayName,
                                format(paramType),
                                i + 1
                            )
                        )
                    }
                    paramServices!![i] = paramProvider
                    decorates = paramProvider
                } else {
                    val paramProvider: Service?
                    try {
                        paramProvider = find(paramType, accessToken, owner.allServices)
                    } catch (e: ServiceLookupException) {
                        throw ServiceCreationException(
                            String.format(
                                "Cannot create service of %s using %s as there is a problem with parameter #%s of type %s.",
                                format("type", serviceTypes),
                                this.factoryDisplayName,
                                i + 1,
                                format(paramType)
                            ), e
                        )
                    }
                    if (paramProvider == null) {
                        throw ServiceCreationException(
                            String.format(
                                "Cannot create service of %s using %s as required service of type %s for parameter #%s is not available.",
                                format("type", serviceTypes),
                                this.factoryDisplayName,
                                format(paramType),
                                i + 1
                            )
                        )
                    }
                    paramServices!![i] = paramProvider
                    paramProvider.requiredBy(this)
                }
            }
        }

        override fun createServiceInstance(): Any? {
            val params = assembleParameters()
            val result = invokeMethod(params)
            // Can discard the state required to create instance
            paramServices = null
            return result
        }

        fun assembleParameters(): Array<Any?> {
            checkNotNull(paramServices) { String.format("Factory %s is not bound or the instance has been created already", this.factoryDisplayName) }

            if (paramServices == NO_DEPENDENTS) {
                return NO_PARAMS
            }
            val params = arrayOfNulls<Any>(paramServices!!.size)
            for (i in paramServices.indices) {
                val paramProvider = paramServices!![i]
                params[i] = paramProvider.get()
            }
            return params
        }

        override fun getAll(serviceType: Class<*>, token: ServiceAccessToken?, visitor: ServiceProvider.Visitor): ServiceProvider.Visitor? {
            super.getAll(serviceType, token, visitor)
            if (decorates == null) {
                return visitor
            } else {
                return object : ServiceProvider.Visitor {
                    override fun visit(service: Service?) {
                        // Ignore the decorated service
                        if (service !== decorates) {
                            visitor.visit(service)
                        }
                    }
                }
            }
        }

        protected abstract fun invokeMethod(params: Array<Any?>?): Any?
    }

    private class FactoryMethodService(
        owner: DefaultServiceRegistry,
        accessScope: ServiceAccessScope,
        token: ServiceAccessToken?,
        serviceTypes: MutableList<out Type>,
        target: Any?,
        method: ServiceMethod
    ) : FactoryService(owner, accessScope, token, serviceTypes) {
        private var method: ServiceMethod?
        private var target: Any?

        constructor(owner: DefaultServiceRegistry, accessScope: ServiceAccessScope, token: ServiceAccessToken?, target: Any?, method: ServiceMethod) : this(
            owner,
            accessScope,
            token,
            serviceTypesOf(method),
            target,
            method
        )

        init {
            validateImplementationForServiceTypes(serviceTypes, method.getServiceType())
            this.target = target
            this.method = method
        }

        override fun getDisplayName(): String? {
            if (method == null) {
                return super.getDisplayName()
            }

            return format("Service", serviceTypes) + " via " + format(method!!.getOwner()) + "." + method!!.getName() + "()"
        }

        override fun getParameterTypes(): Array<Type>? {
            return getMethod().getParameterTypes()
        }

        fun getMethod(): ServiceMethod {
            val method = this.method
            checkNotNull(method) { "Method is no longer available for the instance of " + format("service", serviceTypes) }
            return method
        }

        override fun getFactoryDisplayName(): String {
            return String.format("method %s.%s()", format(getMethod().getOwner()), getMethod().getName())
        }

        override fun invokeMethod(params: Array<Any?>): Any {
            checkNotNull(target) { "The target of the factory method has been discarded after the first service creation attempt" }

            val result: Any
            val method = getMethod()
            try {
                result = method.invoke(target, *params)!!
            } catch (e: Exception) {
                throw ServiceCreationException(
                    String.format(
                        "Could not create service of %s using %s.%s().",
                        format("type", serviceTypes),
                        method.getOwner().getSimpleName(),
                        method.getName()
                    ),
                    e
                )
            }

            if (result == null) {
                throw ServiceCreationException(
                    String.format(
                        "Could not create service of %s using %s.%s() as this method returned null.",
                        format("type", serviceTypes),
                        method.getOwner().getSimpleName(),
                        method.getName()
                    )
                )
            }
            return result
        }

        override fun createServiceInstance(): Any? {
            val result = super.createServiceInstance()
            this.target = null
            this.method = null
            return result
        }

        companion object {
            private fun serviceTypesOf(method: ServiceMethod): MutableList<out Type> {
                val explicitServiceTypes: Array<Class<*>?> = method.getMethod().getAnnotation<Provides?>(Provides::class.java).value
                return if (explicitServiceTypes.size == 0) mutableListOf<Type?>(method.getServiceType()) else Arrays.asList<Class<*>?>(*explicitServiceTypes)
            }
        }
    }

    private class FixedInstanceService(owner: DefaultServiceRegistry, accessScope: ServiceAccessScope, serviceType: Class<*>?, serviceInstance: Any) :
        SingletonService(owner, accessScope, mutableListOf(serviceType)) {
        init {
            setInstance(serviceInstance)
        }

        fun getDisplayNameImpl(serviceInstance: Any): String {
            return format("Service", serviceTypes) + " with implementation " + format(serviceInstance.javaClass)
        }

        override fun instanceRealized(instance: Any) {
            owner.ownServices.instanceRealized(getDeclaredServiceTypes(), Supplier { getDisplayNameImpl(instance) }, instance)
        }

        override fun getDisplayName(): String {
            return getDisplayNameImpl(getInstance()!!)
        }

        override fun createServiceInstance(): Any? {
            throw UnsupportedOperationException()
        }
    }

    private class ConstructorService(owner: DefaultServiceRegistry, accessScope: ServiceAccessScope, token: ServiceAccessToken?, serviceTypes: MutableList<out Type>, implementationType: Class<*>) :
        FactoryService(owner, accessScope, token, serviceTypes) {
        private var constructor: Constructor<*>?

        private constructor(owner: DefaultServiceRegistry, accessScope: ServiceAccessScope, token: ServiceAccessToken?, serviceType: Class<*>?, implementationType: Class<*> = serviceType!!) : this(
            owner,
            accessScope,
            token,
            mutableListOf<Class<*>?>(serviceType),
            implementationType
        )

        init {
            if (implementationType.isInterface()) {
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
            this.constructor = match
        }

        override fun getParameterTypes(): Array<Type?> {
            return getConstructor().getGenericParameterTypes()
        }

        override fun createServiceInstance(): Any? {
            val result = super.createServiceInstance()
            this.constructor = null
            return result
        }

        override fun getDisplayName(): String? {
            if (constructor == null) {
                return super.getDisplayName()
            }

            return format("Service", serviceTypes) + " via " + format(getConstructor().getDeclaringClass()) + " constructor"
        }

        override fun getFactoryDisplayName(): String {
            return String.format("%s constructor", format(getConstructor().getDeclaringClass()))
        }

        override fun invokeMethod(params: Array<Any?>): Any {
            try {
                return getConstructor().newInstance(*params)
            } catch (e: InvocationTargetException) {
                val cause = e.cause
                throw ServiceCreationException(String.format("Could not create service of %s.", format("type", serviceTypes)), if (cause != null) cause else e)
            } catch (e: Exception) {
                throw ServiceCreationException(String.format("Could not create service of %s.", format("type", serviceTypes)), e)
            }
        }

        fun getConstructor(): Constructor<*> {
            val constructor = this.constructor
            checkNotNull(constructor) { "Constructor is no longer available for the instance of " + format("service", serviceTypes) }
            return constructor
        }
    }

    private class CompositeServiceProvider(vararg serviceProviders: ServiceProvider) : ServiceProvider {
        private val serviceProviders: Array<ServiceProvider>

        init {
            this.serviceProviders = serviceProviders
        }

        override fun getService(serviceType: Type?, token: ServiceAccessToken?): Service? {
            for (serviceProvider in serviceProviders) {
                val service = serviceProvider.getService(serviceType, token)
                if (service != null) {
                    return service
                }
            }
            return null
        }

        override fun getAll(serviceType: Class<*>?, token: ServiceAccessToken?, visitor: ServiceProvider.Visitor?): ServiceProvider.Visitor? {
            var visitor = visitor
            for (serviceProvider in serviceProviders) {
                visitor = serviceProvider.getAll(serviceType, token, visitor)
            }
            return visitor
        }

        override fun stop() {
            try {
                CompositeStoppable.stoppable(Arrays.asList<ServiceProvider?>(*serviceProviders)).stop()
            } finally {
                Arrays.fill(serviceProviders, null)
            }
        }

        override fun toString(): String {
            return serviceProviders.contentToString()
        }
    }

    /**
     * Wraps a parent to ignore stop requests.
     */
    private class ParentServices(private val parent: ServiceProvider) : ServiceProvider {
        override fun getService(serviceType: Type?, token: ServiceAccessToken?): Service? {
            return parent.getService(serviceType, token)
        }

        override fun getAll(serviceType: Class<*>?, token: ServiceAccessToken?, visitor: ServiceProvider.Visitor?): ServiceProvider.Visitor? {
            return parent.getAll(serviceType, token, visitor)
        }

        override fun stop() {
        }

        override fun toString(): String {
            return parent.toString()
        }
    }

    private class CollectionService(private val typeArg: Type?, private val services: MutableList<Any?>?, private val providers: MutableList<Service>) : Service {
        override fun getDisplayName(): String {
            return "services with type " + typeArg
        }

        override fun get(): Any? {
            return services
        }

        override fun requiredBy(serviceProvider: ServiceProvider?) {
            for (service in providers) {
                service.requiredBy(serviceProvider)
            }
        }
    }

    private inner class ThisAsService(private val accessScope: ServiceAccessScope) : ServiceProvider, Service {
        override fun getService(serviceType: Type, token: ServiceAccessToken?): Service? {
            if (!accessScope.contains(token)) {
                return null
            }
            if (serviceType == ServiceRegistry::class.java) {
                return this
            }
            return null
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
            return "ServiceRegistry " + this@DefaultServiceRegistry.getDisplayName()
        }

        override fun get(): Any {
            return this@DefaultServiceRegistry
        }

        override fun requiredBy(serviceProvider: ServiceProvider?) {
        }
    }

    private class ClassInspector {
        private val classes: ConcurrentMap<Class<*>?, ClassDetails?> = ConcurrentHashMap<Class<*>?, ClassDetails?>()

        /**
         * Does the given class have the given annotation somewhere in its hierarchy?
         */
        fun hasAnnotation(type: Class<*>, annotationType: Class<out Annotation?>): Boolean {
            return getDetailsForClass(type).hasAnnotation(annotationType)
        }

        fun getHierarchy(type: Class<*>): MutableSet<Class<*>> {
            return getDetailsForClass(type).types
        }

        fun getDetailsForClass(type: Class<*>): ClassDetails {
            var classDetails = classes.get(type)
            if (classDetails == null) {
                // Multiple thread may calculate this at the same time, which is ok. All threads should end up with the same details object
                val newDetails = ClassDetails(type)
                classDetails = classes.putIfAbsent(type, newDetails)
                if (classDetails == null) {
                    classDetails = newDetails
                }
            }
            return classDetails
        }

        private class ClassDetails(type: Class<*>?) {
            private val types: MutableSet<Class<*>> = HashSet<Class<*>>()
            private val annotations: ConcurrentMap<Class<out Annotation?>?, Boolean?> = ConcurrentHashMap<Class<out Annotation?>?, Boolean?>()

            init {
                collectTypes(type, types)
            }

            fun collectTypes(type: Class<*>?, types: MutableSet<Class<*>>) {
                if (type == null || !types.add(type)) {
                    return
                }
                collectTypes(type.getSuperclass(), types)
                for (serviceInterface in type.getInterfaces()) {
                    collectTypes(serviceInterface, types)
                }
            }

            fun hasAnnotation(annotationType: Class<out Annotation?>): Boolean {
                var present = annotations.get(annotationType)
                if (present == null) {
                    // Multiple threads may calculate this at the same time, which is ok
                    present = locateAnnotation(annotationType)
                    annotations.putIfAbsent(annotationType, present)
                }
                return present
            }

            fun locateAnnotation(annotation: Class<out Annotation?>): Boolean {
                for (type in types) {
                    if (type.getAnnotation(annotation) != null) {
                        return true
                    }
                }
                return false
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
    private class ServicesSnapshot(
        val services: PersistentList<SingletonService>,
        val lifecycleHandlers: PersistentArray<AnnotatedServiceLifecycleHandler>,
        val providersByType: PersistentMap<Class<*>?, PersistentArray<ServiceProvider?>>
    ) {
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

        companion object {
            fun of(providersByType: PersistentMap<Class<*>?, PersistentArray<ServiceProvider?>>): ServicesSnapshot {
                return DefaultServiceRegistry.ServicesSnapshot(
                    PersistentList.of<SingletonService?>(),
                    PersistentArray.of<AnnotatedServiceLifecycleHandler?>(),
                    providersByType
                )
            }

            private fun collectProvidersForClassHierarchyOf(
                serviceProvider: SingletonService,
                inspector: ClassInspector,
                providersByType: PersistentMap<Class<*>?, PersistentArray<ServiceProvider?>>
            ): PersistentMap<Class<*>?, PersistentArray<ServiceProvider?>> {
                var providersByType = providersByType
                val newProviders = PersistentArray.of<ServiceProvider?>(serviceProvider)
                for (serviceType in serviceProvider.getDeclaredServiceTypes()) {
                    for (type in inspector.getHierarchy(serviceType)) {
                        if (type == Any::class.java) {
                            continue
                        }
                        require(type != ServiceRegistry::class.java) { "Cannot define a service of type ServiceRegistry: " + serviceProvider }
                        providersByType = providersByType.modify(type, BiFunction { key: Class<*>?, providers: PersistentArray<ServiceProvider?>? ->
                            if (providers == null) {
                                return@modify newProviders
                            } else {
                                return@modify if (providers.contains(serviceProvider))
                                    providers
                                else
                                    providers.plus(serviceProvider)
                            }
                        })
                    }
                }
                return providersByType
            }
        }
    }

    companion object {
        private val NO_PARENTS = arrayOfNulls<ServiceRegistry>(0)
        private val NO_DEPENDENTS: Array<Service> = arrayOfNulls<Service>(0)
        private val NO_PARAMS = arrayOfNulls<Any>(0)

        // Simulation of a sealed class with public constructors in Java 8
        private fun assertAllowedImplementation(impl: Class<out DefaultServiceRegistry?>?) {
            require(!(impl != ScopedServiceRegistry::class.java && impl != DefaultServiceRegistry::class.java)) {
                String.format(
                    "Inheriting from %s is not allowed. Use ServiceRegistryBuilder instead.",
                    DefaultServiceRegistry::class.java.getSimpleName()
                )
            }
        }

        private fun setupParentServices(parents: Array<ServiceRegistry?>): ServiceProvider {
            val parentServices: ServiceProvider
            if (parents.size == 1) {
                parentServices = Companion.toParentServices(parents[0]!!)
            } else {
                val parentServiceProviders: Array<ServiceProvider> = arrayOfNulls<ServiceProvider>(parents.size)
                for (i in parents.indices) {
                    parentServiceProviders[i] = Companion.toParentServices(parents[i]!!)
                }
                parentServices = CompositeServiceProvider(*parentServiceProviders)
            }
            return parentServices
        }

        private fun toParentServices(serviceRegistry: ServiceRegistry): ServiceProvider {
            if (serviceRegistry is AbstractServiceRegistry) {
                return ParentServices(serviceRegistry.asServiceProvider())
            }
            throw IllegalArgumentException(
                String.format(
                    "Service registry %s cannot be used as a parent for another service registry."
                            + " Expected an instance of %s but got %s.",
                    serviceRegistry, AbstractServiceRegistry::class.java.getSimpleName(), serviceRegistry.javaClass.getSimpleName()
                )
            )
        }

        /**
         * Creates a service registry that uses the given providers.
         */
        fun create(vararg providers: ServiceRegistrationProvider): ServiceRegistry {
            val registry = DefaultServiceRegistry()
            for (provider in providers) {
                registry.addProvider(provider)
            }
            return registry
        }

        private fun determineAccessScope(method: ServiceMethod, token: ServiceAccessToken?): ServiceAccessScope {
            val privateService = method.getMethod().getAnnotation<PrivateService?>(PrivateService::class.java)
            return if (privateService != null) ServiceAccess.getPrivateScope(token) else ServiceAccess.getPublicScope()
        }

        private fun unwrap(type: Type): Class<*> {
            if (type is Class<*>) {
                return type
            } else {
                if (type is WildcardType) {
                    val wildcardType = type
                    if (wildcardType.getUpperBounds()[0] is Class<*> && wildcardType.getLowerBounds().size == 0) {
                        return wildcardType.getUpperBounds()[0] as Class<*>
                    }
                }
                val parameterizedType = type as ParameterizedType
                return parameterizedType.getRawType() as Class<*>
            }
        }

        private fun validateImplementationForServiceTypes(serviceTypes: MutableList<out Type>, implementationType: Type) {
            val implementationClass: Class<*> = unwrap(implementationType)
            for (serviceType in serviceTypes) {
                val serviceClass: Class<*> = unwrap(serviceType)
                if (!serviceClass.isAssignableFrom(implementationClass)) {
                    throw ServiceValidationException(
                        String.format(
                            "Cannot register implementation '%s' for service '%s', because it does not implement it",
                            implementationClass.getSimpleName(), serviceClass.getSimpleName()
                        )
                    )
                }
            }
        }

        private fun find(serviceType: Type?, token: ServiceAccessToken?, serviceProvider: ServiceProvider): Service? {
            if (serviceType is ParameterizedType) {
                val parameterizedType = serviceType
                val rawType = parameterizedType.getRawType()
                if (rawType is Class<*>) {
                    if (rawType.isAssignableFrom(MutableList::class.java)) {
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

        private fun getCollectionService(elementType: Type?, token: ServiceAccessToken?, serviceProvider: ServiceProvider): Service {
            if (elementType is Class<*>) {
                val elementClass = elementType
                return Companion.getCollectionService(elementClass, token, serviceProvider)
            }
            if (elementType is WildcardType) {
                val wildcardType = elementType
                if (wildcardType.getUpperBounds()[0] is Class<*> && wildcardType.getLowerBounds().size == 0) {
                    val elementClass = wildcardType.getUpperBounds()[0] as Class<*>
                    return Companion.getCollectionService(elementClass, token, serviceProvider)
                }
            }
            throw ServiceValidationException(String.format("Locating services with type %s is not supported.", format(elementType)))
        }

        private fun getCollectionService(elementClass: Class<*>, token: ServiceAccessToken?, serviceProvider: ServiceProvider): Service {
            assertValidServiceType(elementClass)
            val providers: MutableList<Service> = ArrayList<Service>()
            serviceProvider.getAll(elementClass, token, CollectingVisitor(providers))
            val services: MutableList<Any?> = ArrayList<Any?>(providers.size)
            for (service in providers) {
                services.add(service.get())
            }
            return CollectionService(elementClass, services, providers)
        }

        private fun isAssignableFromAnyType(targetType: Class<*>, candidateTypes: MutableList<Class<*>>): Boolean {
            for (candidate in candidateTypes) {
                if (targetType.isAssignableFrom(candidate)) {
                    return true
                }
            }
            return false
        }

        private fun isEqualToAnyType(targetType: Type, candidateTypes: MutableList<out Type>): Boolean {
            for (candidate in candidateTypes) {
                if (targetType == candidate) {
                    return true
                }
            }
            return false
        }

        private fun isSatisfiedByAny(expected: Type, candidates: MutableList<out Type>): Boolean {
            for (candidate in candidates) {
                if (isSatisfiedBy(expected, candidate)) {
                    return true
                }
            }
            return false
        }

        private fun isSatisfiedBy(expected: Type, actual: Type?): Boolean {
            if (expected == actual) {
                return true
            }
            if (expected is Class<*>) {
                return Companion.isSatisfiedBy(expected, actual)
            }
            if (expected is ParameterizedType) {
                return Companion.isSatisfiedBy(expected, actual)
            }
            return false
        }

        private fun isSatisfiedBy(expectedClass: Class<*>, actual: Type?): Boolean {
            if (actual is ParameterizedType) {
                val parameterizedType = actual
                if (parameterizedType.getRawType() is Class<*>) {
                    return expectedClass.isAssignableFrom(parameterizedType.getRawType() as Class<*>)
                }
            } else if (actual is Class<*>) {
                val other = actual
                return expectedClass.isAssignableFrom(other)
            }
            return false
        }

        private fun isSatisfiedBy(expectedParameterizedType: ParameterizedType, actual: Type?): Boolean {
            val expectedRawType = expectedParameterizedType.getRawType()
            if (actual is ParameterizedType) {
                val parameterizedType = actual
                if (!isSatisfiedBy(expectedRawType, parameterizedType.getRawType())) {
                    return false
                }
                val expectedTypeArguments = expectedParameterizedType.getActualTypeArguments()
                for (i in parameterizedType.getActualTypeArguments().indices) {
                    val type = parameterizedType.getActualTypeArguments()[i]
                    if (!isSatisfiedBy(expectedTypeArguments[i], type)) {
                        return false
                    }
                }
                return true
            }
            return false
        }

        private fun assertValidServiceType(serviceClass: Class<*>) {
            if (serviceClass.isArray()) {
                throw ServiceValidationException("Locating services with array type is not supported.")
            }
            if (serviceClass.isAnnotation()) {
                throw ServiceValidationException("Locating services with annotation type is not supported.")
            }
            if (serviceClass == Any::class.java) {
                throw ServiceValidationException("Locating services with type Object is not supported.")
            }
        }

        private fun format(type: Type?): String? {
            return TypeStringFormatter.format(type)
        }

        private fun format(qualifier: String?, types: MutableList<out Type>): String {
            if (types.size == 1) {
                return qualifier + " " + format(types)
            } else {
                return qualifier + "s " + format(types)
            }
        }

        private fun format(types: MutableList<out Type>): String? {
            if (types.size == 1) {
                return TypeStringFormatter.format(types.get(0))
            } else {
                return CollectionUtils.join(", ", types) { obj: TypeStringFormatter?, type: Type -> TypeStringFormatter.format(type) }
            }
        }
    }
}
