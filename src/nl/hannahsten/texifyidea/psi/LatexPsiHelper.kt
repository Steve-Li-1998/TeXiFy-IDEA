package nl.hannahsten.texifyidea.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.tree.LeafPsiElement
import nl.hannahsten.texifyidea.LatexLanguage
import nl.hannahsten.texifyidea.psi.LatexTypes.CLOSE_BRACKET
import nl.hannahsten.texifyidea.util.childrenOfType
import nl.hannahsten.texifyidea.util.findFirstChild
import nl.hannahsten.texifyidea.util.firstChildOfType

class LatexPsiHelper(private val project: Project) {

    private fun createEnvironmentContent(): LatexEnvironmentContent {
        val environment = createFromText(
            "\\begin{figure}\n" +
                "        Placeholder\n" +
                "    \\end{figure}"
        ).firstChildOfType(LatexEnvironment::class)!!
        environment.environmentContent!!.firstChild.delete()
        return environment.environmentContent!!
    }

    private fun createLatexOptionalParam(): LatexParameter {
        return createFromText("\\usepackage[]{package}")
            .findFirstChild { c -> c is LatexParameter && c.optionalParam != null }!!
    }

    /**
     * Create a label command \label{labelName}.
     */
    fun createLabelCommand(labelName: String): PsiElement {
        val labelText = "\\label{$labelName}"
        val fileFromText = createFromText(labelText)
        return fileFromText.firstChild
    }

    private fun createKeyValuePairs(parameter: String): LatexKeyvalPair {
        val commandText = "\\begin{lstlisting}[$parameter]"
        val environment = createFromText(commandText).firstChildOfType(LatexEnvironment::class)!!
        val optionalParam = environment.beginCommand.firstChildOfType(LatexOptionalParam::class)!!
        return optionalParam.keyvalPairList[0]
    }

    fun createFromText(text: String): PsiElement =
        PsiFileFactory.getInstance(project).createFileFromText("DUMMY.tex", LatexLanguage.INSTANCE, text, false, true)

    /**
     * Adds the supplied element to the content of the environment.
     * @param environment The environment whose content should be manipulated
     * @param element The element to be inserted
     * @param after If specified, the new element will be inserted after this element
     * @return The new element in the PSI tree. Note that this element is *not* necessarily equal
     * to the supplied element. For example, the new element might have an updated endOffset
     */
    fun addToContent(environment: LatexEnvironment, element: PsiElement, after: PsiElement? = null): PsiElement {
        if (environment.environmentContent == null) {
            environment.addAfter(createEnvironmentContent(), environment.beginCommand)
        }
        val environmentContent = environment.environmentContent!!

        return if (after != null) {
            environmentContent.addAfter(element, after)
        }
        else {
            environmentContent.add(element)
        }
    }

    fun createRequiredParameter(content: String): LatexRequiredParam {
        val commandText = "\\label{$content}"
        return createFromText(commandText).firstChildOfType(LatexRequiredParam::class)!!
    }

    fun setOptionalParameter(command: LatexCommandWithParams, name: String, value: String?): PsiElement {
        val existingParameters = command.optionalParameterMap
        if (existingParameters.isEmpty()) {
            command.addAfter(createLatexOptionalParam(), command.parameterList[0])
        }

        val optionalParam = command.parameterList
            .first { p -> p.optionalParam != null }.optionalParam!!

        val parameterText = if (value != null) {
            "$name=$value"
        }
        else {
            name
        }

        val pair = createKeyValuePairs(parameterText)
        val closeBracket = optionalParam.childrenOfType<LeafPsiElement>().first { it.elementType == CLOSE_BRACKET }
        return if (optionalParam.keyvalPairList.isNotEmpty()) {
            val existing = optionalParam.keyvalPairList.find { kv -> kv.keyvalKey.text == name }
            if (existing != null && value != null) {
                existing.keyvalValue?.delete()
                existing.addAfter(pair.keyvalValue!!, existing.childrenOfType<LeafPsiElement>().first { it.text == "=" })
                existing
            }
            else {
                optionalParam.addBefore(createFromText(","), closeBracket)
                optionalParam.addBefore(pair, closeBracket)
                closeBracket.prevSibling
            }
        }
        else {
            optionalParam.addBefore(pair, closeBracket)
            closeBracket.prevSibling
        }
    }
}