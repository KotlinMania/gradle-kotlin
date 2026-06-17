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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAnyOf
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude
import org.gradle.internal.collect.PersistentSet
import org.jspecify.annotations.NullMarked
import java.util.function.Function
import java.util.function.Predicate

@NullMarked
internal class Intersections(private val factory: ExcludeFactory) {
    private val intersections: MutableList<Intersection<out ExcludeSpec, out ExcludeSpec>> = ArrayList<Intersection<out ExcludeSpec, out ExcludeSpec>>()

    init {
        // For the Any intersections, be sure to add the more specific type first, so it gets used if applicable
        intersections.add(Intersections.IntersectAnyWithAny())
        intersections.add(Intersections.IntersectAnyWithBaseSpec())

        intersections.add(IntersectGroupWithGroup())
        intersections.add(IntersectGroupWithModuleId())
        intersections.add(IntersectGroupWithGroupSet())
        intersections.add(IntersectGroupWithModuleIdSet())
        intersections.add(IntersectGroupWithModule())
        intersections.add(IntersectGroupWithModuleSet())

        intersections.add(IntersectGroupSetWithGroupSet())
        intersections.add(IntersectGroupSetWithModuleId())
        intersections.add(IntersectGroupSetWithModuleIdSet())

        intersections.add(IntersectModuleWithModule())
        intersections.add(IntersectModuleWithModuleId())
        intersections.add(IntersectModuleWithModuleSet())
        intersections.add(IntersectModuleWithModuleIdSet())
        intersections.add(IntersectModuleWithGroupSet())

        intersections.add(IntersectModuleIdWithModuleId())
        intersections.add(IntersectModuleIdWithModuleIdSet())
        intersections.add(IntersectModuleIdWithModuleSet())

        intersections.add(IntersectModuleIdSetWithModuleIdSet())
        intersections.add(IntersectModuleIdSetWithModuleSet())

        intersections.add(IntersectModuleSetWithModuleSet())
        intersections.add(IntersectModuleSetWithGroupSet())
    }

    fun tryIntersect(left: ExcludeSpec, right: ExcludeSpec): ExcludeSpec? {
        if (left == right) {
            return left
        } else {
            return intersections.stream()
                .filter { i: Intersection<out ExcludeSpec?, out ExcludeSpec?>? -> i!!.applies(left, right) }
                .findFirst()
                .map<ExcludeSpec?>(Function { i: Intersection<out ExcludeSpec?, out ExcludeSpec?>? -> i!!.intersect(left, right, factory) })
                .orElse(null)
        }
    }

    private inner class IntersectAnyWithAny : AbstractIntersection<ExcludeAnyOf, ExcludeAnyOf>(ExcludeAnyOf::class.java, ExcludeAnyOf::class.java) {
        public override fun doIntersect(left: ExcludeAnyOf, right: ExcludeAnyOf, factory: ExcludeFactory): ExcludeSpec {
            val leftComponents = left.getComponents()
            val rightComponents = right.getComponents()

            val common = leftComponents.intersect(rightComponents)
            if (!common.isEmpty()) {
                val alpha = factory.fromUnion(common)
                if (leftComponents == common || rightComponents == common) {
                    return alpha
                }
                val remainderLeft = leftComponents.except(common)
                val remainderRight = rightComponents.except(common)

                val unionLeft = factory.fromUnion(remainderLeft)
                val unionRight = factory.fromUnion(remainderRight)
                val beta = factory.allOf(unionLeft, unionRight)
                return factory.anyOf(alpha, beta)
            } else {
                // slowest path, full distribution
                // (A ∪ B) ∩ (C ∪ D) = (A ∩ C) ∪ (A ∩ D) ∪ (B ∩ C) ∪ (B ∩ D)
                var intersections = PersistentSet.of<ExcludeSpec>()
                for (leftSpec in leftComponents) {
                    for (rightSpec in rightComponents) {
                        var merged = tryIntersect(leftSpec, rightSpec)
                        if (merged == null) {
                            merged = factory.allOf(leftSpec, rightSpec)
                        }
                        if (merged !is ExcludeNothing) {
                            intersections = intersections.plus(merged)
                        }
                    }
                }
                return factory.fromUnion(intersections)
            }
        }
    }

