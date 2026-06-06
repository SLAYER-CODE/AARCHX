package org.aarchdroid.dragonterminal.component.codegen.interfaces

import org.aarchdroid.dragonterminal.component.codegen.CodeGenParameter

/**
 * @author kiva
 */
abstract class CodeGenerator(parameter: CodeGenParameter) {
    abstract fun getGeneratorName(): String

    abstract fun generateCode(codeGenObject: CodeGenObject): String
}
