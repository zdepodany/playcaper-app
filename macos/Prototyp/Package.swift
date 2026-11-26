// swift-tools-version:6.0
// Prototyp PC-009: ScreenCaptureKit + Vision nad oknem PS Remote Play.
import PackageDescription

let package = Package(
    name: "Prototyp",
    platforms: [.macOS(.v15)],
    targets: [
        .executableTarget(
            name: "Prototyp",
            path: "Sources",
            swiftSettings: [.swiftLanguageMode(.v5)]
        ),
    ]
)
