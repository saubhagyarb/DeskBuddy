import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.saubh.deskbuddy.MainKt"
        // Tray-resident app: cap the heap and use the low-footprint collector.
        jvmArgs += listOf("-Xms32m", "-Xmx256m", "-XX:+UseSerialGC")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // From ./gradlew :desktopApp:suggestRuntimeModules (Ktor, JNA, StartupRegistration).
            modules("java.instrument", "java.management", "jdk.unsupported")
            packageName = "KonneKt"
            packageVersion = "1.0.0"
            // Per-OS files next to the app (read through compose.application.resources.dir): macos/ holds mediaremote-adapter.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            windows {
                iconFile.set(project.file("icons/deskbuddy.ico"))
                menuGroup = "KonneKt"
                shortcut = true
            }
            macOS {
                iconFile.set(project.file("icons/deskbuddy.icns"))
                bundleID = "com.saubh.konnekt"
                infoPlist {
                    // Without this key macOS silently refuses the Apple Events behind now playing, power and open windows.
                    extraKeysRawXml = """
                        <key>NSAppleEventsUsageDescription</key>
                        <string>KonneKt controls music, windows and power on this Mac when you ask from your phone.</string>
                    """.trimIndent()
                }
            }
            linux {
                iconFile.set(project.file("icons/deskbuddy.png"))
            }
        }
    }
}