    private inner class IntersectAnyWithBaseSpec : AbstractIntersection<ExcludeAnyOf, ExcludeSpec>(ExcludeAnyOf::class.java, ExcludeSpec::class.java) {
        public override fun doIntersect(left: ExcludeAnyOf, right: ExcludeSpec, factory: ExcludeFactory): ExcludeSpec? {
            // Here, we will distribute A ∩ (B ∪ C) if, and only if, at
            // least one of the distribution operations (A ∩ B) can be simplified
            val excludeSpecs = left.getComponents().toArray<ExcludeSpec>(arrayOfNulls<ExcludeSpec>(0))
            var intersections: Array<ExcludeSpec?>? = null
            for (i in excludeSpecs.indices) {
                val excludeSpec = tryIntersect(excludeSpecs[i], right)
                if (excludeSpec != null) {
                    if (intersections == null) {
                        intersections = arrayOfNulls<ExcludeSpec>(excludeSpecs.size)
                    }
                    intersections[i] = excludeSpec
                }
            }
            if (intersections != null) {
                var simplified = PersistentSet.of<ExcludeSpec>()
                for (i in intersections.indices) {
                    val intersection = intersections[i]
                    if (intersection is ExcludeNothing) {
                        continue
                    }
                    if (intersection != null) {
                        simplified = simplified.plus(intersection)
                    } else {
                        simplified = simplified.plus(factory.allOf(excludeSpecs[i], right))
                    }
                }
                return factory.fromUnion(simplified)
            } else {
                return null
            }
        }

        override fun applies(left: ExcludeSpec, right: ExcludeSpec): Boolean {
            // We want to use the more specific AnyWithAny intersection if possible
            return (left is ExcludeAnyOf && right !is ExcludeAnyOf)
                    || (right is ExcludeAnyOf && left !is ExcludeAnyOf)
        }
    }

    private class IntersectGroupWithGroup : AbstractIntersection<GroupExclude, GroupExclude>(GroupExclude::class.java, GroupExclude::class.java) {
        public override fun doIntersect(left: GroupExclude, right: GroupExclude, factory: ExcludeFactory): ExcludeSpec {
            // equality has been tested before, so we know groups are different
            return factory.nothing()
        }
    }

    private class IntersectGroupWithModuleId : AbstractIntersection<GroupExclude, ModuleIdExclude>(GroupExclude::class.java, ModuleIdExclude::class.java) {
        public override fun doIntersect(left: GroupExclude, right: ModuleIdExclude, factory: ExcludeFactory): ExcludeSpec {
            val group = left.getGroup()
            if (right.getModuleId().getGroup() == group) {
                return right
            } else {
                return factory.nothing()
            }
        }
    }

    private class IntersectGroupWithGroupSet : AbstractIntersection<GroupExclude, GroupSetExclude>(GroupExclude::class.java, GroupSetExclude::class.java) {
        public override fun doIntersect(left: GroupExclude, right: GroupSetExclude, factory: ExcludeFactory): ExcludeSpec {
            val group = left.getGroup()
            if (right.getGroups().anyMatch(Predicate { g: String? -> g == group })) {
                return left
            }
            return factory.nothing()
        }
    }

    private class IntersectGroupWithModuleIdSet : AbstractIntersection<GroupExclude, ModuleIdSetExclude>(GroupExclude::class.java, ModuleIdSetExclude::class.java) {
        public override fun doIntersect(left: GroupExclude, right: ModuleIdSetExclude, factory: ExcludeFactory): ExcludeSpec {
            val group = left.getGroup()
            val moduleIds = right.getModuleIds().filter(Predicate { id: ModuleIdentifier -> id.getGroup() == group })
            return factory.fromModuleIds(moduleIds)
        }
    }

    private class IntersectGroupWithModule : AbstractIntersection<GroupExclude, ModuleExclude>(GroupExclude::class.java, ModuleExclude::class.java) {
        public override fun doIntersect(left: GroupExclude, right: ModuleExclude, factory: ExcludeFactory): ExcludeSpec {
            return factory.moduleId(DefaultModuleIdentifier.newId(left.getGroup(), right.getModule()))
        }
    }

