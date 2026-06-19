/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.adapter

import com.google.common.base.Optional
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.time.Time.startCountdownTimer
import org.gradle.tooling.ToolingModelContract
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.internal.Exceptions
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import java.io.IOException
import java.io.ObjectInputStream
import java.io.Serializable
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.ArrayDeque
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Supplier
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Adapts some source object to some target view type.
 */
class ProtocolToModelAdapter @JvmOverloads constructor(private val targetTypeProvider: TargetTypeProvider = IDENTITY_TYPE_PROVIDER) : ObjectGraphAdapter {
    /**
     * Creates an adapter for a single object graph. Each object adapted by the returned adapter is treated as part of the same object graph, for the purposes of caching etc.
     */
    fun newGraph(): ObjectGraphAdapter {
        val graphDetails = ViewGraphDetails(targetTypeProvider)
        return object : ObjectGraphAdapter {
            override fun <T> adapt(targetType: Class<T?>, sourceObject: Any?): T? {
                return createView<T?>(targetType, sourceObject, NO_OP_MAPPER, graphDetails)
            }

            override fun <T> builder(viewType: Class<T?>): ViewBuilder<T?> {
                return DefaultViewBuilder<T?>(viewType, graphDetails)
            }
        }
    }

    /**
     * Adapts the source object to a view object.
     */
    override fun <T> adapt(targetType: Class<T?>, sourceObject: Any?): T? {
        if (sourceObject == null) {
            return null
        }
        return createView<T?>(targetType, sourceObject, NO_OP_MAPPER, ViewGraphDetails(targetTypeProvider))
    }

    /**
     * Creates a builder for views of the given type.
     */
    override fun <T> builder(viewType: Class<T?>): ViewBuilder<T?> {
        return DefaultViewBuilder<T?>(viewType)
    }

    /**
     * Unpacks the source object from a given view object.
     */
    fun unpack(viewObject: Any): Any {
        require(!(!Proxy.isProxyClass(viewObject.javaClass) || Proxy.getInvocationHandler(viewObject) !is InvocationHandlerImpl)) { "The given object is not a view object" }
        val handler = Proxy.getInvocationHandler(viewObject) as InvocationHandlerImpl
        return handler.sourceObject
    }

    private class ViewGraphDetails(val typeProvider: TargetTypeProvider) : Serializable {
        // Transient, don't serialize all the views that happen to have been visited, recreate them when visited via the deserialized view
        @Transient
        private var views = WeakIdentityHashMap<Any, MutableMap<ViewKey, WeakReference<Any>>>()

        fun putViewFor(sourceObject: Any, key: ViewKey, proxy: Any) {
            val viewsForSource = views.computeIfAbsent(
                sourceObject,
                object : WeakIdentityHashMap.AbsentValueProvider<MutableMap<ViewKey, WeakReference<Any>>?> {
                    override fun provide(): MutableMap<ViewKey, WeakReference<Any>> {
                        return HashMap<ViewKey, WeakReference<Any>>()
                    }
                })!!

            viewsForSource.put(key, WeakReference<Any>(proxy))
        }

        fun getViewFor(sourceObject: Any, key: ViewKey): Any? {
            val viewsForSource = views.get(sourceObject)

            if (viewsForSource == null) {
                return null
            }

            val viewWeakRef = viewsForSource.get(key)
            if (viewWeakRef == null) {
                return null
            }

            return viewWeakRef.get()
        }

        @Throws(IOException::class, ClassNotFoundException::class)
        fun readObject(`in`: ObjectInputStream) {
            `in`.defaultReadObject()
            views = WeakIdentityHashMap<Any, MutableMap<ViewKey, WeakReference<Any>>>()
        }
    }

    private class ViewKey(private val type: Class<*>, private val viewDecoration: ViewDecoration) : Serializable {
        override fun equals(obj: Any?): Boolean {
            if (obj !is ViewKey) {
                return false
            }
            val other = obj
            return other.type == type && other.viewDecoration == viewDecoration
        }

        override fun hashCode(): Int {
            return type.hashCode() xor viewDecoration.hashCode()
        }
    }

