package org.aarchdroid.dragonterminal.frontend.component

/**
 * @author kiva
 */
class ComponentDuplicateException(serviceName: String) : RuntimeException("Service $serviceName duplicate")