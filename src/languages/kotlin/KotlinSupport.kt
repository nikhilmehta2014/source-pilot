package languages

import base.LanguageSupport
import core.BaseFeature
import extensions.startsWithUppercaseLetter
import languages.kotlin.features.LayoutResFeature
import languages.kotlin.features.StringResFeature
import org.w3c.dom.*
import utils.CommonParser
import utils.KotlinLineFinder
import utils.KotlinParser
import utils.XMLLineFinder
import kotlin.browser.document
import kotlin.browser.window

/**
 * To support .kt (kotlin) files
 * This class will be extended by JavaSupport. All 'open' methods will be overriden in `JavaSupport`
 */
open class KotlinSupport : LanguageSupport() {

    companion object {
        private const val DATA_BINDING_IMPORT_REGEX = ".*\\.databinding\\..+Binding"
        protected const val LAYOUT_PREFIX = ".layout."
        protected const val STRING_PREFIX = ".string."
        protected const val MENU_PREFIX = ".menu."
    }

    private var fileUrl: String? = null

    override fun getFeatures(): List<BaseFeature> {
        return listOf(
                LayoutResFeature(),
                StringResFeature()
        )
    }

    /**
     * To hold imports in current kotlin file
     */
    private val imports by lazy { KotlinParser.parseImports(getFullCode()) }

    override fun getNewResourceUrl(inputText: String, htmlSpanElement: HTMLSpanElement, callback: (url: String?, isNewTab: Boolean) -> Unit) {

        if (!isKotlinDataType(inputText)) {

            for (feature in getFeatures()) {
                if (feature.isMatch(inputText, htmlSpanElement)) {
                    feature.handle(inputText, htmlSpanElement, callback)
                    return
                }
            }


            if (isMenuRes(htmlSpanElement)) {
                println("Generating new url for menu : $inputText")
                val menuFileName = getMenuFileName(inputText)
                val currentUrl = window.location.toString()
                val newUrl = getMenuUrl(currentUrl, menuFileName)
                callback(newUrl, true)
            } else if (isImportStatement(htmlSpanElement)) {
                println("Clicked on an import statement")

                val currentPackageName = KotlinParser.getCurrentPackageName(getFullCode())
                val importStatement = htmlSpanElement.parentElement?.textContent!!

                val isDir: Boolean
                val importPackage = if (isClickedOnEndClass(htmlSpanElement)) {
                    isDir = false
                    KotlinParser.parseImportPackage(importStatement)
                } else {
                    // directory navigation
                    isDir = true
                    getDirectoryPackage(htmlSpanElement).trim()
                }

                if (isClickableImport(importPackage)) {
                    gotoImport(currentPackageName, importPackage, isDir, callback)
                } else {
                    callback(null, false)
                }

            } else if (isInternalMethodCall(htmlSpanElement)) {
                println("Internal method call..")

                // internal method call
                val methodName = inputText.trim()
                val lineNumbers = getMethodDefinitionLineNumber(methodName)
                if (lineNumbers.isNotEmpty()) {
                    if (lineNumbers.size == 1) {
                        val lineNumber = lineNumbers.first()
                        goto(lineNumber, callback)
                    } else {
                        // multiple method definitions
                        callback(null, false)
                        htmlSpanElement.setAttribute("sp-error", "Selected method has multiple definitions")
                    }

                } else {
                    callback(null, false)
                }
            } else if (isVariable(htmlSpanElement)) {
                println("It's a variable")
                val assignLineNumber = getAssignedLineNumber(inputText)
                goto(assignLineNumber, callback)
            } else if (KotlinParser.isExternalMethodCall(inputText, htmlSpanElement)) {
                println("$inputText is an external method call")
                val variableName = getVariableName(htmlSpanElement)
                println("Variable name is $variableName")
                if (variableName != null) {
                    val variableType = getVariableType(variableName)
                    if (isClassName(variableType)) {
                        println("Class name is $variableType")
                        gotoClass(variableType!!, htmlSpanElement, callback)

                        // Getting line number
                        // TODO : Get line number heres
                        if (fileUrl != null) {
                            KotlinLineFinder.getLineNumber(fileUrl!!, getFunRegEx(inputText.replace(".", ""))) { lineNumber ->
                                val newUrl = "${fileUrl!!.replace("#L.+".toRegex(), "")}#L$lineNumber"
                                callback(newUrl, true)
                            }
                        }
                    } else {
                        println("Non class variable types, such as method calls will supported in future")
                        callback(null, false)
                    }
                } else {
                    callback(null, false)
                }
            } else if (imports.isNotEmpty()) {
                gotoClass(inputText, htmlSpanElement, callback)
            } else {
                println("No match found")
                callback(null, true)
            }


        } else {
            // it was a kotlin data type
            callback(null, true)
        }
    }