    private class InvocationHandlerImpl(private val targetType: Class<*>, val sourceObject: Any, private val decoration: ViewDecoration, private val graphDetails: ViewGraphDetails) :
        InvocationHandler, Serializable {
        private var proxy: Any? = null

        // Recreate the invoker when deserialized, rather than serialize all its state
        @Transient
        private var invoker: MethodInvoker? = null

        init {
            setup()
        }

        @Throws(IOException::class, ClassNotFoundException::class)
        fun readObject(`in`: ObjectInputStream) {
            `in`.defaultReadObject()
            setup()
            graphDetails.putViewFor(sourceObject, ViewKey(targetType, decoration), proxy!!)
        }

        fun setup() {
            val invokers: MutableList<MethodInvoker> = ArrayList<MethodInvoker>()
            invokers.add(REFLECTION_METHOD_INVOKER)
            decoration.collectInvokers(sourceObject, targetType, invokers)

            val mixInMethodInvoker = if (invokers.size == 1) invokers.get(0) else ChainedMethodInvoker(invokers)

            invoker = SupportedPropertyInvoker(
                SafeMethodInvoker(
                    PropertyCachingMethodInvoker(
                        AdaptingMethodInvoker(
                            decoration, graphDetails,
                            mixInMethodInvoker
                        )
                    )
                )
            )
        }

        override fun equals(o: Any?): Boolean {
            if (o === this) {
                return true
            }
            if (o == null || o.javaClass != javaClass) {
                return false
            }

            val other = o as InvocationHandlerImpl
            return sourceObject == other.sourceObject
        }

        override fun hashCode(): Int {
            return sourceObject.hashCode()
        }

        @Throws(Throwable::class)
        override fun invoke(target: Any, method: Method, params: Array<Any>): Any {
            if (EQUALS_METHOD == method) {
                val param = params[0]
                if (param == null || !Proxy.isProxyClass(param.javaClass)) {
                    return false
                }
                val other = Proxy.getInvocationHandler(param)
                return equals(other)
            } else if (HASHCODE_METHOD == method) {
                return hashCode()
            }

            val invocation = MethodInvocation(method.name, method.returnType, method.genericReturnType, method.parameterTypes, target, targetType, sourceObject, params)
            invoker!!.invoke(invocation)
            if (!invocation.found()) {
                val methodName = method.getDeclaringClass().getSimpleName() + "." + method.name + "()"
                throw Exceptions.unsupportedMethod(methodName)
            }
            return invocation.result!!
        }

        fun attachProxy(proxy: Any) {
            this.proxy = proxy
        }
    }

    private class ChainedMethodInvoker(invokers: MutableList<MethodInvoker>) : MethodInvoker {
        private val invokers: Array<MethodInvoker>

        init {
            this.invokers = invokers.toTypedArray<MethodInvoker>()
        }

        @Throws(Throwable::class)
        override fun invoke(method: MethodInvocation) {
            var i = 0
            while (!method.found() && i < invokers.size) {
                val invoker = invokers[i]
                invoker.invoke(method)
                i++
            }
        }
    }

    private class AdaptingMethodInvoker(private val decoration: ViewDecoration, private val graphDetails: ViewGraphDetails, private val next: MethodInvoker) : MethodInvoker {
        @Throws(Throwable::class)
        override fun invoke(invocation: MethodInvocation) {
            next.invoke(invocation)
            if (invocation.found() && invocation.result != null) {
                invocation.result = Companion.convert(invocation.genericReturnType, invocation.result!!, decoration, graphDetails)
            }
        }
    }

    private class MethodInvocationCache {
        private val store: MutableMap<MethodInvocationKey, Optional<Method>> = HashMap<MethodInvocationKey, Optional<Method>>()
        private val lock = ReentrantReadWriteLock()

        // For stats we don't really care about thread safety
        private var cacheMiss = 0
        private var cacheHit = 0
        private var evict = 0

        private val cleanupTimer = startCountdownTimer(MINIMAL_CLEANUP_INTERVAL)

        private class MethodInvocationKey(lookupClass: Class<*>?, private val methodName: String?, parameterTypes: Array<Class<*>>) {
            private val lookupClass: SoftReference<Class<*>>
            private val parameterTypes: SoftReference<Array<Class<*>>>
            private val hashCode: Int

            init {
                this.lookupClass = SoftReference<Class<*>>(lookupClass)
                this.parameterTypes = SoftReference<Array<Class<*>>>(parameterTypes)
                // hashcode will always be used, so we precompute it in order to make sure we
                // won't compute it multiple times during comparisons
                var result = if (lookupClass != null) lookupClass.hashCode() else 0
                result = 31 * result + (if (methodName != null) methodName.hashCode() else 0)
                result = 31 * result + parameterTypes.contentHashCode()
                this.hashCode = result
            }

            val isDirty: Boolean
                get() = lookupClass.get() == null || parameterTypes.get() == null

            override fun equals(o: Any?): Boolean {
                if (this === o) {
                    return true
                }
                if (o == null || javaClass != o.javaClass) {
                    return false
                }

                val that = o as MethodInvocationKey

                if (this.isDirty && that.isDirty) {
                    return true
                }
                if (!Companion.eq(lookupClass, that.lookupClass)) {
                    return false
                }
                if (methodName != that.methodName) {
                    return false
                }
                return Companion.eq(parameterTypes, that.parameterTypes)
            }

