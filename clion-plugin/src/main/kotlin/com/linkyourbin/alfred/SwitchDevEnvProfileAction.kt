package com.linkyourbin.alfred

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages

class SwitchDevEnvProfileAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        if (project?.basePath == null) {
            Messages.showErrorDialog("Open a project first.", DevEnvProfiles.TITLE)
            return
        }

        try {
            val root = DevEnvProfiles.root(project)
            DevEnvProfiles.migrateProjectProfiles(root)
            val profiles = DevEnvProfiles.profiles()
            val profileDirs = DevEnvProfiles.profileDirs(profiles)
            if (profileDirs.isEmpty()) {
                Messages.showInfoMessage(project, "No profiles found in $profiles", DevEnvProfiles.TITLE)
                return
            }

            val names = profileDirs.map { it.fileName.toString() }.toTypedArray()
            val active = DevEnvProfiles.activeProfileName()
            val initial = active.ifBlank { names[0] }
            val selected = Messages.showEditableChooseDialog(
                "Choose a chip/dev env profile:",
                DevEnvProfiles.TITLE,
                Messages.getQuestionIcon(),
                names,
                initial,
                null as InputValidator?,
            )
            if (selected.isNullOrBlank()) {
                return
            }

            DevEnvProfiles.applyProfileAfterExit(profiles.resolve(selected), root)
            Messages.showInfoMessage(
                project,
                "Profile staged: $selected\nClose and reopen CLion to apply all saved project and IDE settings.",
                DevEnvProfiles.TITLE,
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message ?: e.toString(), DevEnvProfiles.TITLE)
        }
    }
}
