package io.github.kotlinmania.gradle.util

import kotlin.concurrent.Volatile

/**
 * Represents a hierarchical colon-separated path in Gradle.
 */
class Path private constructor(
    private val segments: List<String>,
    val isAbsolute: Boolean,
) : Comparable<Path> {
    private val cachedHashCode: Int = computeHashCode(isAbsolute, segments)

    @Volatile
    private var fullPath: String? = null

    val parent: Path?
        get() =
            when {
                segments.isEmpty() -> null
                segments.size == 1 -> if (isAbsolute) ROOT else null
                else -> Path(segments.subList(0, segments.size - 1), isAbsolute)
            }

    val name: String?
        get() = if (segments.isEmpty()) null else segments[segments.size - 1]

    override fun toString(): String = asString()

    /**
     * Appends the supplied path to this path, returning the new path.
     * The resulting path is absolute or relative based on the path being appended to.
     */
    fun append(path: Path): Path {
        if (segments.isEmpty()) {
            return if (isAbsolute == path.isAbsolute) {
                path
            } else {
                Path(path.segments, isAbsolute)
            }
        }
        if (path.segments.isEmpty()) {
            return this
        }
        val concat = ArrayList<String>(segments.size + path.segments.size)
        concat.addAll(segments)
        concat.addAll(path.segments)
        return Path(concat, isAbsolute)
    }

    /** Returns the string representation of this path. */
    @Deprecated("Use asString() instead")
    fun getPath(): String = asString()

    /** Returns the full path as a string. */
    fun asString(): String {
        val existing = fullPath
        if (existing != null) {
            return existing
        }
        val created = createFullPath()
        fullPath = created
        return created
    }

    private fun createFullPath(): String =
        buildString {
            if (isAbsolute) {
                append(SEPARATOR)
            }
            for (i in segments.indices) {
                if (i > 0) {
                    append(SEPARATOR)
                }
                append(segments[i])
            }
        }

    /** Returns an immutable list of the segments of this path. */
    fun segments(): List<String> = segments.toList()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Path) {
            return false
        }
        if (isAbsolute != other.isAbsolute) {
            return false
        }
        return segments == other.segments
    }

    override fun hashCode(): Int = cachedHashCode

    override fun compareTo(other: Path): Int {
        if (isAbsolute && !other.isAbsolute) {
            return 1
        }
        if (!isAbsolute && other.isAbsolute) {
            return -1
        }
        val limit = minOf(segments.size, other.segments.size)
        for (i in 0 until limit) {
            val diff = compareCaseInsensitive(segments[i], other.segments[i])
            if (diff != 0) {
                return diff
            }
        }
        val lenDiff = segments.size - other.segments.size
        return when {
            lenDiff > 0 -> 1
            lenDiff < 0 -> -1
            else -> 0
        }
    }

    /** Creates a child of this path with the given name. */
    fun child(name: String): Path {
        val childSegments = ArrayList<String>(segments.size + 1)
        childSegments.addAll(segments)
        childSegments.add(name)
        return Path(childSegments, isAbsolute)
    }

    /** Resolves the given name relative to this path. If an absolute path is provided, it is returned. */
    fun absolutePath(path: String): String = absolutePath(path(path)).asString()

    /** Resolves the given path relative to this path. If an absolute path is provided, it is returned. */
    fun absolutePath(path: Path): Path = if (path.isAbsolute) path else append(path)

    /** Calculates a path relative to this path. If the given path is not a child of this path, it is returned unmodified. */
    fun relativePath(path: String): String = relativePath(path(path)).asString()

    /** Calculates a path relative to this path. If the given path is not a child of this path, it is returned unmodified. */
    fun relativePath(path: Path): Path {
        if (path.isAbsolute != isAbsolute || path.segments.size < segments.size) {
            return path
        }
        for (i in segments.indices) {
            if (path.segments[i] != segments[i]) {
                return path
            }
        }
        if (path.segments.size == segments.size) {
            return path
        }
        return Path(path.segments.subList(segments.size, path.segments.size).toList(), false)
    }

    fun segmentCount(): Int = segments.size

    fun removeFirstSegments(n: Int): Path {
        if (n == 0) {
            return this
        }
        if (n == segments.size && isAbsolute) {
            return ROOT
        }
        if (n < 0 || n >= segments.size) {
            throw IllegalArgumentException("Cannot remove $n segments from path ${asString()}")
        }
        return Path(segments.subList(n, segments.size).toList(), isAbsolute)
    }

    fun segment(index: Int): String {
        if (index < 0 || index >= segments.size) {
            throw IllegalArgumentException("Segment index $index is invalid for path ${asString()}")
        }
        return segments[index]
    }

    /**
     * Returns a [Path] containing only the first [n] segments of this [Path].
     * The returned [Path] is absolute if this [Path] is absolute.
     */
    fun takeFirstSegments(n: Int): Path {
        if (n < 1) {
            throw IllegalArgumentException("Taken path segment count must be >= 1.")
        }
        if (n >= segmentCount()) {
            return this
        }
        return Path(segments.subList(0, minOf(n, segmentCount())).toList(), isAbsolute)
    }

    /**
     * Returns all ancestors of this path, starting with the root path (if absolute)
     * or the first segment (if relative) and not including this path.
     */
    fun ancestors(): List<Path> {
        val result = ArrayList<Path>(segments.size)
        var segmentsToInclude = if (isAbsolute) 0 else 1
        while (segmentsToInclude < segments.size) {
            if (segmentsToInclude == 0) {
                segmentsToInclude++
                result.add(ROOT)
            } else {
                val count = segmentsToInclude
                segmentsToInclude++
                result.add(takeFirstSegments(count))
            }
        }
        return result
    }

    /** Determines if this path starts with the given path. */
    fun startsWith(other: Path): Boolean {
        if (other.isAbsolute != isAbsolute || other.segments.size > segments.size) {
            return false
        }
        for (i in other.segments.indices) {
            if (other.segments[i] != segments[i]) {
                return false
            }
        }
        return true
    }

    companion object {
        private const val HASH_MULTIPLIER = 31

        val ROOT: Path = Path(emptyList(), true)
        const val SEPARATOR: String = ":"

        fun path(path: String?): Path {
            if (path.isNullOrEmpty()) {
                throw IllegalArgumentException("A path must be specified!")
            }
            return if (SEPARATOR == path) {
                ROOT
            } else {
                parsePath(path)
            }
        }

        fun validatePath(path: String?) {
            validatePathInternal(path)
        }

        private fun validatePathInternal(path: String?) {
            require(!path.isNullOrEmpty()) { "A path must be specified!" }
        }

        private fun parsePath(path: String): Path {
            val segments = path.split(SEPARATOR).filter { it.isNotEmpty() }
            val absolute = path.startsWith(SEPARATOR)
            return Path(segments, absolute)
        }

        private fun computeHashCode(
            absolute: Boolean,
            segments: List<String>,
        ): Int {
            var result = 0
            for (element in segments) {
                result = HASH_MULTIPLIER * result + element.hashCode()
            }
            result = HASH_MULTIPLIER * result + if (absolute) 1 else 0
            return result
        }

        private fun compareCaseInsensitive(
            o1: String,
            o2: String,
        ): Int {
            val diff = o1.compareTo(o2, ignoreCase = true)
            if (diff != 0) {
                return diff
            }
            return o1.compareTo(o2)
        }
    }
}