            override fun hashCode(): Int {
                return hashCode
            }

            companion object {
                private fun eq(aRef: SoftReference<*>, bRef: SoftReference<*>): Boolean {
                    val a: Any? = aRef.get()
                    val b: Any? = bRef.get()
                    return Companion.eq(a!!, b!!)
                }

                private fun eq(a: Any, b: Any): Boolean {
                    if (a === b) {
                        return true
                    }
                    if (a == null) {
                        return false
                    }
                    if (a.javaClass.isArray()) {
                        return (a as Array<Any?>).contentEquals(b as Array<Any?>)
                    }
                    return a == b
                }
            }
        }

        fun get(invocation: MethodInvocation): Method? {
            val owner: Class<*> = invocation.delegate.javaClass
            val name = invocation.name
            val parameterTypes = invocation.parameterTypes
            val key = MethodInvocationKey(
                owner,
                name,
                parameterTypes
            )
            lock.readLock().lock()
            try {
                var cached = store.get(key)
                if (cached == null) {
                    cacheMiss++
                    lock.readLock().unlock()
                    lock.writeLock().lock()
                    try {
                        cached = store.get(key)
                        if (cached == null) {
                            cached = lookup(owner, name, parameterTypes)
                            if (cacheMiss % 10 == 0) {
                                removeDirtyEntries()
                            }
                            store.put(key, cached)
                        }
                    } finally {
                        lock.readLock().lock()
                        lock.writeLock().unlock()
                    }
                } else {
                    cacheHit++
                }
                return cached.orNull()
            } finally {
                lock.readLock().unlock()
            }
        }

        /**
         * Removes dirty entries from the cache. Calling System.currentTimeMillis() is costly so we should try to limit calls to this method. This method will only trigger cleanup at most once per
         * 30s.
         */
        fun removeDirtyEntries() {
            if (!cleanupTimer.hasExpired()) {
                return
            }
            lock.writeLock().lock()
            try {
                for (key in LinkedList<MethodInvocationKey>(store.keys)) {
                    if (key.isDirty) {
                        evict++
                        store.remove(key)
                    }
                }
            } finally {
                cleanupTimer.reset()
                lock.writeLock().unlock()
            }
        }

        override fun toString(): String {
            return "Cache size: " + store.size + " Hits: " + cacheHit + " Miss: " + cacheMiss + " Evicted: " + evict
        }

        companion object {
            private const val MINIMAL_CLEANUP_INTERVAL: Long = 30000

            private fun lookup(sourceClass: Class<*>, methodName: String, parameterTypes: Array<Class<*>>): Optional<Method> {
                var match: Method?
                try {
                    match = sourceClass.getMethod(methodName, *parameterTypes)
                } catch (e: NoSuchMethodException) {
                    return Optional.absent<Method>()
                }

                val queue = LinkedList<Class<*>>()
                queue.add(sourceClass)
                while (!queue.isEmpty()) {
                    val c = queue.removeFirst()
                    try {
                        match = c.getMethod(methodName, *parameterTypes)
                    } catch (e: NoSuchMethodException) {
                        // ignore
                    }
                    for (interfaceType in c.getInterfaces()) {
                        queue.addFirst(interfaceType)
                    }
                    if (c.getSuperclass() != null) {
                        queue.addFirst(c.getSuperclass())
                    }
                }
                match!!.setAccessible(true)
                return Optional.of<Method>(match)
            }
        }
    }

    private class ReflectionMethodInvoker : MethodInvoker {
        private val lookupCache = MethodInvocationCache()

        @Throws(Throwable::class)
        override fun invoke(invocation: MethodInvocation) {
            val targetMethod = locateMethod(invocation)
            if (targetMethod == null) {
                return
            }

            val returnValue: Any?
            try {
                returnValue = targetMethod.invoke(invocation.delegate, *invocation.parameters)
            } catch (e: InvocationTargetException) {
                throw e.cause!!
            }

            invocation.result = returnValue
        }

        fun locateMethod(invocation: MethodInvocation): Method? {
            return lookupCache.get(invocation)
        }
    }

    private class PropertyCachingMethodInvoker(private val next: MethodInvoker) : MethodInvoker {
        private var properties = mutableMapOf<String, Any>()
        private var unknown = mutableSetOf<String>()

