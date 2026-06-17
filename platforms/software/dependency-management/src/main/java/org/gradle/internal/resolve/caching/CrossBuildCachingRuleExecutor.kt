/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.resolve.caching

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import org.gradle.api.Transformer
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.api.logging.Logging.getLogger
import org.gradle.cache.FileLockManager
import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.action.ConfigurableRules
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.isolation.Isolatable
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.io.Closeable
import java.io.File
import java.io.IOException

open class CrossBuildCachingRuleExecutor<KEY, DETAILS, RESULT>(
    name: String,
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
    cacheDecoratorFactory: InMemoryCacheDecoratorFactory,
    private val snapshotter: ValueSnapshotter,
    private val timeProvider: BuildCommencedTimeProvider,
    private val validator: EntryValidator<RESULT?>,
    private val keyToSnapshottable: Transformer<*, KEY?>,
    resultSerializer: Serializer<RESULT?>
) : CachingRuleExecutor<KEY?, DETAILS?, RESULT?>, Closeable {
    private val cache: PersistentCache
    private val store: IndexedCache<HashCode?, CachedEntry<RESULT?>?>

    init {
        this.cache = cacheBuilderFactory
            .createCacheBuilder(name)
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .open()
        val cacheParams: IndexedCacheParameters<HashCode?, CachedEntry<RESULT?>?> = createCacheConfiguration(name, resultSerializer, cacheDecoratorFactory)
        this.store = this.cache.createIndexedCache<HashCode?, CachedEntry<RESULT?>?>(cacheParams)
    }

    private fun createCacheConfiguration(
        name: String,
        resultSerializer: Serializer<RESULT?>,
        cacheDecoratorFactory: InMemoryCacheDecoratorFactory
    ): IndexedCacheParameters<HashCode?, CachedEntry<RESULT?>?> {
        return IndexedCacheParameters.of<HashCode?, CachedEntry<RESULT?>?>(
            name,
            HashCodeSerializer(),
            createEntrySerializer(resultSerializer)
        ).withCacheDecorator(
            cacheDecoratorFactory.decorator(2000, true)
        )
    }

    private fun createEntrySerializer(resultSerializer: Serializer<RESULT?>): Serializer<CachedEntry<RESULT?>?> {
        return CacheEntrySerializer<RESULT?>(resultSerializer)
    }

    override fun <D : DETAILS?> execute(
        key: KEY?,
        action: InstantiatingAction<DETAILS?>?,
        detailsToResult: Transformer<RESULT?, D?>,
        onCacheMiss: Transformer<D?, KEY?>,
        cacheExpirationControl: CacheExpirationControl?
    ): RESULT? {
        if (action == null) {
            return null
        }
        val rules = action.getRules()
        if (rules.isCacheable()) {
            return tryFromCache<D?>(key, action, detailsToResult, onCacheMiss, cacheExpirationControl, rules)
        } else {
            return executeRule<D?>(key, action, detailsToResult, onCacheMiss)
        }
    }

    private fun <D : DETAILS?> tryFromCache(
        key: KEY?,
        action: InstantiatingAction<DETAILS?>,
        detailsToResult: Transformer<RESULT?, D?>,
        onCacheMiss: Transformer<D?, KEY?>,
        cacheExpirationControl: CacheExpirationControl?,
        rules: ConfigurableRules<DETAILS?>
    ): RESULT? {
        var action = action
        val keyHash = computeExplicitInputsSnapshot(key, rules)
        val registrar = DefaultImplicitInputRegistrar()
        val instantiator = findInputCapturingInstantiator(action)
        if (instantiator != null) {
            action = action.withInstantiator(instantiator.capturing(registrar))
        }
        // First step is to find an entry with the explicit inputs in the cache
        val entry = store.getIfPresent(keyHash)
        if (entry != null) {
            if (LOGGER!!.isDebugEnabled()) {
                LOGGER.debug("Found result for rule {} and key {} in cache", rules, key)
            }
            if (validator.isValid(cacheExpirationControl, entry) && areImplicitInputsUpToDate(instantiator!!, key, rules, entry)) {
                // Here it means that we have validated that the entry is still up-to-date, and that means a couple of things:
                // 1. the cache policy said that the entry is still valid (for example, `--refresh-dependencies` wasn't called)
                // 2. if the rule is cacheable, we have validated that its discovered inputs are still the same
                return entry.result
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Invalidating result for rule {} and key {} in cache", rules, key)
            }
        }

        val result = executeRule<D?>(key, action, detailsToResult, onCacheMiss)
        store.put(keyHash, CrossBuildCachingRuleExecutor.CachedEntry<RESULT?>(timeProvider.getCurrentTime(), registrar.implicits, result))
        return result
    }

    /**
     * This method computes a snapshot of the explicit inputs of the rule, which consist of the rule implementation,
     * the rule key (for example, a module identifier) and the optional rule parameters.
     *
     * @param key the primary key
     * @param rules the rules to be snapshotted
     * @return a snapshot of the inputs
     */
    private fun computeExplicitInputsSnapshot(key: KEY?, rules: ConfigurableRules<DETAILS?>): HashCode {
        val toBeSnapshotted: MutableList<Any?> = ArrayList<Any?>(2 + 2 * rules.getConfigurableRules().size)
        toBeSnapshotted.add(keyToSnapshottable.transform(key))
        for (rule in rules.getConfigurableRules()) {
            val ruleClass = rule.getRuleClass()
            val ruleParams: Isolatable<Array<Any?>?>? = rule.getRuleParams()
            toBeSnapshotted.add(ruleClass)
            toBeSnapshotted.add(ruleParams)
        }

        return Hashing.hashHashable(snapshotter.snapshot(toBeSnapshotted))
    }

    private fun findInputCapturingInstantiator(action: InstantiatingAction<DETAILS?>): ImplicitInputsCapturingInstantiator? {
        val instantiator = action.getInstantiator()
        if (instantiator is ImplicitInputsCapturingInstantiator) {
            return instantiator
        }
        return null
    }

    private fun areImplicitInputsUpToDate(serviceRegistry: ImplicitInputsCapturingInstantiator, key: KEY?, rules: ConfigurableRules<DETAILS?>?, entry: CachedEntry<RESULT?>): Boolean {
        for (implicitEntry in entry.implicits.asMap().entries) {
            val serviceName: String = implicitEntry.key!!
            val provider = uncheckedCast<ImplicitInputsProvidingService<Any?, Any?, *>?>(serviceRegistry.findInputCapturingServiceByName<Any?, Any?, Any?>(serviceName))
            for (list in implicitEntry.value) {
                if (!provider!!.isUpToDate(list!!.getInput(), list.getOutput())) {
                    if (LOGGER!!.isDebugEnabled()) {
                        LOGGER.debug("Invalidating result for rule {} and key {} in cache because implicit input provided by service {} changed", rules, key, provider.javaClass)
                    }
                    return false
                }
            }
        }
        return true
    }

    private fun <D : DETAILS?> executeRule(key: KEY?, action: InstantiatingAction<DETAILS?>, detailsToResult: Transformer<RESULT?, D?>, onCacheMiss: Transformer<D?, KEY?>): RESULT? {
        val details = onCacheMiss.transform(key)
        action.execute(details)
        return detailsToResult.transform(details)
    }

    @Throws(IOException::class)
    override fun close() {
        cache.close()
    }

    class CachedEntry<RESULT> private constructor(val timestamp: Long, val implicits: Multimap<String?, ImplicitInputRecord<*, *>?>, val result: RESULT?)

    /**
     * When getting a result from the cache, we need to check whether the
     * result is still valid or not. We cannot take that decision before
     * knowing the actual type of KEY, so we need to provide this as a
     * pluggable strategy when creating the executor.
     *
     * @param <RESULT> the type of entry stored in the cache.
    </RESULT> */
    interface EntryValidator<RESULT> {
        fun isValid(policy: CacheExpirationControl?, entry: CachedEntry<RESULT?>?): Boolean
    }

    private class CacheEntrySerializer<RESULT>(private val resultSerializer: Serializer<RESULT?>) : AbstractSerializer<CachedEntry<RESULT?>?>() {
        private val anySerializer = AnySerializer()

        @Throws(Exception::class)
        override fun read(decoder: Decoder): CachedEntry<RESULT?> {
            return CrossBuildCachingRuleExecutor.CachedEntry<RESULT?>(decoder.readLong(), readImplicits(decoder), resultSerializer.read(decoder))
        }

        @Throws(Exception::class)
        fun readImplicits(decoder: Decoder): Multimap<String?, ImplicitInputRecord<*, *>?> {
            val cpt = decoder.readSmallInt()
            val result: Multimap<String?, ImplicitInputRecord<*, *>?> = HashMultimap.create<String?, ImplicitInputRecord<*, *>?>()
            for (i in 0..<cpt) {
                val impl = decoder.readString()
                val implicitInputOutputs = readImplicitList(decoder)
                result.putAll(impl, implicitInputOutputs)
            }
            return result
        }

        @Throws(Exception::class)
        fun readImplicitList(decoder: Decoder): MutableList<ImplicitInputRecord<*, *>?> {
            val cpt = decoder.readSmallInt()
            val implicits: MutableList<ImplicitInputRecord<*, *>?> = ArrayList<ImplicitInputRecord<*, *>?>(cpt)
            for (i in 0..<cpt) {
                val `in` = readAny(decoder)
                val out = readAny(decoder)
                implicits.add(object : ImplicitInputRecord<Any?, Any?> {
                    override fun getInput(): Any? {
                        return `in`
                    }

                    override fun getOutput(): Any? {
                        return out
                    }
                })
            }
            return implicits
        }

        @Throws(Exception::class)
        fun readAny(decoder: Decoder): Any? {
            return anySerializer.read(decoder)
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: CachedEntry<RESULT?>) {
            encoder.writeLong(value.timestamp)
            writeImplicits(encoder, value.implicits)
            resultSerializer.write(encoder, value.result)
        }

        @Throws(Exception::class)
        fun writeImplicits(encoder: Encoder, implicits: Multimap<String?, ImplicitInputRecord<*, *>?>) {
            encoder.writeSmallInt(implicits.size())
            for (entry in implicits.asMap().entries) {
                encoder.writeString(entry.key)
                writeImplicitList(encoder, entry.value)
            }
        }

        @Throws(Exception::class)
        fun writeImplicitList(encoder: Encoder, implicits: MutableCollection<ImplicitInputRecord<*, *>>) {
            encoder.writeSmallInt(implicits.size)
            for (implicit in implicits) {
                writeAny(encoder, implicit.getInput())
                writeAny(encoder, implicit.getOutput())
            }
        }

        @Throws(Exception::class)
        fun writeAny(encoder: Encoder, any: Any?) {
            anySerializer.write(encoder, any)
        }
    }

    private class DefaultImplicitInputRegistrar : ImplicitInputRecorder {
        val implicits: Multimap<String?, ImplicitInputRecord<*, *>?> = HashMultimap.create<String?, ImplicitInputRecord<*, *>?>()

        override fun <IN, OUT> register(serviceName: String?, input: ImplicitInputRecord<IN?, OUT?>?) {
            implicits.put(serviceName, input)
        }
    }

    private class AnySerializer : Serializer<Any?> {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Any? {
            val index = decoder.readSmallInt()
            if (index == -1) {
                return null
            }
            val clazz: Class<*>?
            if (index == -2) {
                val typeName = decoder.readString()
                clazz = Class.forName(typeName)
            } else {
                clazz = USUAL_TYPES[index]
            }

            return SERIALIZER_FACTORY.getSerializerFor(clazz).read(decoder)
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Any?) {
            if (value == null) {
                encoder.writeSmallInt(-1)
                return
            }
            val anyType: Class<*> = value.javaClass
            val serializer: Serializer<Any?> = org.gradle.internal.Cast.uncheckedCast<Serializer<Any?>?>(
                org.gradle.internal.resolve.caching.CrossBuildCachingRuleExecutor.AnySerializer.Companion.SERIALIZER_FACTORY.getSerializerFor(
                    anyType
                )
            )!!
            for (i in USUAL_TYPES.indices) {
                if (USUAL_TYPES[i] == anyType) {
                    encoder.writeSmallInt(i)
                    serializer.write(encoder, value)
                    return
                }
            }
            encoder.writeSmallInt(-2)
            encoder.writeString(anyType.getName())
            serializer.write(encoder, value)
        }

        companion object {
            private val SERIALIZER_FACTORY = BaseSerializerFactory()

            private val USUAL_TYPES: Array<Class<*>?> = arrayOf<Class<*>>(
                String::class.java,
                Boolean::class.java,
                Long::class.java,
                File::class.java,
                ByteArray::class.java,
                HashCode::class.java,
                Throwable::class.java
            )
        }
    }

    companion object {
        private val LOGGER = getLogger(CrossBuildCachingRuleExecutor::class.java)
    }
}
