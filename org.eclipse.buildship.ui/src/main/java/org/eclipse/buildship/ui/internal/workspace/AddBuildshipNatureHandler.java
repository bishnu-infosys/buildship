/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.eclipse.buildship.ui.internal.workspace;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.configuration.BuildConfiguration;
import org.eclipse.buildship.core.internal.configuration.GradleProjectNature;
import org.eclipse.buildship.core.internal.util.collections.AdapterFunction;
import org.eclipse.buildship.core.internal.workspace.NewProjectHandler;
import org.eclipse.buildship.core.internal.workspace.SynchronizationJob;
/**
 * Synchronizes the given projects as if the user had run the import wizard on their location.
 *
 * @author Stefan Oehme
 *
 */
public class AddBuildshipNatureHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        if (selection instanceof StructuredSelection) {
            List<?> elements = ((StructuredSelection) selection).toList();
            Set<IProject> projects = collectProjects(elements);
            Set<BuildConfiguration> buildConfigs = createBuildConfigsFor(projects);
            synchronize(buildConfigs);
        }
        return null;
    }

    private Set<IProject> collectProjects(List<?> elements) {
        Set<IProject> projects = Sets.newLinkedHashSet();
        AdapterFunction<IProject> adapterFunction = AdapterFunction.forType(IProject.class);
        for (Object element : elements) {
            IProject project = adapterFunction.apply(element);
            if (project != null && !GradleProjectNature.isPresentOn(project) && project.getLocation() != null) {
                projects.add(project);
            }
        }
        return projects;
    }

    private Set<BuildConfiguration> createBuildConfigsFor(Set<IProject> projects) {
        return projects.stream()
            .map(project -> BuildConfiguration.forRootProjectDirectory(project.getLocation().toFile()).build())
            .collect(Collectors.toSet());
    }

    private void synchronize(Set<BuildConfiguration> buildConfigs) {
        Set<org.eclipse.buildship.core.GradleBuild> gradleBuilds = buildConfigs.stream()
            .map(buildConfig -> GradleCore.getWorkspace().createBuild(buildConfig)).collect(Collectors.toSet());
        new SynchronizationJob(NewProjectHandler.IMPORT_AND_MERGE, gradleBuilds).schedule();
    }
}
