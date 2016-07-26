package com.github.jparound30.idea.plugin.stepcounter.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.ProjectComponent

/**
 * Created by tabukinobuhiro on 2016/07/25.
 */
class StepCounterProjectComponent : ProjectComponent {
    companion object {
        val PROJECT_COMPONENT_NAME = "StepCounterProjectComponent"
    }
    override fun getComponentName(): String {
        return PROJECT_COMPONENT_NAME
    }

    override fun projectOpened() {
        System.out.println("projectOpened")
    }

    override fun projectClosed() {
        System.out.println("projectClosed")
    }

    override fun initComponent() {
        System.out.println("initComponent")

        val am = ActionManager.getInstance()
        val action = StepCountAction()

        if (am.getAction("StepCount.StepCount") == null) {
            // Passes an instance of your custom TextBoxes class to the registerAction method of the ActionManager class.
            am.registerAction("StepCount.StepCount", action)

            // Gets an instance of the WindowMenu action group.
            val VcsLogContextMenu = am.getAction("Vcs.Log.ContextMenu") as DefaultActionGroup

            // Adds a separator and a new menu command to the WindowMenu group on the main menu.
            VcsLogContextMenu.add(Separator.getInstance(), Constraints.FIRST)
            VcsLogContextMenu.add(action, Constraints.FIRST)
        }
    }

    override fun disposeComponent() {
        System.out.println("disposeComponent")
    }

}