    private fun getMenuUrl(currentUrl: String, menuFileName: String?): String {
        println("MENU: menuFileName : $menuFileName")
        println("MENU: currentUrl : $currentUrl")
        val lastMainIndex = currentUrl.indexOf("/main/")
        return currentUrl.substring(0, lastMainIndex) + "/main/res/menu/$menuFileName.xml"
    }

    open fun getMenuFileName(inputText: String): String? {
        return CommonParser.parseMenuFileName(inputText)
    }

    private fun getFunRegEx(methodName: String): String {
        return "fun\\s+$methodName\\s*\\("
    }

    private fun getVariableName(htmlSpanElement: HTMLSpanElement): String? {
        return getPreviousNonSpaceSiblingElement(htmlSpanElement)?.textContent
    }

    private fun gotoClass(inputText: String, htmlSpanElement: HTMLSpanElement, callback: (url: String?, isNewTab: Boolean) -> Unit) {

        val currentPackageName = KotlinParser.getCurrentPackageName(getFullCode())

        // Getting possible import statements for the class
        val matchingImport = getMatchingImport(inputText, currentPackageName, htmlSpanElement)

        when {

            isInnerInterfaceOrClass(inputText) -> {
                val lineNumber = getLineNumber(getContentRegEx(inputText))
                goto(lineNumber, callback)
            }

            isClickableImport(matchingImport) -> {
                gotoImport(currentPackageName, matchingImport, false, callback)
            }

            else -> {
                println("No import matched! Matching importing was : $matchingImport")
                callback(null, true)
            }
        }
    }

    private fun isVariable(htmlSpanElement: HTMLSpanElement): Boolean {
        return htmlSpanElement.className != "pl-en" && htmlSpanElement.textContent?.matches("\\w+") ?: false
                && getNextNonSpaceSiblingElement(htmlSpanElement)?.textContent?.startsWith(".") ?: false
    }

    protected fun getNextNonSpaceSiblingElement(htmlSpanElement: HTMLElement): Element? {
        var x = htmlSpanElement.nextElementSibling
        while (x != null) {
            if (x.textContent?.isNotBlank() == true) {
                return x
            }
            x = x.nextElementSibling
        }
        return null
    }

    private fun getPreviousNonSpaceSiblingElement(htmlSpanElement: Element): Element? {
        var x = htmlSpanElement.previousElementSibling
        while (x != null) {
            if (x.textContent?.isNotBlank() == true) {
                return x
            }
            x = x.previousElementSibling
        }
        return null
    }

    private fun getAssignedLineNumber(variableName: String?): Int {
        val allTd = document.querySelectorAll("table.highlight tbody tr td.blob-code")
        val matchRegEx = KotlinParser.getAssignedPattern(variableName)
        println("RegEx is $matchRegEx")
        for (i in 0 until allTd.length) {
            val td = allTd[i] as Element
            val line = td.textContent
            if (line != null && line.matches(matchRegEx)) {
                return td.id.replace("LC", "").toInt()
            }
        }
        return -1
    }

