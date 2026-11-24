package org.mobilism.material.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

// These provider protocol tests run on the JVM without Android Resources.
private val providerMessages by lazy {
    val file = listOf(File("src/main/res/values/strings.xml"), File("core/data/src/main/res/values/strings.xml")).first { it.isFile }
    val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
    (0 until strings.length).associate {
        val element = strings.item(it) as Element
        R.string::class.java.getField(element.getAttribute("name")).getInt(null) to element.textContent.replace("\\'", "'")
    }
}

internal fun providerTestString(id: Int): String = providerMessages.getValue(id)
