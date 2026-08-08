package io.legado.desktop

data class DesktopLaunchRequest(
    val onlineImportUrl: String? = null,
    val subscriptionPageUrl: String? = null,
    val localFilePath: String? = null,
    val startupValidation: Boolean = false,
    val portable: Boolean = false,
    val dataDirectory: String? = null
) {
    companion object {
        fun fromArgs(args: Array<String>): DesktopLaunchRequest? {
            val startupValidation = args.any { it.trim().equals("--validate-startup", ignoreCase = true) }
            val portable = args.any { it.trim().equals("--portable", ignoreCase = true) }
            val dataDirectory = args.asSequence()
                .map(String::trim)
                .firstNotNullOfOrNull { argument ->
                    when {
                        argument.startsWith("--data-dir=", ignoreCase = true) ->
                            argument.substringAfter('=').trim().takeIf(String::isNotEmpty)
                        else -> null
                    }
                }
                ?: args.asSequence()
                    .mapIndexed { index, argument -> index to argument.trim() }
                    .firstNotNullOfOrNull { (index, argument) ->
                        if (argument.equals("--data-dir", ignoreCase = true)) {
                            args.getOrNull(index + 1)?.trim()?.takeIf(String::isNotEmpty)
                        } else {
                            null
                        }
                    }
            if (startupValidation) {
                return DesktopLaunchRequest(
                    startupValidation = true,
                    portable = portable,
                    dataDirectory = dataDirectory
                )
            }
            args.asSequence()
                .mapIndexed { index, argument -> index to argument.trim() }
                .filter { (_, argument) -> argument.isNotEmpty() }
                .forEach { (index, argument) ->
                    if (argument.equals("--data-dir", ignoreCase = true)) return@forEach
                    if (index > 0 && args[index - 1].trim().equals("--data-dir", ignoreCase = true)) {
                        return@forEach
                    }
                    if (argument.startsWith("yuedu://", ignoreCase = true) ||
                        argument.startsWith("legado://", ignoreCase = true)
                    ) {
                        if (SourceImportUrl.classify(argument) == SourceImportKind.BOOK_SOURCE) {
                            return DesktopLaunchRequest(
                                onlineImportUrl = SourceImportUrl.resolve(argument),
                                portable = portable,
                                dataDirectory = dataDirectory
                            )
                        }
                        return@forEach
                    }
                    if (argument.startsWith("http://", ignoreCase = true) ||
                        argument.startsWith("https://", ignoreCase = true)
                    ) {
                        return DesktopLaunchRequest(
                            subscriptionPageUrl = argument,
                            portable = portable,
                            dataDirectory = dataDirectory
                        )
                    }
                    if (WindowsFileAssociationRegistration.isSupportedPath(argument)) {
                        return DesktopLaunchRequest(
                            localFilePath = argument,
                            portable = portable,
                            dataDirectory = dataDirectory
                        )
                    }
                }
            return if (portable || dataDirectory != null) {
                DesktopLaunchRequest(portable = portable, dataDirectory = dataDirectory)
            } else {
                null
            }
        }
    }
}
