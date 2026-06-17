/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.tasks.scala

import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode
import java.io.Serializable

/**
 * Immutable classpath with it's precomputed hash.
 */
class HashedClasspath(val classpath: ClassPath?, val hash: HashCode?) : Serializable {
    constructor(classpath: ClassPath, hasher: ClasspathHasher) : this(classpath, hasher.hash(classpath))
}
