package io.legado.desktop

class ReaderReadAloudController(
    private val modelFactory: (String) -> WindowsReadAloudModel = { text ->
        WindowsReadAloudModel(text)
    }
) : AutoCloseable {

    private var chapterKey: String? = null
    private var model: WindowsReadAloudModel? = null

    val currentModel: WindowsReadAloudModel?
        get() = model

    fun sync(chapterKey: String, text: String): WindowsReadAloudModel? {
        if (text.isBlank()) {
            clear()
            return null
        }
        if (this.chapterKey == chapterKey && model != null) return model

        clear()
        this.chapterKey = chapterKey
        return modelFactory(text).also { model = it }
    }

    fun clear() {
        model?.close()
        model = null
        chapterKey = null
    }

    override fun close() {
        clear()
    }
}
