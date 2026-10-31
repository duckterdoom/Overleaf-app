package com.overleaf.client.ui.backend;

import java.util.List;

/**
 * UI seam for the project list shown on the home dashboard.
 */
public interface ProjectGateway {

    record ProjectSummary(String name, String folder, String modified, int files, boolean shared) {
    }

    List<ProjectSummary> projects();

    ProjectSummary create(String name);

    /** Renames the project folder; throws {@link IllegalStateException} with a user-facing message. */
    ProjectSummary rename(ProjectSummary project, String newName);

    /** Removes the project from the list (implementations should keep a recoverable copy). */
    void delete(ProjectSummary project);

    /** Folder a new project with this name would be created in, for display. */
    String locationFor(String name);
}