        @Throws(Throwable::class)
        override fun invoke(method: MethodInvocation) {
            if (method.isGetter) {
                if (properties.containsKey(method.name)) {
                    method.result = properties.get(method.name)
                    return
                }
                if (unknown.contains(method.name)) {
                    return
                }

                val value: Any?
                next.invoke(method)
                if (!method.found()) {
                    markUnknown(method.name)
                    return
                }
                value = method.result
                cachePropertyValue(method.name, value!!)
                return
            }

            next.invoke(method)
        }

        fun markUnknown(methodName: String) {
            if (unknown.isEmpty()) {
                unknown = HashSet<String>()
            }
            unknown.add(methodName)
        }

        fun cachePropertyValue(methodName: String, value: Any) {
            if (properties.isEmpty()) {
                properties = HashMap<String, Any>()
            }
            properties.put(methodName, value)
        }
    }

    private class SafeMethodInvoker(private val next: MethodInvoker) : MethodInvoker {
        @Throws(Throwable::class)
        override fun invoke(invocation: MethodInvocation) {
            next.invoke(invocation)
            if (invocation.found() || invocation.parameterTypes.size != 1 || !invocation.isIsOrGet) {
                return
            }

            val getterInvocation = MethodInvocation(
                invocation.name,
                invocation.returnType,
                invocation.genericReturnType,
                EMPTY_CLASS_ARRAY,
                invocation.view,
                invocation.viewType,
                invocation.delegate,
                EMPTY
            )
            next.invoke(getterInvocation)
            if (getterInvocation.found() && getterInvocation.result != null) {
                invocation.result = getterInvocation.result
            } else {
                invocation.result = invocation.parameters[0]
            }
        }
    }

    private class SupportedPropertyInvoker(private val next: MethodInvoker) : MethodInvoker {
        @Throws(Throwable::class)
        override fun invoke(invocation: MethodInvocation) {
            next.invoke(invocation)
            if (invocation.found()) {
                return
            }

            val methodName = invocation.name
            val isSupportMethod = methodName.length > 11 && methodName.startsWith("is") && methodName.endsWith("Supported")
            if (!isSupportMethod) {
                return
            }

            val getterName = "get" + methodName.substring(2, methodName.length - 9)
            val getterInvocation = MethodInvocation(
                getterName,
                invocation.returnType,
                invocation.genericReturnType,
                EMPTY_CLASS_ARRAY,
                invocation.view,
                invocation.viewType,
                invocation.delegate,
                EMPTY
            )
            next.invoke(getterInvocation)
            invocation.result = getterInvocation.found()
        }
    }

    private class BeanMixInMethodInvoker(private val instance: Any, private val next: MethodInvoker) : MethodInvoker {
        @Throws(Throwable::class)
        override fun invoke(invocation: MethodInvocation) {
            var beanInvocation = MethodInvocation(
                invocation.name,
                invocation.returnType,
                invocation.genericReturnType,
                invocation.parameterTypes,
                invocation.view,
                invocation.viewType,
                instance,
                invocation.parameters
            )
            next.invoke(beanInvocation)
            if (beanInvocation.found()) {
                invocation.result = beanInvocation.result
                return
            }
            if (!invocation.isGetter) {
                return
            }

            beanInvocation = MethodInvocation(
                invocation.name,
                invocation.returnType,
                invocation.genericReturnType,
                arrayOf<Class<*>>(invocation.viewType),
                invocation.view,
                invocation.viewType,
                instance,
                arrayOf<Any>(invocation.view)
            )
            next.invoke(beanInvocation)
            if (beanInvocation.found()) {
                invocation.result = beanInvocation.result
            }
        }
    }

    private class ClassMixInMethodInvoker(private val mixInClass: Class<*>, private val next: MethodInvoker) : MethodInvoker {
        private var instance: Any? = null

        //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
        private val current = ThreadLocal<MethodInvocation>()

        @Throws(Throwable::class)
        override fun invoke(invocation: MethodInvocation) {
            if (current.get() != null) {
                // Already invoking a method on the mix-in
                return
            }

            if (instance == null) {
                instance = DirectInstantiator.INSTANCE.newInstance(mixInClass, invocation.view)
            }
            val beanInvocation = MethodInvocation(
                invocation.name,
                invocation.returnType,
                invocation.genericReturnType,
                invocation.parameterTypes,
                invocation.view,
                invocation.viewType,
                instance!!,
                invocation.parameters
            )
            current.set(beanInvocation)
            try {
                next.invoke(beanInvocation)
            } finally {
                current.set(null)
            }
            if (beanInvocation.found()) {
                invocation.result = beanInvocation.result
            }
        }
    }

    private interface ViewDecoration {
        fun collectInvokers(sourceObject: Any, viewType: Class<*>, invokers: MutableList<MethodInvoker>)

        val isNoOp: Boolean

