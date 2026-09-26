pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MesOS"

// MesOS shared foundation: version identity, logging, preferences, design system.
include(":core")
// MesOS Home: home screen, dock and app drawer.
include(":launcher")
// MesOS Settings: settings categories, About MesOS, MesOS Update screen.
include(":settings")
// MesOS component updater engine (no UI).
include(":updater")
// MesOS apps.
include(":apps:camera")
include(":apps:photos")
include(":apps:files")
include(":apps:calculator")
include(":apps:notes")
include(":apps:weather")
include(":apps:calendar")
include(":apps:clock")
include(":apps:music")
include(":apps:scanner")
include(":apps:care")
include(":apps:recorder")
include(":apps:contacts")
include(":apps:tips")
// MesOS Shell: the deployable MesOS userland APK that bundles the modules above.
include(":shell")
