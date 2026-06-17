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
package org.gradle.internal.service.scopes

/**
 * Each scope corresponds to some state managed by Gradle when
 * executing a *Gradle invocation* against a *user build*.
 *
 *
 * **User build** is a collection of files on disk that constitutes the user's software project,
 * building of which is orchestrated by Gradle.
 * It can be a Java library, an enterprise monorepo or anything else.
 *
 *
 * **Gradle invocation** is a direct or indirect request of the user to invoke Gradle
 * over a given *user build* to do some work.
 * It can be a command-line invocation or a Tooling API client request (e.g. an IDE sync).
 *
 * <h2>Scope hierarchy</h2>
 * The scopes are arranged in a hierarchy (with some scopes having multiple parents):
 * <pre>
 * Global
 * ┌────────┴─────────┐
 * UserHome      CrossBuildSession
 * └────────┬─────────┘
 * BuildSession
 * │
 * BuildTree
 * │
 * Build
 * ┌────┴────┐
 * Project   Settings
</pre> *
 *
 * Each scope roughly corresponds to the following user-facing concepts:
 *
 *  * [Global]            — Gradle daemon process
 *  * [UserHome]          — Gradle user home
 *  * [CrossBuildSession] — exists mainly because of `GradleBuild` task
 *  * [BuildSession]      — continuous build
 *  * [BuildTree]         — composite build
 *  * [Build]             — build in a composite build
 *  * [Settings]          — init scripts, settings script
 *  * [Project]           — project in a build
 *
 *
 * There can be multiple "instances" of a scope inside one "instance" of a parent scope.
 *
 *
 * For example, in a composite build, the simplified hierarchy of state can look like:
 * <pre>
 * build tree
 * ├── root build
 * │   ├── root project
 * │   └── project
 * └── included build
 * ├── root project
 * └── project
</pre> *
 *
 * <h3>Services and their visibility</h3>
 * The state in each scope is created and managed by services registered in that scope.
 * All services of a scope are assembled in a `ServiceRegistry`.
 * When the registry is closed all its services are closed as well and the state is discarded.
 *
 *
 * Services of all parent scopes are visible to services in a given scope.
 * For example, all `Global` services are visible to services in `UserHome` scope, but not vice versa.
 *
 * @see ServiceScope
 */
interface Scope {
    /**
     * Scope of the entire *build process*, e.g. a long-lived Gradle daemon.
     *
     *
     * The build process can potentially serve multiple Gradle invocations.
     * The *build process state* holds the global state of the build process and manages all the other state.
     *
     *
     * The build process state is managed by the `BuildProcessState` class.
     * An instance is created once for a given process.
     *
     *
     * Global services are visible to all other services.
     */
    interface Global : Scope

    /**
     * The scope of a Gradle invocation with a specific Gradle user home directory.
     *
     *
     * When the user-home directory changes between subsequent Gradle invocations in the same *build process*,
     * the state of this scope is discarded and recreated.
     * Otherwise, the state is reused between invocations.
     *
     *
     * The related state is created per Gradle invocation.
     *
     *
     * [Global] services are visible to [UserHome] services and descendant scopes, but not vice versa.
     */
    interface UserHome : Global

    /**
     * The scope of the state shared across [build sessions][BuildSession].
     *
     *
     * A regular Gradle invocation requires only one "main" build session.
     * However, when the `GradleBuild` task is involved, it can create "nested" build sessions.
     * Having the `GradleBuild` task reuse the "main" build session is complicated because
     * it [can use a different Gradle user home](https://github.com/gradle/gradle/issues/4559).
     *
     *
     * The cross build session state is managed by the `CrossBuildSessionState` class.
     * An instance is created per Gradle invocation.
     * Unlike the `UserHome` or `Global` state, this state is discarded at the end of each invocation.
     *
     *
     * [Global] services are visible to [CrossBuildSession] services and descendant scopes, but not vice versa.
     */
    interface CrossBuildSession : Global

    /**
     * The scope of a build session.
     *
     *
     * A *build session* represents a single invocation of Gradle, for example when you run `gradlew build`.
     * A session runs the build one or more times.
     * For example, when continuous build is enabled, the session may run the build many times,
     * but when it is disabled, the session will run the build only once.
     *
     *
     * The build session state is managed by the `BuildSessionState` class.
     * An instance is created at the start of a Gradle invocation and discarded at the end of that invocation.
     *
     *
     * [UserHome], [CrossBuildSession] and their parent services are visible to
     * [BuildSession] services and descendant scopes, but not vice versa.
     */
    interface BuildSession : UserHome, CrossBuildSession

    /**
     * The scope of a single *build execution* within a [build session][BuildSession].
     *
     *
     * *Build tree* is another name for the build definition (`BuildDefinition`),
     * which corresponds to composite build.
     *
     *
     * The *build tree state* holds the state for the entire build definition for a single build execution within a session.
     * The build tree state is managed by the `BuildTreeState` class.
     * An instance is created at the start of a build execution and discarded at the end of that execution.
     *
     *
     * [BuildSession] and its parent services are visible to [BuildTree] services and descendant scopes, but not vice versa.
     */
    interface BuildTree : BuildSession

    /**
     * The scope of a single *build* within a [build tree][BuildTree].
     *
     *
     * The *build state* holds the state for a *build* within the *build definition* for a single *build execution*,
     * and is contained by the *build tree state*.
     *
     *
     * The build state is managed by the `BuildState` class.
     * An instance is created for each build in the build definition, once per build execution and is discarded at the end of that execution.
     *
     *
     * There is one-to-one correspondence between a *build* and its `Settings`.
     * However, the build state also contains the state exposed to init scripts,
     * evaluation of which happens prior to the evaluation of settings.
     *
     *
     * [BuildTree] and parent services are visible to [Build] services and descendant scopes, but not vice versa.
     */
    interface Build : BuildTree

    /**
     * The scope that owns the settings of a build.
     *
     *
     * The settings state is managed by the `SettingsState` class.
     * The creation of that state implies evaluation of init scripts and settings scripts
     * of the owner-build and any builds that are included as part of a composite build.
     *
     *
     * The state is discarded at the end of the build execution.
     *
     *
     * [Build] and parent services are visible to [Settings] scope services, but not vice versa.
     */
    interface Settings : Build

    /**
     * The scope of a single project within a [build][Build].
     *
     *
     * The *project state* holds the state for a project for a single build execution,
     * and is contained by the build state (and not the state of the parent project).
     *
     *
     * The project state is managed by the `ProjectState` class.
     * It is created for each project in the build definition,
     * once per build execution and is discarded at the end of the execution.
     *
     *
     * [Build] and parent scope services are visible to [Project] scope services, but not vice versa.
     */
    interface Project : Build
}
