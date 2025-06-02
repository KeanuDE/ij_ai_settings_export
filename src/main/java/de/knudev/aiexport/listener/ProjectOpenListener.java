
package de.knudev.aiexport.listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import de.knudev.aiexport.action.ImportSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Listener that automatically imports AI instructions when a project is opened
 */
public class ProjectOpenListener implements ProjectManagerListener {

    @Override
    public void projectOpened(@NotNull Project project) {
        // Create an instance of ImportSettings
        ImportSettings importSettings = new ImportSettings();

        // Call the import functionality
        importSettings.importOnProjectOpen(project);
    }
}
