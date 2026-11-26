// swift-tools-version:6.0
// PlayCaper pro Mac (PC-009): čte titulky z okna PS Remote Play a posílá překlad do přijímače na TV.
import PackageDescription

let v5: [SwiftSetting] = [.swiftLanguageMode(.v5)]

let package = Package(
    name: "PlayCaper",
    platforms: [.macOS(.v15)],
    targets: [
        // Čistá logika bez UI a bez snímání (testovatelná přes `swift test`).
        .target(name: "PlayCaperCore", swiftSettings: v5),
        .executableTarget(name: "PlayCaper", dependencies: ["PlayCaperCore"], swiftSettings: v5),
        .testTarget(name: "PlayCaperCoreTests", dependencies: ["PlayCaperCore"], swiftSettings: v5),
    ]
)
