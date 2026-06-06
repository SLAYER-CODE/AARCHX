package org.aarchdroid.dragonterminal.component.codegen.generators

import org.aarchdroid.dragonterminal.component.codegen.CodeGenParameter
import org.aarchdroid.dragonterminal.component.codegen.interfaces.CodeGenObject
import org.aarchdroid.dragonterminal.component.codegen.interfaces.CodeGenerator

/**
 * @author kiva
 */
class NeoProfileGenerator(parameter: CodeGenParameter) : CodeGenerator(parameter) {
    override fun getGeneratorName(): String {
        return "NeoProfile-Generator"
    }

    override fun generateCode(codeGenObject: CodeGenObject): String {
        return ""
    }
}