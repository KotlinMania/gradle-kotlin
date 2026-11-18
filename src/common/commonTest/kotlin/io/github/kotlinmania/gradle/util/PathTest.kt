package io.github.kotlinmania.gradle.util

import io.github.kotlinmania.gradle.util.Path.Companion.ROOT
import io.github.kotlinmania.gradle.util.Path.Companion.path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PathTest {
    @Test
    fun constructionFromString() {
        assertEquals(":", path(":").asString())
        assertSame(ROOT, path(":"))
        assertEquals(":", ROOT.asString())
        assertEquals("a", path("a").asString())
        assertEquals("a:b:c", path("a:b:c").asString())
        assertEquals(":a", path(":a").asString())
        assertEquals(":a:b", path(":a:b").asString())
        assertEquals(":a:b", path(":a:b:").asString())
    }

    @Test
    fun equalsAndHashCode() {
        assertEqualsAndHash(ROOT, ROOT)
        assertEqualsAndHash(path("path"), path("path"))
        assertEqualsAndHash(path(":a:path"), path(":a:path"))
        assertNotEquals(path(":a"), path(":b"))
        assertNotEquals(path(":a"), path("a"))
    }

    @Test
    fun canGetParent() {
        assertEquals(path(":a"), path(":a:b").parent)
        assertEquals(path(":"), path(":a").parent)
        assertNull(path(":").parent)
        assertEquals(path("a"), path("a:b").parent)
        assertNull(path("a").parent)
    }

    @Test
    fun canGetName() {
        assertEquals("b", path(":a:b").name)
        assertEquals("a", path(":a").name)
        assertNull(path(":").name)
        assertEquals("b", path("a:b").name)
        assertEquals("a", path("a").name)
    }

    @Test
    fun canCreateChild() {
        assertEquals(path(":a"), path(":").child("a"))
        assertEquals(path(":a:b"), path(":a").child("b"))
        assertEquals(path("a:b:c"), path("a:b").child("c"))
    }

    @Test
    fun convertsRelativePathToAbsolutePath() {
        assertEquals(":path", path(":").absolutePath("path"))
        assertEquals(":sub:path", path(":sub").absolutePath("path"))
    }

    @Test
    fun convertsAbsolutePathToAbsolutePath() {
        val rootPath = path(":")
        assertEquals(":", rootPath.absolutePath(":"))
        assertEquals(":path", rootPath.absolutePath(":path"))
    }

    @Test
    fun convertsAbsolutePathToRelativePath() {
        val rootPath = path(":")
        assertEquals(":", rootPath.relativePath(":"))
        assertEquals("path", rootPath.relativePath("path".let { ":$it" }))

        val subPath = path(":sub")
        assertEquals(":", subPath.relativePath(":"))
        assertEquals(":sub", subPath.relativePath(":sub"))
        assertEquals("path", subPath.relativePath(":sub:path"))
        assertEquals(":sub2:path", subPath.relativePath(":sub2:path"))
        assertEquals(":other:path", subPath.relativePath(":other:path"))
    }

    @Test
    fun convertsRelativePathToRelativePath() {
        assertEquals("path", path(":").relativePath("path"))
    }

    @Test
    fun appendsPath() {
        assertEquals(path(":a:b:c:d"), path(":a:b").append(path(":c:d")))
        assertEquals(path(":a:b:c:d"), path(":a:b").append(path("c:d")))
        assertEquals(path("a:b:c:d"), path("a:b").append(path(":c:d")))
        assertEquals(path("a:b:c:d"), path("a:b").append(path("c:d")))
    }

    @Test
    fun appendsPathToAbsolutePath() {
        val p = path(":path")
        assertSame(p, p.append(ROOT))
        assertEquals(path(":path:absolute"), p.append(path(":absolute")))
        assertEquals(path(":path:absolute:subpath"), p.append(path(":absolute:subpath")))
        assertEquals(path(":path:relative"), p.append(path("relative")))
        assertEquals(path(":path:relative:subpath"), p.append(path("relative:subpath")))
    }

    @Test
    fun appendsPathToRelativePath() {
        val p = path("path")
        assertSame(p, p.append(ROOT))
        assertEquals(path("path:absolute"), p.append(path(":absolute")))
        assertEquals(path("path:absolute:subpath"), p.append(path(":absolute:subpath")))
        assertEquals(path("path:relative"), p.append(path("relative")))
        assertEquals(path("path:relative:subpath"), p.append(path("relative:subpath")))
    }

    @Test
    fun appendsEmptyPaths() {
        val relativeFoo = path("foo")
        val absoluteFoo = path(":foo")

        assertSame(ROOT, ROOT.append(ROOT))
        assertSame(absoluteFoo, ROOT.append(absoluteFoo))
        assertSame(absoluteFoo, absoluteFoo.append(ROOT))
        assertSame(relativeFoo, relativeFoo.append(ROOT))
        assertEquals(absoluteFoo, ROOT.append(relativeFoo))
    }

    @Test
    fun sortsPathsDepthFirstCaseInsensitive() {
        assertEquals(
            paths("A", "a", "abc", "b"),
            paths("a", "b", "A", "abc").sorted(),
        )
        assertEquals(
            paths(":", ":a", ":a:a", ":B", ":B:a", ":b", ":b:a"),
            paths(":a", ":b", ":b:a", ":B:a", ":", ":B", ":a:a").sorted(),
        )
        assertEquals(
            paths("a", "a:a", "b", "b:a"),
            paths("b", "b:a", "a", "a:a").sorted(),
        )
        assertEquals(
            paths("a", ":", ":a"),
            paths(":", ":a", "a").sorted(),
        )
    }

    @Test
    fun countsSegments() {
        assertEquals(0, ROOT.segmentCount())
        assertEquals(0, path(":").segmentCount())
        assertEquals(1, path("a").segmentCount())
        assertEquals(1, path(":a").segmentCount())
        assertEquals(2, path(":a:b").segmentCount())
        assertEquals(2, path("a:b").segmentCount())
        assertEquals(3, path(":a:b:c").segmentCount())
        assertEquals(3, path("a:b:c").segmentCount())
    }

    @Test
    fun canRemoveAllSegmentsFromAbsolutePath() {
        for (raw in listOf(":", ":a", ":a:b")) {
            val p = path(raw)
            assertSame(ROOT, p.removeFirstSegments(p.segmentCount()))
        }
    }

    @Test
    fun removesInvalidSegments() {
        val cases =
            listOf(
                "a:b" to -1,
                "a:b" to 2,
                ":a:b" to -1,
                ":a:b" to 3,
            )
        for ((raw, count) in cases) {
            val e =
                assertFailsWith<IllegalArgumentException> {
                    path(raw).removeFirstSegments(count)
                }
            assertEquals("Cannot remove $count segments from path $raw", e.message)
        }
    }

    @Test
    fun removesZeroSegments() {
        for (raw in listOf(":", "a", ":a", ":a:b", "a:b", ":a:b:c", "a:b:c")) {
            assertEquals(path(raw), path(raw).removeFirstSegments(0))
        }
        assertEquals(ROOT, ROOT.removeFirstSegments(0))
    }

    @Test
    fun removesOneSegment() {
        assertEquals(path(":b"), path(":a:b").removeFirstSegments(1))
        assertEquals(path("b"), path("a:b").removeFirstSegments(1))
        assertEquals(path(":b:c"), path(":a:b:c").removeFirstSegments(1))
        assertEquals(path("b:c"), path("a:b:c").removeFirstSegments(1))
    }

    @Test
    fun removesTwoSegments() {
        assertEquals(path(":c"), path(":a:b:c").removeFirstSegments(2))
        assertEquals(path("c"), path("a:b:c").removeFirstSegments(2))
    }

    @Test
    fun retrievesSegment() {
        assertEquals("a", path(":a:b:c").segment(0))
        assertEquals("b", path(":a:b:c").segment(1))
        assertEquals("c", path(":a:b:c").segment(2))
        assertEquals("a", path("a:b:c").segment(0))
        assertEquals("b", path("a:b:c").segment(1))
        assertEquals("c", path("a:b:c").segment(2))

        val e1 = assertFailsWith<IllegalArgumentException> { path(":a:b:c").segment(3) }
        assertEquals("Segment index 3 is invalid for path :a:b:c", e1.message)

        val e2 = assertFailsWith<IllegalArgumentException> { path(":a:b:c").segment(-1) }
        assertEquals("Segment index -1 is invalid for path :a:b:c", e2.message)
    }

    @Test
    fun testIfPathIsAbsolute() {
        assertTrue(path(":").isAbsolute)
        assertTrue(path(":a").isAbsolute)
        assertFalse(path("a").isAbsolute)
        assertTrue(path(":a:b").isAbsolute)
        assertFalse(path("a:b").isAbsolute)
    }

    @Test
    fun canTakeFirstNSegments() {
        assertEquals(path(":"), path(":").takeFirstSegments(1))
        assertEquals(path(":foo"), path(":foo").takeFirstSegments(1))
        assertEquals(path(":foo"), path(":foo:bar").takeFirstSegments(1))
        assertEquals(path(":foo:bar"), path(":foo:bar").takeFirstSegments(2))
        assertEquals(path(":foo:bar"), path(":foo:bar:baz").takeFirstSegments(2))
    }

    @Test
    fun canTakeFirstNSegmentsWithoutIncurringUnnecessaryCopies() {
        val p = path(":foo:bar")
        assertSame(p, p.takeFirstSegments(42))
    }

    @Test
    fun segments() {
        assertEquals(listOf("foo", "bar"), path(":foo:bar").segments())
        assertEquals(listOf("foo", "bar"), path("foo:bar").segments())
        assertEquals(listOf("foo", "bar", "zzz"), path("foo:bar:zzz").segments())
    }

    @Test
    fun validatePathFails() {
        for (raw in listOf("", null)) {
            assertFailsWith<IllegalArgumentException> {
                Path.validatePath(raw)
            }
        }
    }

    @Test
    fun validatePathSucceeds() {
        for (raw in listOf("path", ":path:p2")) {
            Path.validatePath(raw)
        }
    }

    @Test
    fun canGetAncestorsOfAbsolutePath() {
        assertEquals(emptyList(), path(":").ancestors())
        assertEquals(paths(":"), path(":a").ancestors())
        assertEquals(paths(":", ":a"), path(":a:b").ancestors())
        assertEquals(paths(":", ":a", ":a:b"), path(":a:b:c").ancestors())
    }

    @Test
    fun canGetAncestorsOfRelativePath() {
        assertEquals(emptyList(), path("a").ancestors())
        assertEquals(paths("a"), path("a:b").ancestors())
        assertEquals(paths("a", "a:b"), path("a:b:c").ancestors())
    }

    @Test
    fun canDetermineIfOnePathStartsWithAnother() {
        assertTrue(path(":a:b").startsWith(path(":a")))
        assertTrue(path(":a:b").startsWith(path(":a:b")))
        assertFalse(path(":a:b").startsWith(path(":b")))
        assertFalse(path(":a:b").startsWith(path("a")))
        assertFalse(path("a:b").startsWith(path(":a")))
        assertFalse(path("a:b").startsWith(path("b")))
        assertTrue(path("a:b:c").startsWith(path("a:b")))
        assertTrue(path("a:b:c").startsWith(path("a:b:c")))
    }

    private fun paths(vararg raw: String): List<Path> = raw.map { path(it) }

    private fun assertEqualsAndHash(
        first: Path,
        second: Path,
    ) {
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}