    private fun getVariableType(variableName: String?): String? {
        return KotlinParser.getAssignedFrom(getFullCode(), variableName)
    }

    private fun isAssignedViaMethod(assignedFrom: String): Boolean {
        return assignedFrom.matches("(?<variableName>\\w+)\\s*.\\s*(?<methodName>\\w+)")
    }

    private fun isClassName(assignedFrom: String?): Boolean {
        return assignedFrom?.matches("\\w+") ?: false
    }

    /**
     * To get matching import for the input passed from the imports in the file.
     */
    private fun getMatchingImport(_inputText: String, currentPackageName: String, htmlSpanElement: HTMLSpanElement): String? {

        // Removing '?' from inputText. For eg: Bundle? -> Bundle
        val inputText = _inputText.replace("?", "")

        val matchingImports = imports.filter { it.endsWith(".$inputText") }
        println("Matching imports are : $matchingImports")
        return if (matchingImports.isNotEmpty()) {
            matchingImports.first()
        } else {
            println("No import matched for $inputText, setting current name : $currentPackageName")
            if (inputText.startsWithUppercaseLetter()) {
                "$currentPackageName.$inputText"
            } else {
                // Checking if it's the import statement it self
                println("Checking if it's import statement")
                val isImportStatement = htmlSpanElement.parentElement?.textContent.equals(getImportStatement(inputText))
                if (isImportStatement) {
                    inputText
                } else {
                    null
                }
            }
        }
    }

    protected open fun getImportStatement(importStatement: String): String {
        return "import $importStatement"
    }

    private fun getDirectoryPackage(htmlSpanElement: HTMLSpanElement): String {
        var s = ""
        var x: Element? = htmlSpanElement
        while (x != null) {
            val text = x.textContent
            if (text != null && text.trim() != "import") {
                s = "$text$s"
            }
            x = x.previousElementSibling
        }
        return s
    }

    open fun isClickedOnEndClass(htmlSpanElement: HTMLSpanElement): Boolean {
        return htmlSpanElement.nextElementSibling == null
    }

    private fun gotoImport(currentPackageName: String, matchingImport: String?, isDir: Boolean, callback: (url: String?, isNewTab: Boolean) -> Unit, lineNumber: Int = 1) {
        val currentUrl = window.location.toString()
        val curFileExt = CommonParser.parseFileExt(currentUrl)
        val packageSlash = '/' + currentPackageName.replace('.', '/');
        val windowLocSplit = currentUrl.split(packageSlash)
        val fileExt = if (isDir) {
            ""
        } else {
            ".$curFileExt#L$lineNumber"
        }
        // Returning new url
        this.fileUrl = "${windowLocSplit[0]}/${matchingImport!!.replace('.', '/')}$fileExt"
        println("GEN URL is $fileUrl -> isDir : $isDir ")
        callback(fileUrl, true)
    }

    open fun isImportStatement(htmlSpanElement: HTMLSpanElement): Boolean {
        val fullLine = htmlSpanElement.parentElement?.textContent ?: ""
        println("IMPORT: full import line is $fullLine")
        return KotlinParser.IMPORT_PATTERN.matches(fullLine)
    }

    private fun goto(lineNumber: Int, callback: (url: String?, isNewTab: Boolean) -> Unit) {
        if (lineNumber > 0) {
            var currentUrl = window.location.toString()
            if (CommonParser.hasLineNumber(currentUrl)) {
                currentUrl = CommonParser.parseUrlOnly(currentUrl)
            }

            callback("$currentUrl#L$lineNumber", false)
        } else {
            callback(null, false)
        }
    }

    private fun isInnerInterfaceOrClass(inputText: String): Boolean {
        val fullCode = getFullCode()
        return fullCode.matches(getContentRegEx(inputText))
    }

