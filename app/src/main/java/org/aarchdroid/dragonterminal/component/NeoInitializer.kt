package org.aarchdroid.dragonterminal.component

import android.content.Context
import org.aarchdroid.dragonterminal.component.codegen.CodeGenComponent
import org.aarchdroid.dragonterminal.component.colorscheme.ColorSchemeComponent
import org.aarchdroid.dragonterminal.component.completion.CompletionComponent
import org.aarchdroid.dragonterminal.component.config.ConfigureComponent
import org.aarchdroid.dragonterminal.component.extrakey.ExtraKeyComponent
import org.aarchdroid.dragonterminal.component.font.FontComponent
import org.aarchdroid.dragonterminal.component.pm.PackageComponent
import org.aarchdroid.dragonterminal.component.profile.ProfileComponent
import org.aarchdroid.dragonterminal.component.session.SessionComponent
import org.aarchdroid.dragonterminal.component.userscript.UserScriptComponent
import org.aarchdroid.dragonterminal.frontend.component.ComponentManager
import org.aarchdroid.dragonterminal.frontend.logging.NLog
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellProfile

/**
 * @author kiva
 */
object NeoInitializer {
    fun init(context: Context) {
        NLog.init(context)
        initComponents()
    }

    fun initComponents() {
        ComponentManager.registerComponent(ConfigureComponent::class.java)
        ComponentManager.registerComponent(CodeGenComponent::class.java)
        ComponentManager.registerComponent(ColorSchemeComponent::class.java)
        ComponentManager.registerComponent(FontComponent::class.java)
        ComponentManager.registerComponent(UserScriptComponent::class.java)
        ComponentManager.registerComponent(ExtraKeyComponent::class.java)
        ComponentManager.registerComponent(CompletionComponent::class.java)
        ComponentManager.registerComponent(PackageComponent::class.java)
        ComponentManager.registerComponent(SessionComponent::class.java)
        ComponentManager.registerComponent(ProfileComponent::class.java)

        val profileComp = ComponentManager.getComponent<ProfileComponent>()
        profileComp.registerProfile(ShellProfile.PROFILE_META_NAME, ShellProfile::class.java)
    }
}