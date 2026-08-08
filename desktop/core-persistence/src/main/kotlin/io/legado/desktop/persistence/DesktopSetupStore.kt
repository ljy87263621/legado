package io.legado.desktop.persistence

interface DesktopSetupStore {
    fun isSetupComplete(): Boolean

    fun markSetupComplete()
}
