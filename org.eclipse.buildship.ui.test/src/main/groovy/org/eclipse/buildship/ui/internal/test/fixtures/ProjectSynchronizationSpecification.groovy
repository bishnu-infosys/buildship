package org.eclipse.buildship.ui.internal.test.fixtures

import org.gradle.tooling.GradleConnector

import com.google.common.base.Optional
import com.google.common.base.Preconditions

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.NullProgressMonitor

import org.eclipse.buildship.core.GradleCore
import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.configuration.BuildConfiguration
import org.eclipse.buildship.core.internal.util.gradle.GradleDistribution
import org.eclipse.buildship.core.internal.workspace.NewProjectHandler

abstract class ProjectSynchronizationSpecification extends WorkspaceSpecification {

    protected static final GradleDistribution DEFAULT_DISTRIBUTION = GradleDistribution.fromBuild()

    protected void synchronizeAndWait(File location) {
        Optional<IProject> project = CorePlugin.workspaceOperations().findProjectByLocation(location.canonicalFile)
        Preconditions.checkState(project.present, "Workspace does not have project located at ${location.absolutePath}")
        synchronizeAndWait(project.get())
    }

    protected void synchronizeAndWait(IProject project) {
        GradleCore.workspace.getBuild(project).get().synchronize(new NullProgressMonitor());
        waitForGradleJobsToFinish()
    }

    protected void importAndWait(File location, GradleDistribution distribution = GradleDistribution.fromBuild()) {
        BuildConfiguration buildConfiguration = createOverridingBuildConfiguration(location, distribution)
        CorePlugin.gradleWorkspaceManager().getGradleBuild(buildConfiguration).synchronize(NewProjectHandler.IMPORT_AND_MERGE, GradleConnector.newCancellationTokenSource(), new NullProgressMonitor())
        waitForGradleJobsToFinish()
    }
}