    private class IntersectGroupWithModuleSet : AbstractIntersection<GroupExclude, ModuleSetExclude>(GroupExclude::class.java, ModuleSetExclude::class.java) {
        public override fun doIntersect(left: GroupExclude, right: ModuleSetExclude, factory: ExcludeFactory): ExcludeSpec {
            return factory.moduleIdSet(
                right.getModules().map<ModuleIdentifier>(Function { module: String? -> DefaultModuleIdentifier.newId(left.getGroup(), module!!) })
            )
        }
    }

    private class IntersectGroupSetWithGroupSet : AbstractIntersection<GroupSetExclude, GroupSetExclude>(GroupSetExclude::class.java, GroupSetExclude::class.java) {
        public override fun doIntersect(left: GroupSetExclude, right: GroupSetExclude, factory: ExcludeFactory): ExcludeSpec {
            val groups = left.getGroups()
            val common = right.getGroups().intersect(groups)
            return factory.fromGroups(common)
        }
    }

    private class IntersectGroupSetWithModuleId : AbstractIntersection<GroupSetExclude, ModuleIdExclude>(GroupSetExclude::class.java, ModuleIdExclude::class.java) {
        public override fun doIntersect(left: GroupSetExclude, right: ModuleIdExclude, factory: ExcludeFactory): ExcludeSpec {
            val groups = left.getGroups()
            if (groups.contains(right.getModuleId().getGroup())) {
                return right
            }
            return factory.nothing()
        }
    }

    private class IntersectGroupSetWithModuleIdSet : AbstractIntersection<GroupSetExclude, ModuleIdSetExclude>(GroupSetExclude::class.java, ModuleIdSetExclude::class.java) {
        public override fun doIntersect(left: GroupSetExclude, right: ModuleIdSetExclude, factory: ExcludeFactory): ExcludeSpec {
            val groups = left.getGroups()
            val filtered = right.getModuleIds()
                .filter(Predicate { id: ModuleIdentifier -> groups.contains(id.getGroup()) })
            return factory.fromModuleIds(filtered)
        }
    }

    private class IntersectModuleWithModule : AbstractIntersection<ModuleExclude, ModuleExclude>(ModuleExclude::class.java, ModuleExclude::class.java) {
        public override fun doIntersect(left: ModuleExclude, right: ModuleExclude, factory: ExcludeFactory): ExcludeSpec {
            val module = left.getModule()
            if (right.getModule() == module) {
                return left
            } else {
                return factory.nothing()
            }
        }
    }

    private class IntersectModuleWithModuleId : AbstractIntersection<ModuleExclude, ModuleIdExclude>(ModuleExclude::class.java, ModuleIdExclude::class.java) {
        public override fun doIntersect(left: ModuleExclude, right: ModuleIdExclude, factory: ExcludeFactory): ExcludeSpec {
            val module = left.getModule()
            if (right.getModuleId().getName() == module) {
                return right
            } else {
                return factory.nothing()
            }
        }
    }

    private class IntersectModuleWithModuleSet : AbstractIntersection<ModuleExclude, ModuleSetExclude>(ModuleExclude::class.java, ModuleSetExclude::class.java) {
        public override fun doIntersect(left: ModuleExclude, right: ModuleSetExclude, factory: ExcludeFactory): ExcludeSpec {
            val module = left.getModule()
            if (right.getModules().anyMatch(Predicate { g: String? -> g == module })) {
                return left
            }
            return factory.nothing()
        }
    }

    private class IntersectModuleWithModuleIdSet : AbstractIntersection<ModuleExclude, ModuleIdSetExclude>(ModuleExclude::class.java, ModuleIdSetExclude::class.java) {
        public override fun doIntersect(left: ModuleExclude, right: ModuleIdSetExclude, factory: ExcludeFactory): ExcludeSpec {
            val module = left.getModule()
            val common = right.getModuleIds().filter(Predicate { id: ModuleIdentifier -> id.getName() == module })
            return factory.fromModuleIds(common)
        }
    }

    private class IntersectModuleIdWithModuleId : AbstractIntersection<ModuleIdExclude, ModuleIdExclude>(ModuleIdExclude::class.java, ModuleIdExclude::class.java) {
        public override fun doIntersect(left: ModuleIdExclude, right: ModuleIdExclude, factory: ExcludeFactory): ExcludeSpec {
            if (left == right) {
                return left
            }
            return factory.nothing()
        }
    }