        /**
         * Filter this decoration to apply only to the given view types. Return [.NO_OP_MAPPER] if this decoration does not apply to any of the types.
         */
        fun restrictTo(viewTypes: MutableSet<Class<*>>): ViewDecoration
    }

    private class NoOpDecoration : ViewDecoration, Serializable {
        override fun collectInvokers(sourceObject: Any, viewType: Class<*>, invokers: MutableList<MethodInvoker>) {
        }

        override fun equals(obj: Any?): Boolean {
            return obj is NoOpDecoration
        }

        override fun hashCode(): Int {
            return 0
        }

        override val isNoOp: Boolean
            get() = true

        override fun restrictTo(viewTypes: MutableSet<Class<*>>): ViewDecoration {
            return this
        }
    }

    private class MixInMappingAction(decorations: MutableList<out ViewDecoration>) : ViewDecoration, Serializable {
        private val decorations: MutableList<out ViewDecoration>

        init {
            assert(decorations.size >= 2)
            this.decorations = decorations
        }

        override fun hashCode(): Int {
            var v = 0
            for (decoration in decorations) {
                v = v xor decoration.hashCode()
            }
            return v
        }

        override fun equals(obj: Any?): Boolean {
            if (obj?.javaClass != MixInMappingAction::class.java) {
                return false
            }
            val other = obj as MixInMappingAction
            return decorations == other.decorations
        }

        override val isNoOp: Boolean
            get() {
                for (decoration in decorations) {
                    if (!decoration.isNoOp) {
                        return false
                    }
                }
                return true
            }

        override fun restrictTo(viewTypes: MutableSet<Class<*>>): ViewDecoration {
            val filtered: MutableList<ViewDecoration> = ArrayList<ViewDecoration>()
            for (viewDecoration in decorations) {
                val filteredDecoration = viewDecoration.restrictTo(viewTypes)
                if (!filteredDecoration.isNoOp) {
                    filtered.add(filteredDecoration)
                }
            }
            if (filtered.size == 0) {
                return NO_OP_MAPPER
            }
            if (filtered.size == 1) {
                return filtered.get(0)
            }
            if (filtered == decorations) {
                return this
            }
            return MixInMappingAction(filtered)
        }

        override fun collectInvokers(sourceObject: Any, viewType: Class<*>, invokers: MutableList<MethodInvoker>) {
            for (decoration in decorations) {
                decoration.collectInvokers(sourceObject, viewType, invokers)
            }
        }

        companion object {
            fun chain(decorations: MutableList<out ViewDecoration>): ViewDecoration {
                if (decorations.isEmpty()) {
                    return NO_OP_MAPPER
                }
                if (decorations.size == 1) {
                    return decorations.get(0)
                }
                return MixInMappingAction(decorations)
            }
        }
    }

    private abstract class TypeSpecificMappingAction(protected val targetType: Class<*>) : ViewDecoration, Serializable {
        override val isNoOp: Boolean
            get() = false

        override fun restrictTo(viewTypes: MutableSet<Class<*>>): ViewDecoration {
            if (viewTypes.contains(targetType)) {
                return this
            }
            return NO_OP_MAPPER
        }

        override fun collectInvokers(sourceObject: Any, viewType: Class<*>, invokers: MutableList<MethodInvoker>) {
            if (targetType.isAssignableFrom(viewType)) {
                invokers.add(createInvoker()!!)
            }
        }

        protected abstract fun createInvoker(): MethodInvoker?
    }

    private class MixInBeanMappingAction(targetType: Class<*>, private val mixIn: Any) : TypeSpecificMappingAction(targetType) {
        override fun hashCode(): Int {
            return targetType.hashCode() xor mixIn.hashCode()
        }

        override fun equals(obj: Any?): Boolean {
            if (obj?.javaClass != MixInBeanMappingAction::class.java) {
                return false
            }
            val other = obj as MixInBeanMappingAction
            return targetType == other.targetType && mixIn == other.mixIn
        }

        override fun createInvoker(): MethodInvoker {
            return BeanMixInMethodInvoker(mixIn, REFLECTION_METHOD_INVOKER)
        }
    }

    private class MixInTypeMappingAction(targetType: Class<*>, private val mixInType: Class<*>) : TypeSpecificMappingAction(targetType) {
        override fun hashCode(): Int {
            return targetType.hashCode() xor mixInType.hashCode()
        }

        override fun equals(obj: Any?): Boolean {
            if (obj?.javaClass != MixInTypeMappingAction::class.java) {
                return false
            }
            val other = obj as MixInTypeMappingAction
            return targetType == other.targetType && mixInType == other.mixInType
        }

