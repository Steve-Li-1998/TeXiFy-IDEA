package nl.hannahsten.texifyidea.util.labels

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.rd.util.first
import nl.hannahsten.texifyidea.lang.CommandManager
import nl.hannahsten.texifyidea.lang.commands.LatexGenericRegularCommand
import nl.hannahsten.texifyidea.psi.*
import nl.hannahsten.texifyidea.util.files.commandsInFileSet
import nl.hannahsten.texifyidea.util.firstChildOfType
import nl.hannahsten.texifyidea.util.identifier
import nl.hannahsten.texifyidea.util.magic.CommandMagic
import nl.hannahsten.texifyidea.util.magic.EnvironmentMagic
import nl.hannahsten.texifyidea.util.requiredParameter


/**
 * Extracts the label element (so the element that should be resolved to) from the PsiElement given that the PsiElement represents a label.
 */
fun PsiElement.extractLabelElement(): PsiElement? {
    fun getLabelParameterText(command: LatexCommandWithParams): LatexParameterText {
        val optionalParameters = command.optionalParameterMap
        val labelEntry = optionalParameters.filter { pair -> pair.key.toString() == "label" }.first()
        val contentList = labelEntry.value.keyvalContentList
        return contentList.firstOrNull { c -> c.parameterText != null }?.parameterText
            ?: contentList.first { c -> c.parameterGroup != null }.parameterGroup!!.parameterGroupText!!.parameterTextList.first()
    }

    return when (this) {
        is BibtexEntry -> firstChildOfType(BibtexId::class)
        is LatexCommands -> {
            if (CommandMagic.labelAsParameter.contains(name)) {
                return getLabelParameterText(this)
            }
            else {
                // For now just take the first label name (may be multiple for user defined commands)
                val info = CommandManager.labelAliasesInfo.getOrDefault(name, null)
                val position = info?.positions?.firstOrNull() ?: 0

                // Skip optional parameters for now
                this.parameterList.mapNotNull { it.requiredParam }.getOrNull(position)
                    ?.firstChildOfType(LatexParameterText::class)
            }
        }
        is LatexEnvironment -> {
            if (EnvironmentMagic.labelAsParameter.contains(environmentName)) {
                getLabelParameterText(beginCommand)
            }
            else {
                null
            }
        }
        else -> null
    }
}

/**
 * Extracts the label name from the PsiElement given that the PsiElement represents a label.
 *
 * @param referencingFileSet Any file in the fileset containing the element referencing [this], which can make a different for what the label name should be, as using the xr package labels from other filesets can be included with a prefix.
 */
fun PsiElement.extractLabelName(referencingFileSet: PsiFile? = null): String {
    return when (this) {
        is BibtexEntry -> identifier() ?: ""
        is LatexCommands -> {
            if (CommandMagic.labelAsParameter.contains(name)) {
                optionalParameterMap.toStringMap()["label"]!!
            }
            else {
                // For now just take the first label name (which may be multiple for user defined commands)
                val info = CommandManager.labelAliasesInfo.getOrDefault(name, null)
                val position = info?.positions?.firstOrNull() ?: 0
                var prefix = info?.prefix ?: ""

                // Check if there is any prefix given by the xr package
                // todo something wrong here, no commands in file set?
                if (referencingFileSet != null) {
                    referencingFileSet.commandsInFileSet()
                        // Don't think there can be multiple, at least I wouldn't know what prefix it would use
                        .firstOrNull { it.name == LatexGenericRegularCommand.EXTERNALDOCUMENT.commandWithSlash }
                        ?.parameterList
                        ?.mapNotNull { it.optionalParam }
                        // Assume it's the first optional parameter if there is one
                        ?.firstOrNull()
                        ?.text?.trim('[', ']')
                        ?.let { prefix = it }
                }

                // Skip optional parameters for now (also below and in
                prefix + this.requiredParameter(position)
            }
        }
        is LatexEnvironment -> this.label ?: ""
        else -> text
    }
}