import base.LanguageSupport
import extensions.startsWithUppercaseLetter
import org.w3c.dom.*
import org.w3c.xhr.XMLHttpRequest
import utils.CodeModifier
import utils.GitHubUtils
import utils.SupportManager
import kotlin.browser.document
import kotlin.browser.window

var isControlActive = false;
var activeElement: HTMLSpanElement? = null
var resLink: String? = null
var isNewTab: Boolean? = null
var support: LanguageSupport? = null
var prevUrl = window.location.toString()

fun main() {

    activateSourcePilot()

    watchForUrlChange {
        println("URL changed, reactivating source pilot")
        activateSourcePilot()
    }
}

private fun watchForUrlChange(callback: () -> Unit) {
    window.setInterval({
        val newUrl = window.location.toString()
        if (newUrl != prevUrl) {
            prevUrl = newUrl
            callback()
        }
    }, 500)
}


private fun activateSourcePilot() {

    println("✈ source-pilot activated")

    val supportManager = SupportManager()
    support = supportManager.getSupportForCurrentFile()

    // Changing non span elements to span elements
    val codeTable = document.querySelector("table.highlight")

    if (codeTable != null) {

        println("Found code table ")
        CodeModifier.spanNonSpanned()
        CodeModifier.splitDotSpanned()
        CodeModifier.splitCommaSpanned()
        CodeModifier.splitOpenBraSpanner()

        // Element Mouse Over ib
        val allCodeSpan = document.querySelectorAll("table.highlight tbody tr td.blob-code > span")
        allCodeSpan.asList().forEach { _node ->
            val node = _node as HTMLSpanElement

            // Mouse over span
            node.onmouseover = {
                activeElement = node
                checkIfClickable()
            }

            // Mouse leave span
            node.onmouseleave = {
                removeUnderlineFromActiveElement()
            }

            // Mouse click
            node.onclick = {
                if (isControlActive) {
                    if (resLink != null) {
                        if (isNewTab != null && isNewTab == true) {
                            window.open(resLink!!, "_blank")
                        } else {
                            window.location.assign(resLink.toString())
                        }
                    } else {
                        val errorMessage = activeElement?.getAttribute("sp-error")
                        if (errorMessage != null) {
                            window.alert(errorMessage)
                        } else {
                            val clickedCompName = activeElement?.textContent?.trim() ?: "The component"
                            if (clickedCompName.startsWithUppercaseLetter()) {
                                window.alert("$clickedCompName is either from Android SDK or from external dependencies")
                            } else {
                                // Navigating to method definition
                                window.alert("Navigating to/with method definition will be available in up coming versions...")
                            }
                        }
                    }
                }
            }
        }

        document.onkeydown = {
            if (it.which == 17) {
                isControlActive = true
                checkIfClickable()
            }
        }

        document.onkeyup = {
            if (it.which == 17) {
                isControlActive = false
                removeUnderlineFromActiveElement()
            }
        }
    } else {
        println("Code table not found @${window.location}")
        if (GitHubUtils.isFile(window.location.toString())) {
            println("Looks like the code is not ready yet. Scheduled recheck after one second..")
            window.setTimeout({
                activateSourcePilot()
            }, 1000)
        } else {
            println("Seems like it's not a file")
        }
    }
}


fun checkIsClickable(inputText: String) {
    println("Checking if $inputText is clickable ")
    support?.getNewResourceUrl(inputText, activeElement!!) { newUrl, newTab ->
        if (newUrl != null) {
            resLink = newUrl
            isNewTab = newTab
            activeElement?.style?.textDecoration = "underline"
            activeElement?.style?.cursor = "pointer"
            doubleCheckUrl(newUrl)
        } else {
            resLink = null
            isNewTab = null
            removeUnderlineFromActiveElement()
        }
    }
}


fun doubleCheckUrl(newUrl: String) {
    val xhr = XMLHttpRequest()
    xhr.open("GET", newUrl)
    xhr.onload = {
        if (xhr.status.toInt() != 200) {
            // invalid
            resLink = null
            activeElement?.style?.textDecoration = "none"
            activeElement?.style?.cursor = ""
        }
    }
    xhr.send()
}

fun removeUnderlineFromActiveElement() {
    activeElement?.style?.textDecoration = "none"
    activeElement?.style?.cursor = ""
    activeElement = null
}

fun checkIfClickable() {
    activeElement?.let { activeElement ->
        val inputText = activeElement.innerText
        if (isControlActive) {
            checkIsClickable(inputText)
        }
    }
}