        override fun createInvoker(): MethodInvoker {
            return ClassMixInMethodInvoker(mixInType, REFLECTION_METHOD_INVOKER)
        }
    }

    private inner class DefaultViewBuilder<T> : ViewBuilder<T?> {
        private val viewType: Class<T?>
        private val graphDetails: ViewGraphDetails?
        var viewDecorations: MutableList<ViewDecoration> = ArrayList<ViewDecoration>()

        internal constructor(viewType: Class<T?>) {
            this.viewType = viewType
            this.graphDetails = null
        }

        internal constructor(viewType: Class<T?>, graphDetails: ViewGraphDetails?) {
            this.viewType = viewType
            this.graphDetails = graphDetails
        }

        override fun mixInTo(targetType: Class<*>, mixIn: Any): ViewBuilder<T?> {
            viewDecorations.add(MixInBeanMappingAction(targetType, mixIn))
            return this
        }

        override fun mixInTo(targetType: Class<*>, mixInType: Class<*>): ViewBuilder<T?> {
            viewDecorations.add(MixInTypeMappingAction(targetType, mixInType))
            return this
        }

        override fun build(sourceObject: Any?): T? {
            if (sourceObject == null) {
                return null
            }

            val viewDecoration: ViewDecoration = MixInMappingAction.Companion.chain(viewDecorations)
            return createView<T?>(viewType, sourceObject, viewDecoration, if (graphDetails != null) graphDetails else ViewGraphDetails(targetTypeProvider))
        }
    }

