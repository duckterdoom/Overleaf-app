package com.overleaf.client.ui.backend;

import java.util.List;
import java.nio.file.Path;

/**
 * UI seam for the project list shown on the home dashboard.
 */
public interface ProjectGateway {

    /** {@code icon} and {@code color} are the user's picks from the New project dialog; null on older projects. */
    record ProjectSummary(String name, String folder, String modified, int files, boolean shared,
                          String icon, String color) {

        public ProjectSummary(String name, String folder, String modified, int files, boolean shared) {
            this(name, folder, modified, files, shared, null, null);
        }
    }

    List<ProjectSummary> projects();

    default ProjectSummary create(String name) {
        return create(name, null, null);
    }

    /** Creates the project and remembers its icon id and {@code #RRGGBB} color (either may be null). */
    ProjectSummary create(String name, String icon, String color);

    /** Renames the project folder; throws {@link IllegalStateException} with a user-facing message. */
    ProjectSummary rename(ProjectSummary project, String newName);

    /** Removes the project from the list (implementations should keep a recoverable copy). */
    void delete(ProjectSummary project);

    /** Folder a new project with this name would be created in, for display. */
    String locationFor(String name);

    Path projectsDirectory();
    void setProjectsDirectory(Path directory);
    ProjectSummary create(NewProjectRequest request);
    ProjectSummary move(ProjectSummary project, Path newParent);
    String affiliation();
    void setAffiliation(String value);
    default String preferredProjectFolder() { return null; }
}
