package com.linkyourbin.alfred;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.nio.file.Path;
import java.util.List;

public final class SwitchDevEnvProfileAction extends AnAction {
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
            Path profiles = DevEnvProfiles.profiles();
            List<Path> profileDirs = DevEnvProfiles.profileDirs(profiles);
            if (profileDirs.isEmpty()) {
                Messages.showInfoMessage(project, "No profiles found in " + profiles, DevEnvProfiles.TITLE);
                return;
            }

            String[] names = profileDirs.stream().map(path -> path.getFileName().toString()).toArray(String[]::new);
            String active = DevEnvProfiles.activeProfileName();
            String initial = active.isBlank() ? names[0] : active;
            String selected = Messages.showEditableChooseDialog(
                    "Choose a chip/dev env profile:",
                    DevEnvProfiles.TITLE,
                    Messages.getQuestionIcon(),
                    names,
                    initial,
                    null
            );
            if (selected == null || selected.isBlank()) {
                return;
            }

            DevEnvProfiles.applyProfileAfterExit(profiles.resolve(selected), root);
            Messages.showInfoMessage(
                    project,
                    "Profile staged: " + selected + "\nClose and reopen CLion to apply all saved project and IDE settings.",
                    DevEnvProfiles.TITLE
            );
        } catch (Exception e) {
            Messages.showErrorDialog(project, e.getMessage(), DevEnvProfiles.TITLE);
        }
    }
}