    companion object {
        private val NO_OP_MAPPER: ViewDecoration = NoOpDecoration()
        private val IDENTITY_TYPE_PROVIDER: TargetTypeProvider = object : TargetTypeProvider {
            override fun <T> getTargetType(initialTargetType: Class<T?>, protocolObject: Any): Class<out T> {
                return initialTargetType as Class<out T>
            }
        }

        private val UPPER_LOWER_PATTERN: Pattern = Pattern.compile("(?m)([A-Z]*)([a-z0-9]*)")
        private val REFLECTION_METHOD_INVOKER = ReflectionMethodInvoker()
        private val TYPE_INSPECTOR = TypeInspector()
        private val COLLECTION_MAPPER = CollectionMapper()
        private val EMPTY: Array<Any> = emptyArray<Any>()
        private val EMPTY_CLASS_ARRAY: Array<Class<*>> = emptyArray<Class<*>>()
        private val EQUALS_METHOD: Method
        private val HASHCODE_METHOD: Method

        init {
            val equalsMethod: Method
            val hashCodeMethod: Method
            try {
                equalsMethod = Any::class.java.getMethod("equals", Any::class.java)
                hashCodeMethod = Any::class.java.getMethod("hashCode")
            } catch (e: NoSuchMethodException) {
                throw throwAsUncheckedException(e)
            }
            EQUALS_METHOD = equalsMethod
            HASHCODE_METHOD = hashCodeMethod
        }

        private fun <T> createView(targetType: Class<T?>, sourceObject: Any?, decoration: ViewDecoration, graphDetails: ViewGraphDetails): T? {
            if (sourceObject == null) {
                return null
            }
            if (sourceObject is Supplier<*>) {
                return createView<T?>(targetType, sourceObject.get(), decoration, graphDetails)
            }

            // Calculate the actual type
            val viewType = graphDetails.typeProvider.getTargetType<T>(targetType, sourceObject)

            if (viewType.isInstance(sourceObject)) {
                return viewType.cast(sourceObject)
            }
            if (targetType.isEnum()) {
                return adaptToEnum<T?, Any?>(targetType, sourceObject)
            }

            // Restrict the decorations to those required to decorate all views reachable from this type
            val decorationsForThisType = if (decoration.isNoOp) decoration else decoration.restrictTo(TYPE_INSPECTOR.getReachableTypes(targetType))

            val viewKey = ViewKey(viewType, decorationsForThisType)
            val view = graphDetails.getViewFor(sourceObject, viewKey)
            if (view != null) {
                return targetType.cast(view)
            }

            // Create a proxy
            val handler = InvocationHandlerImpl(targetType, sourceObject, decorationsForThisType, graphDetails)
            val modelContractInterfaces: Array<Class<*>> = getModelContractInterfaces<T?>(targetType, sourceObject, viewType)
            val proxy = Proxy.newProxyInstance(viewType.getClassLoader(), modelContractInterfaces, handler)
            handler.attachProxy(proxy)

            graphDetails.putViewFor(sourceObject, viewKey, proxy)

            return viewType.cast(proxy)
        }

        private fun <T> getModelContractInterfaces(targetType: Class<T?>, sourceObject: Any, viewType: Class<out T>): Array<Class<*>> {
            val potentialSubInterfaces: MutableMap<String, Class<*>> = getPotentialModelContractSubInterfaces<T?>(targetType)
            val actualSubInterfaces: MutableSet<Class<*>> = getActualImplementedModelContractSubInterfaces(sourceObject, potentialSubInterfaces)

            val modelContractInterfaces: MutableList<Class<*>> = ArrayList<Class<*>>()
            modelContractInterfaces.add(viewType) // base interface
            modelContractInterfaces.addAll(actualSubInterfaces)
            return modelContractInterfaces.toTypedArray<Class<*>>()
        }

        private fun <T> getPotentialModelContractSubInterfaces(targetType: Class<T?>): MutableMap<String, Class<*>> {
            val result = HashMap<String, Class<*>>()
            getPotentialModelContractSubInterfaces<T?>(targetType, HashSet<Class<*>>(), result)
            return result
        }

        private fun <T> getPotentialModelContractSubInterfaces(
            targetType: Class<T?>,
            visited: MutableSet<Class<*>>,
            result: MutableMap<String, Class<*>>
        ) {
            val isNew = visited.add(targetType)
            if (isNew) {
                val annotations = targetType.getAnnotations()
                for (annotation in annotations) {
                    if (annotation is ToolingModelContract) {
                        val classes: Array<Class<*>> = annotation.subTypes.map { it.java }.toTypedArray()
                        for (clazz in classes) {
                            result.put(clazz.name, clazz)
                            Companion.getPotentialModelContractSubInterfaces(clazz as Class<Any?>, visited, result)
                        }
                    }
                }
            }
        }

        private fun getActualImplementedModelContractSubInterfaces(sourceObject: Any, potentialModelContractInterfaces: MutableMap<String, Class<*>>): MutableSet<Class<*>> {
            if (potentialModelContractInterfaces.isEmpty()) {
                return mutableSetOf<Class<*>>()
            }

            val allImplementedInterfaces: MutableSet<Class<*>> = walkTypeHierarchyAndExtractInterfaces<Any>(sourceObject.javaClass)

            // keep only those implemented interfaces which are in model contract set
            val filteredImplementedInterfaces: MutableSet<Class<*>> = HashSet<Class<*>>()
            for (i in allImplementedInterfaces) {
                val actualSubType = potentialModelContractInterfaces.get(i.name)
                if (actualSubType != null) {
                    filteredImplementedInterfaces.add(actualSubType)
                }
            }

            return filteredImplementedInterfaces
        }

        private fun <T> walkTypeHierarchyAndExtractInterfaces(clazz: Class<*>): MutableSet<Class<*>> {
            val seenInterfaces: MutableSet<Class<*>> = HashSet<Class<*>>()
            val queue: Queue<Class<*>> = ArrayDeque<Class<*>>()
            queue.add(clazz)
            var type: Class<*>?
            while ((queue.poll().also { type = it }) != null) {
                val superclass: Class<*>? = type!!.getSuperclass()
                if (superclass != null) {
                    queue.add(superclass)
                }
                for (iface in type.getInterfaces()) {
                    if (seenInterfaces.add(iface)) {
                        queue.add(uncheckedCast<Class<in T?>?>(iface))
                    }
                }
            }
            return seenInterfaces
        }

        private fun <T, S> adaptToEnum(targetType: Class<T?>, sourceObject: S?): T? {
            val literal: String
            if (sourceObject is Enum<*>) {
                literal = (sourceObject as Enum<*>).name
            } else if (sourceObject is String) {
                literal = sourceObject as String
            } else {
                literal = sourceObject.toString()
            }

            val result = Companion.toEnum(targetType as Class<out Enum<*>>, literal) as T?
            return result
        }

        // Copied from GUtils.toEnum(). We can't use that class here as it depends on Java 8 classe
        // which breaks the TAPI build actions when the target Gradle version is running on Java 6.
        fun toEnum(enumType: Class<out Enum<*>>, literal: String): Enum<*>? {
            var match = findEnumValue(enumType, literal)
            if (match != null) {
                return match
            }

            val alternativeLiteral: String? = toWords(literal, '_')
            match = Companion.findEnumValue(enumType, alternativeLiteral!!)
            if (match != null) {
                return match
            }

            var sep = ""
            val builder = StringBuilder()
            for (ec in enumType.getEnumConstants()) {
                builder.append(sep)
                builder.append(ec.name)
                sep = ", "
            }

            throw IllegalArgumentException(
                String.format(
                    "Cannot convert string value '%s' to an enum value of type '%s' (valid case insensitive values: %s)",
                    literal, enumType.name, builder.toString()
                )
            )
        }

        private fun findEnumValue(enumType: Class<out Enum<*>>, literal: String): Enum<*>? {
            for (ec in enumType.getEnumConstants()) {
                if (ec.name.equals(literal, ignoreCase = true)) {
                    return ec
                }
            }
            return null
        }

        fun toWords(string: CharSequence?, separator: Char): String? {
            if (string == null) {
                return null
            }
            val builder = StringBuilder()
            var pos = 0
            val matcher: Matcher = UPPER_LOWER_PATTERN.matcher(string)
            while (pos < string.length) {
                matcher.find(pos)
                if (matcher.end() == pos) {
                    // Not looking at a match
                    pos++
                    continue
                }
                if (builder.length > 0) {
                    builder.append(separator)
                }
                val group1 = matcher.group(1).lowercase()
                val group2 = matcher.group(2)
                if (group2.length == 0) {
                    builder.append(group1)
                } else {
                    if (group1.length > 1) {
                        builder.append(group1.substring(0, group1.length - 1))
                        builder.append(separator)
                        builder.append(group1.substring(group1.length - 1))
                    } else {
                        builder.append(group1)
                    }
                    builder.append(group2)
                }
                pos = matcher.end()
            }

            return builder.toString()
        }

        private fun convert(targetType: Type, sourceObject: Any, decoration: ViewDecoration, graphDetails: ViewGraphDetails): Any? {
            if (targetType is ParameterizedType) {
                val parameterizedTargetType = targetType
                if (parameterizedTargetType.getRawType() is Class<*>) {
                    val rawClass = parameterizedTargetType.getRawType() as Class<*>
                    if (Iterable::class.java.isAssignableFrom(rawClass)) {
                        val targetElementType: Type = getElementType(parameterizedTargetType, 0)
                        return convertCollectionInternal(rawClass, targetElementType, sourceObject as Iterable<*>, decoration, graphDetails)
                    }
                    if (MutableMap::class.java.isAssignableFrom(rawClass)) {
                        val targetKeyType: Type = getElementType(parameterizedTargetType, 0)
                        val targetValueType: Type = getElementType(parameterizedTargetType, 1)
                        return convertMap(rawClass, targetKeyType, targetValueType, sourceObject as MutableMap<*, *>, decoration, graphDetails)
                    }
                }
            }
            if (targetType is Class<*>) {
                val targetClassType = uncheckedNonnullCast<Class<Any>>(targetType)
                if (targetClassType.isPrimitive()) {
                    return sourceObject
                }
                return Companion.createView<Any>(targetClassType as Class<Any?>, sourceObject, decoration, graphDetails)
            }
            throw UnsupportedOperationException(String.format("Cannot convert object of %s to %s.", sourceObject.javaClass, targetType))
        }

        private fun convertMap(
            mapClass: Class<*>,
            targetKeyType: Type,
            targetValueType: Type,
            sourceObject: MutableMap<*, *>,
            decoration: ViewDecoration,
            graphDetails: ViewGraphDetails
        ): MutableMap<Any, Any> {
            val convertedElements: MutableMap<Any, Any> = COLLECTION_MAPPER.createEmptyMap(mapClass)
            for (entry in sourceObject.entries) {
                convertedElements.put(Companion.convert(targetKeyType, entry.key!!, decoration, graphDetails)!!, Companion.convert(targetValueType, entry.value!!, decoration, graphDetails)!!)
            }
            return convertedElements
        }

        private fun convertCollectionInternal(collectionClass: Class<*>, targetElementType: Type, sourceObject: Iterable<*>, decoration: ViewDecoration, graphDetails: ViewGraphDetails): Any {
            val convertedElements: MutableCollection<Any> = COLLECTION_MAPPER.createEmptyCollection(collectionClass)
            convertCollectionInternal(convertedElements, targetElementType, sourceObject, decoration, graphDetails)
            if (collectionClass == DomainObjectSet::class.java) {
                return ImmutableDomainObjectSet<Any>(convertedElements)
            } else {
                return convertedElements
            }
        }

        private fun convertCollectionInternal(
            targetCollection: MutableCollection<Any>,
            targetElementType: Type,
            sourceObject: Iterable<*>,
            viewDecoration: ViewDecoration,
            graphDetails: ViewGraphDetails
        ) {
            for (element in sourceObject) {
                targetCollection.add(Companion.convert(targetElementType, element!!, viewDecoration, graphDetails)!!)
            }
        }

        private fun getElementType(type: ParameterizedType, index: Int): Type {
            val elementType = type.getActualTypeArguments()[index]
            if (elementType is WildcardType) {
                val wildcardType = elementType
                return wildcardType.getUpperBounds()[0]
            }
            return elementType
        }
    }
}
