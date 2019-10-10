package languages.kotlin.features

import base.LanguageSupport
import core.BaseFeature
import org.w3c.dom.HTMLSpanElement
import utils.CommonParser
import kotlin.browser.window

class LayoutResFeature : BaseFeature {

    override fun isMatch(inputText: String, htmlSpanElement: HTMLSpanElement): Boolean {
        return htmlSpanElement.previousElementSibling?.textContent.equals(".layout")
    }

    override fun handle(languageSupport: LanguageSupport, inputText: String, htmlSpanElement: HTMLSpanElement, callback: (url: String?, isNewTab: Boolean) -> Unit) {
        val layoutFileName = CommonParser.parseLayoutFileName(inputText)
        val currentUrl = window.location.toString()
        val newUrl = "${currentUrl.split("main")[0]}main/res/layout/$layoutFileName.xml"
        callback(newUrl, true)
    }
}