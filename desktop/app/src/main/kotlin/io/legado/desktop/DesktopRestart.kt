package io.legado.desktop

import java.nio.file.Path

object DesktopRestart {

    fun command(executable: Path, dataDirectory: Path): List<String> = listOf(
        executable.toAbsolutePath().normalize().toString(),
        "--data-dir",
        dataDirectory.toAbsolutePath().normalize().toString()
    )

    fun restart(executable: Path, dataDirectory: Path) {
        ProcessBuilder(command(executable, dataDirectory))
            .redirectErrorStream(true)
            .start()
    }
}
