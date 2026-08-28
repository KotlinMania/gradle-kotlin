#if canImport(Testing)
import Testing
import Gradle

@Suite("Gradle Swift Export Tests")
struct GradleExportTests {
    @Test("Swift module loads")
    func testSwiftModuleLoads() {
        #expect(Bool(true), "Gradle swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Gradle

final class GradleExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "Gradle swift module imported cleanly")
    }
}
#endif
