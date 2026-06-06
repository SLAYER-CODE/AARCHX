package org.aarchdroid.dragonterminal.component.codegen

import org.aarchdroid.dragonterminal.component.codegen.interfaces.CodeGenObject
import org.aarchdroid.dragonterminal.component.codegen.interfaces.CodeGenerator
import org.aarchdroid.dragonterminal.frontend.component.NeoComponent

/**
 * @author kiva
 */
class CodeGenComponent : NeoComponent {
    override fun onServiceInit() {
    }

    override fun onServiceDestroy() {
    }

    override fun onServiceObtained() {
    }

    fun newGenerator(codeObject: CodeGenObject): CodeGenerator {
        val parameter = CodeGenParameter()
        return codeObject.getCodeGenerator(parameter)
    }
}