    private class IntersectModuleIdWithModuleIdSet : AbstractIntersection<ModuleIdExclude, ModuleIdSetExclude>(ModuleIdExclude::class.java, ModuleIdSetExclude::class.java) {
        public override fun doIntersect(left: ModuleIdExclude, right: ModuleIdSetExclude, factory: ExcludeFactory): ExcludeSpec {
            val rightModuleIds = right.getModuleIds()
            if (rightModuleIds.contains(left.getModuleId())) {
                return left
            }
            return factory.nothing()
        }
    }

    private class IntersectModuleIdWithModuleSet : AbstractIntersection<ModuleIdExclude, ModuleSetExclude>(ModuleIdExclude::class.java, ModuleSetExclude::class.java) {
        public override fun doIntersect(left: ModuleIdExclude, right: ModuleSetExclude, factory: ExcludeFactory): ExcludeSpec {
            if (right.getModules().contains(left.getModuleId().getName())) {
                return left
            } else {
                return factory.nothing()
            }
        }
    }

    private class IntersectModuleIdSetWithModuleIdSet : AbstractIntersection<ModuleIdSetExclude, ModuleIdSetExclude>(ModuleIdSetExclude::class.java, ModuleIdSetExclude::class.java) {
        public override fun doIntersect(left: ModuleIdSetExclude, right: ModuleIdSetExclude, factory: ExcludeFactory): ExcludeSpec {
            val moduleIds = left.getModuleIds()
            val common = right.getModuleIds().intersect(moduleIds)
            return factory.fromModuleIds(common)
        }
    }

    private class IntersectModuleIdSetWithModuleSet : AbstractIntersection<ModuleIdSetExclude, ModuleSetExclude>(ModuleIdSetExclude::class.java, ModuleSetExclude::class.java) {
        public override fun doIntersect(left: ModuleIdSetExclude, right: ModuleSetExclude, factory: ExcludeFactory): ExcludeSpec {
            val moduleIds = left.getModuleIds()
            val modules = right.getModules()
            val identifiers = moduleIds
                .filter(Predicate { e: ModuleIdentifier -> modules.contains(e.getName()) })
            if (identifiers.isEmpty()) {
                return factory.nothing()
            }
            if (identifiers.size() == 1) {
                return factory.moduleId(identifiers.iterator().next())
            } else {
                return factory.moduleIdSet(identifiers)
            }
        }
    }

    private class IntersectModuleSetWithModuleSet : AbstractIntersection<ModuleSetExclude, ModuleSetExclude>(ModuleSetExclude::class.java, ModuleSetExclude::class.java) {
        public override fun doIntersect(left: ModuleSetExclude, right: ModuleSetExclude, factory: ExcludeFactory): ExcludeSpec {
            val modules = left.getModules().intersect(right.getModules())
            if (modules.isEmpty()) {
                return factory.nothing()
            }
            if (modules.size() == 1) {
                return factory.module(modules.iterator().next())
            }
            return factory.moduleSet(modules)
        }
    }

    private class IntersectModuleSetWithGroupSet : AbstractIntersection<ModuleSetExclude, GroupSetExclude>(ModuleSetExclude::class.java, GroupSetExclude::class.java) {
        public override fun doIntersect(left: ModuleSetExclude, right: GroupSetExclude, factory: ExcludeFactory): ExcludeSpec {
            return factory.moduleIdSet(
                right.getGroups().flatMap<ModuleIdentifier>(Function { group: String? -> left.getModules().map<Any>(Function { module: String? -> DefaultModuleIdentifier.newId(group, module!!) }) })
            )
        }
    }

    private class IntersectModuleWithGroupSet : AbstractIntersection<ModuleExclude, GroupSetExclude>(ModuleExclude::class.java, GroupSetExclude::class.java) {
        public override fun doIntersect(left: ModuleExclude, right: GroupSetExclude, factory: ExcludeFactory): ExcludeSpec {
            return factory.moduleIdSet(right.getGroups().map<ModuleIdentifier>(Function { group: String? -> DefaultModuleIdentifier.newId(group, left.getModule()) }))
        }
    }
}
