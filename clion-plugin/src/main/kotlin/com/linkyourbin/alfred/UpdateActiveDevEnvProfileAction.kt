package com.linkyourbin.alfred

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages

class UpdateActiveDevEnvProfileAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        if (project?.basePath == null) {
            Messages.showErrorDialog("Open a project first.", DevEnvProfiles.TITLE)
            return
        }

        try {
            val root = DevEnvProfiles.root(project)
            DevEnvProfiles.migrateProjectProfiles(root)
            var active = DevEnvProfiles.activeProfileName()
            if (active.isBlank()) {
                val profileDirs = DevEnvProfiles.profileDirs(DevEnvProfiles.profiles())
                if (profileDirs.isEmpty()) {
                    Messages.showInfoMessage(project, "No profiles found in ${DevEnvProfiles.profiles()}", DevEnvProfiles.TITLE)
                    return
                }

                val names = profileDirs.map { it.fileName.toString() }.toTypedArray()
                val selected = Messages.showEditableChooseDialog(
                    "Choose profile to update:",
                    DevEnvProfiles.TITLE,
                    Messages.getQuestionIcon(),
                    names,
                    names[0],
                    null as InputValidator?,
                )
                if (selected.isNullOrBlank()) {
                    return
                }
                active = selected
                DevEnvProfiles.saveProfile(root, active)
            } else {
                DevEnvProfiles.updateActiveProfile(root)
            }
            Messages.showInfoMessage(project, "Updated active profile: $active", DevEnvProfiles.TITLE)
        } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message ?: e.toString(), DevEnvProfiles.TITLE)
        }
    }
}
