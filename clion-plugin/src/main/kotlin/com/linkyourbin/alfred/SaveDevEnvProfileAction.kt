package com.linkyourbin.alfred

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class SaveDevEnvProfileAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        if (project?.basePath == null) {
            Messages.showErrorDialog("Open a project first.", DevEnvProfiles.TITLE)
            return
        }

        val name = Messages.showInputDialog(
            project,
            "Profile name, for example stm32f4-debug or esp32s3-release:",
            DevEnvProfiles.TITLE,
            Messages.getQuestionIcon(),
        )
        if (name.isNullOrBlank()) {
            return
        }

        try {
            val root = DevEnvProfiles.root(project)
            DevEnvProfiles.migrateProjectProfiles(root)
            DevEnvProfiles.saveProfile(root, name)
            Messages.showInfoMessage(project, "Saved profile under ${DevEnvProfiles.profiles()}", DevEnvProfiles.TITLE)
        } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message ?: e.toString(), DevEnvProfiles.TITLE)
        }
    }
}
