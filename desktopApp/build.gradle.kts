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

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "DeskBuddy"
            packageVersion = "1.0.0"

            windows {
                iconFile.set(project.file("icons/deskbuddy.ico"))
                menuGroup = "DeskBuddy"
                shortcut = true
            }
            macOS {
                iconFile.set(project.file("icons/deskbuddy.icns"))
            }
            linux {
                iconFile.set(project.file("icons/deskbuddy.png"))
            }
        }
    }
}