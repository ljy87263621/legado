package io.legado.desktop

import io.legado.desktop.persistence.DesktopSetupStore

enum class DesktopStartupRoute {
    WELCOME,
    BOOKSHELF,
    SUBSCRIPTIONS;

    companion object {
        fun resolve(
            setupComplete: Boolean?,
            hasLaunchRequest: Boolean,
            setupStoreAvailable: Boolean = true,
            hasLocalFileLaunch: Boolean = false
        ): AppRoute = when {
            hasLocalFileLaunch -> AppRoute.BOOKSHELF
            hasLaunchRequest -> AppRoute.SUBSCRIPTIONS
            !setupStoreAvailable -> AppRoute.BOOKSHELF
            setupComplete == true -> AppRoute.BOOKSHELF
            else -> AppRoute.WELCOME
        }
    }
}

class WelcomeModel(
    private val setupStore: DesktopSetupStore
) {
    fun completeSetup() {
        setupStore.markSetupComplete()
    }
}
