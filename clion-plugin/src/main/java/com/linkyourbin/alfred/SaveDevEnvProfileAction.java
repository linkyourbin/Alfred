package com.linkyourbin.alfred;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.nio.file.Path;

public final class SaveDevEnvProfileAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null || project.getBasePath() == null) {
            Messages.showErrorDialog("Open a project first.", DevEnvProfiles.TITLE);
            return;
        }

        String name = Messages.showInputDialog(
                project,
                "Profile name, for example stm32f4-debug or esp32s3-release:",
                DevEnvProfiles.TITLE,
                Messages.getQuestionIcon()
        );
        if (name == null || name.isBlank()) {
            return;
        }

        try {
            Path root = DevEnvProfiles.root(project);
            DevEnvProfiles.migrateProjectProfiles(root);
            DevEnvProfiles.saveProfile(root, name);
            Messages.showInfoMessage(project, "Saved profile under " + DevEnvProfiles.profiles(), DevEnvProfiles.TITLE);
        } catch (Exception e) {
            Messages.showErrorDialog(project, e.getMessage(), DevEnvProfiles.TITLE);
        }
    }
}
