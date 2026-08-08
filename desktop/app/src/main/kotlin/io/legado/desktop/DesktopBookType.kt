package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookType

object DesktopBookType {
    private const val TEXT_BIT = CoreBookType.TEXT
    private const val AUDIO_BIT = CoreBookType.AUDIO
    private const val IMAGE_BIT = CoreBookType.IMAGE
    private const val LOCAL_BIT = 1 shl 8

    const val TEXT = TEXT_BIT or LOCAL_BIT
    const val AUDIO = AUDIO_BIT or LOCAL_BIT
    const val IMAGE = IMAGE_BIT or LOCAL_BIT
    const val ONLINE_IMAGE = IMAGE_BIT

    fun isImage(type: Int): Boolean = type and IMAGE_BIT > 0

    fun isAudio(type: Int): Boolean = type and AUDIO_BIT > 0

    fun isLocalImageBook(book: CoreBook): Boolean = book.origin == "loc_book" && isImage(book.type)

    fun isOnlineImageBook(book: CoreBook): Boolean = book.origin != "loc_book" && isImage(book.type)

    fun isLocalAudioBook(book: CoreBook): Boolean = book.origin == "loc_book" && isAudio(book.type)
}
