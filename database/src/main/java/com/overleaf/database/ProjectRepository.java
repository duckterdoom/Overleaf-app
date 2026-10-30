package com.overleaf.database;

import com.overleaf.common.model.Project;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * M5: Database - Repository Pattern cho đối tượng Dự án (Project).
 */
public class ProjectRepository {
    private final Map<String, Project> memoryStorage = new ConcurrentHashMap<>();

    public ProjectRepository() {
        Project defaultProject = new Project("proj_001", "Research_Paper_2026", "admin");
        defaultProject.addCollaborator("alice");
        defaultProject.addCollaborator("bob");
        save(defaultProject);
    }

    public Optional<Project> findById(String id) {
        return Optional.ofNullable(memoryStorage.get(id));
    }

    public List<Project> findByOwner(String ownerUsername) {
        return memoryStorage.values().stream()
                .filter(p -> p.getOwnerUsername().equalsIgnoreCase(ownerUsername))
                .collect(Collectors.toList());
    }

    public List<Project> findAccessibleProjects(String username) {
        return memoryStorage.values().stream()
                .filter(p -> p.getOwnerUsername().equalsIgnoreCase(username) || 
                             p.getCollaborators().contains(username))
                .collect(Collectors.toList());
    }

    public Project save(Project project) {
        if (project.getId() == null || project.getId().isEmpty()) {
            project.setId("proj_" + UUID.randomUUID().toString().substring(0, 8));
        }
        project.setLastModified(new Date());
        memoryStorage.put(project.getId(), project);
        return project;
    }

    public boolean delete(String id) {
        return memoryStorage.remove(id) != null;
    }

    public List<Project> findAll() {
        return new ArrayList<>(memoryStorage.values());
    }
}
