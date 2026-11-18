#if canImport(Testing)
import Testing
import Gradle

@Suite("Gradle Swift Export Tests")
struct GradleExportTests {
    @Test("Path round-trips construction, parent, ancestors, and startsWith")
    func testPathExports() {
        let p = util.Path.Companion.shared.path(path: ":a:b:c")
        #expect(p.asString() == ":a:b:c")
        #expect(p.isAbsolute)
        #expect(p.name == "c")
        #expect(p.parent?.asString() == ":a:b")
        #expect(p.ancestors().map { $0.asString() } == [":", ":a", ":a:b"])
        let prefix = util.Path.Companion.shared.path(path: ":a:b")
        #expect(p.startsWith(other: prefix))
    }
}
#elseif canImport(XCTest)
import XCTest
import Gradle

final class GradleExportTests: XCTestCase {
    func testPathExports() throws {
        let p = util.Path.Companion.shared.path(path: ":a:b:c")
        XCTAssertEqual(p.asString(), ":a:b:c")
        XCTAssertTrue(p.isAbsolute)
        XCTAssertEqual(p.name, "c")
        XCTAssertEqual(p.parent?.asString(), ":a:b")
        XCTAssertEqual(p.ancestors().map { $0.asString() }, [":", ":a", ":a:b"])
        let prefix = util.Path.Companion.shared.path(path: ":a:b")
        XCTAssertTrue(p.startsWith(other: prefix))
    }
}
#endif
