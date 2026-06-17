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
package org.gradle.internal.service

/**
 * Marker interface for reflection-based service declaration and registration.
 *
 * <h2>Declaring statically</h2>
 * You can declare services and factories by adding methods to the implementation of this interface.
 *
 *
 * The service-declaring methods are
 *
 *  *  not static and annotated with [@Provides][Provides]
 *  *  have a name that starts with `create` or `decorate`
 *  *  have zero or more parameters declaring dependencies
 *  *  have a non-void return type
 *
 *
 * For example:
 * <pre>`
 * &#64;Provides
 * protected SomeService createSomeService(OtherService otherServiceDependency) { ... }`</pre>
 *
 *
 *
 * Any other methods will not be ignored.
 *
 * <h3>Registering dynamically</h3>
 * You can register services dynamically by declaring a `configure` method with a [ServiceRegistration] parameter.
 *
 *
 * The recognized methods are:
 *
 *  *  not static and called `configure`
 *  *  have one of the parameters of type [ServiceRegistration]
 *  *  have zero or more parameters declaring dependencies
 *  *  have a void return type
 *
 *
 * For example:
 * <pre>`
 * protected void configure(ServiceRegistration registration) { ... }`</pre>
 *
 *
 *
 * The `configure` methods can be injected with additional parameters the same way as service-declaring methods.
 *
 * <h3>Dependency injection</h3>
 *
 * Both service-declaring methods and `configure` methods can have parameters
 * that describe their dependencies.
 *
 *
 * On top of the basic case of injecting dependencies, more advanced use-cases are also supported:
 * decoration, aggregation, owner registry injection.
 * <pre>`
 * &#64;Provides
 * protected MyService createMyService(
 * SomeService someService,
 * MyService myServiceFromParent,
 * List<OtherService> otherServices,
 * ServiceRegistry ownerServiceRegistry
 * ) { ... }`</pre>
 *
 *
 *
 * **Basic dependency.**
 * As long as the other service is available in the same or one of the parent registries,
 * it will be injected into the parameter. See `SomeService someService` in the example.
 *
 *
 * If the service is available in a service registry, the parent registries are not checked.
 * This can also be used to **override** services in child registries by providing a service of the same type.
 *
 *
 *
 * **Decoration.**
 * If `MyService` is available in a parent registry, then it can be decorated in child registries.
 * When the parameter has the same type as the service type (return-type),
 * the parameter is injected with an instance of the service from a parent registry.
 * See `MyService myServiceFromParent` in the example.
 *
 *
 *
 * **Aggregation.**
 * When the parameter is of type `List<T>`, it will receive with all services of type `T`
 * from the current and all parent registries.
 * If there are no services of this type, the list will be *empty*.
 * See `List<OtherService> otherServices` in the example.
 *
 *
 *
 * **Owner dependency.**
 * When the parameter is of type [ServiceRegistry], it will receive an instance of registry that owns the service.
 * See `ServiceRegistry ownerServiceRegistry` in the example.
 *
 * <h3>Service lookup order</h3>
 *
 * **Own services** of a registry are services contributed by the service providers.
 *
 *
 * **All services** of a registry are its own services and *all services* of all its parents.
 *
 *
 * The lookup order for dependencies is the following:
 *
 *  1.  Own services of the current registry
 *  1.  All services of the first parent
 *  1.  All services of the second parent
 *  1.  ...
 *
 *
 * The *decorator* declarations skip the own services, and start the lookup in the parents.
 *
 * <h3>Service visibility</h3>
 *
 * By default, all registered services are visible to all consumers, both via injection and lookup.
 *
 * <h4>Private services</h4>
 *
 * Using [PrivateService] annotation the services can be made *private* to the registration provider that declares them.
 *
 *
 * A private service is visible only within the same *registration provider*.
 * It is not visible to other registration providers in the same registry or to other registries.
 *
 *
 * The lookup for private services will fail if no other service can fulfil the lookup request.
 * The private services are also not collected as part of the *aggregated* injection.
 *
 * <h3>Service lifetime</h3>
 *
 * Services are created lazily and might not be instantiated at all during the lifetime of the owning service registry.
 *
 *
 * If a service instance was created and the service implements [Closeable][java.io.Closeable.close] or
 * [Stoppable][org.gradle.internal.concurrent.Stoppable.stop] then the appropriate
 * method is called to dispose of it when the owning service registry is [closed][CloseableServiceRegistry.close].
 */
interface ServiceRegistrationProvider