    private fun isInternalMethodCall(htmlSpanElement: HTMLSpanElement): Boolean {
        return htmlSpanElement.nextElementSibling?.textContent?.startsWith("(") ?: false
                && htmlSpanElement.className != "pl-en"
                && htmlSpanElement.previousElementSibling?.textContent?.isBlank() ?: true
    }

    open fun isMenuRes(htmlSpanElement: HTMLSpanElement): Boolean {
        return htmlSpanElement.previousElementSibling?.textContent.equals(".menu")
    }

    open fun isStringRes(htmlSpanElement: HTMLSpanElement): Boolean {
        return htmlSpanElement.previousElementSibling?.textContent.equals(".string")
    }

    open fun isLayoutName(htmlSpanElement: HTMLSpanElement): Boolean {
        return htmlSpanElement.previousElementSibling?.textContent.equals(".layout")
    }

    private fun getSupportableElementFromReverse(_htmlSpanElement: HTMLSpanElement): List<String> {
        val arr = mutableListOf<String>()
        var endNode = _htmlSpanElement as Element?
        while (endNode != null) {
            arr.add(endNode.textContent!!)
            endNode = endNode.previousElementSibling
        }

        val x = mutableListOf<String>()
        for (arrElement in arr.withIndex()) {
            val subList = arr.subList(0, arrElement.index).reversed()
            val newElement = subList.joinToString(separator = "")
            if (newElement.isNotBlank()) {
                x.add(newElement)
            }
        }
        return x
    }

    private fun getMethodDefinitionLineNumber(methodName: String): List<Int> {
        val lineNumbers = mutableListOf<Int>()
        val tdBlobCodes = document.querySelectorAll("table.highlight tbody tr td.blob-code")
        for (tdIndex in 0 until tdBlobCodes.length) {
            val td = tdBlobCodes[tdIndex] as Element
            val codeLine = td.textContent
            if (codeLine != null && codeLine.trim().isNotEmpty()) {
                val regex = getMethodRegEx(methodName)
                val isMatch = codeLine.matches(regex)
                if (isMatch) {
                    val lineNumber = td.id.replace("LC", "").toInt()
                    lineNumbers.add(lineNumber)
                }
            }
        }
        return lineNumbers
    }

    private fun getLineNumber(regex: String): Int {
        val tdBlobCodes = document.querySelectorAll("table.highlight tbody tr td.blob-code")
        for (tdIndex in 0 until tdBlobCodes.length) {
            val td = tdBlobCodes[tdIndex] as Element
            val codeLine = td.textContent
            if (codeLine != null && codeLine.trim().isNotEmpty()) {
                val isMatch = codeLine.matches(regex)
                if (isMatch) {
                    return td.id.replace("LC", "").toInt()
                }
            }
        }
        return -1
    }

    private fun getContentRegEx(inputText: String): String {
        return "(?:interface|class)\\s*$inputText\\s*[{(]"
    }

    open fun getMethodRegEx(methodName: String): String {
        return "fun\\s*$methodName\\s*\\("
    }

    private fun isKotlinDataType(_inputText: String): Boolean {
        return when (_inputText.replace("?", "")) {
            "Boolean",
            "Long",
            "Float",
            "Double",
            "Char",
            "Int",
            "String" -> true
            else -> false
        }
    }

    /**
     * To check if the passed import passes all non matching conditions
     */
    private fun isClickableImport(matchingImport: String?): Boolean {
        return matchingImport != null &&
                !matchingImport.startsWith("android.") &&
                !matchingImport.startsWith("java.") &&
                !matchingImport.startsWith("androidx.") &&
                !matchingImport.startsWith("kotlinx.android.synthetic.") &&
                !matchingImport.startsWith("com.google.android.material.") &&
                !isDataBindingImport(matchingImport)
    }

    private fun isDataBindingImport(matchingImport: String): Boolean {
        return matchingImport.matches(DATA_BINDING_IMPORT_REGEX)
    }

    override fun getFileExtension(): String {
        return "kt"
    }


}