package com.linkyourbin.alfred;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.nio.file.Path;
import java.util.List;

public final class UpdateActiveDevEnvProfileAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null || project.getBasePath() == null) {
            Messages.showErrorDialog("Open a project first.", DevEnvProfiles.TITLE);
            return;
        }

        try {
            Path root = DevEnvProfiles.root(project);
            DevEnvProfiles.migrateProjectProfiles(root);
            String active = DevEnvProfiles.activeProfileName();
            if (active.isBlank()) {
                List<Path> profileDirs = DevEnvProfiles.profileDirs(DevEnvProfiles.profiles());
                if (profileDirs.isEmpty()) {
                    Messages.showInfoMessage(project, "No profiles found in " + DevEnvProfiles.profiles(), DevEnvProfiles.TITLE);
                    return;
                }

                String[] names = profileDirs.stream().map(path -> path.getFileName().toString()).toArray(String[]::new);
                active = Messages.showEditableChooseDialog(
                        "Choose profile to update:",
                        DevEnvProfiles.TITLE,
                        Messages.getQuestionIcon(),
                        names,
                        names[0],
                        null
                );
                if (active == null || active.isBlank()) {
                    return;
                }
                DevEnvProfiles.saveProfile(root, active);
            } else {
                DevEnvProfiles.updateActiveProfile(root);
            }
            Messages.showInfoMessage(project, "Updated active profile: " + active, DevEnvProfiles.TITLE);
        } catch (Exception e) {
            Messages.showErrorDialog(project, e.getMessage(), DevEnvProfiles.TITLE);
        }
    }
}
