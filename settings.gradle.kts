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

rootProject.name = "SiliconVerity"

include(":app")
include(":core:model")
include(":core:hardware")
include(":core:provenance")
include(":core:benchmark")
include(":core:storage")
include(":core:designsystem")
include(":benchmark:storage")
include(":native:cpu")
include(":native:memory")
include(":feature:hardware")
include(":feature:home")
include(":feature:history")
include(":feature:settings")
