package nl.hannahsten.texifyidea.run.latex

import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import nl.hannahsten.texifyidea.file.LatexFileType
import nl.hannahsten.texifyidea.lang.magic.DefaultMagicKeys
import nl.hannahsten.texifyidea.lang.magic.allParentMagicComments
import nl.hannahsten.texifyidea.run.compiler.LatexCompiler

/**
 * @author Hannah Schellekens
 */
class LatexRunConfigurationProducer : LazyRunConfigurationProducer<LatexRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return LatexConfigurationFactory(LatexRunConfigurationType.instance)
    }

    override fun setupConfigurationFromContext(runConfiguration: LatexRunConfiguration,
                                               context: ConfigurationContext,
                                               sourceElement: Ref<PsiElement>): Boolean {
        val location = context.location ?: return false
        val container = getEntryPointContainer(location) ?: return false
        val mainFile = container.virtualFile ?: return false

        // Only activate on .tex files.
        val extension = mainFile.extension
        val texTension = LatexFileType.defaultExtension
        if (extension == null || !extension.equals(texTension, ignoreCase = true)) {
            return false
        }

        // Change the main file as given by the template run configuration to the current file
        runConfiguration.mainFile = mainFile
        runConfiguration.psiFile = container
        runConfiguration.setDefaultOutputPath()
        runConfiguration.setDefaultAuxilPath()
        runConfiguration.setSuggestedName()
        val runCommand = container.allParentMagicComments().value(DefaultMagicKeys.COMPILER)
        if (runCommand != null) {
            val compiler = runCommand.let { it.subSequence(0, it.indexOf(' ')) }.trim().toString()
            runConfiguration.compiler = LatexCompiler.byExecutableName(compiler)
            runConfiguration.compilerArguments = runCommand.removePrefix(compiler).trim()
        }
        return true
    }

    private fun getEntryPointContainer(location: Location<*>?): PsiFile? {
        if (location == null) {
            return null
        }

        val locationElement = location.psiElement
        return locationElement.containingFile
    }

    override fun isConfigurationFromContext(runConfiguration: LatexRunConfiguration,
                                            context: ConfigurationContext): Boolean {
        val mainFile = runConfiguration.mainFile
        val psiFile = context.dataContext.getData(PlatformDataKeys.PSI_FILE) ?: return false
        val currentFile = psiFile.virtualFile ?: return false
        return mainFile?.path == currentFile.path
    }
}