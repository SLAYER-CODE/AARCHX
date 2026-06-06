package org.aarchdroid.dragonterminal.component.codegen.interfaces

import org.aarchdroid.dragonterminal.component.codegen.CodeGenParameter

/**
 * @author kiva
 */
interface CodeGenObject {
    fun getCodeGenerator(parameter: CodeGenParameter): CodeGenerator
}