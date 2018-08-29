/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.ui.internal.wizard.project;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.gradle.tooling.CancellationTokenSource;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.GradleDistributionInfo;
import org.eclipse.buildship.core.internal.configuration.BuildConfiguration;
import org.eclipse.buildship.core.internal.operation.BaseToolingApiOperation;
import org.eclipse.buildship.core.internal.operation.ToolingApiOperation;
import org.eclipse.buildship.core.internal.operation.ToolingApiOperations;
import org.eclipse.buildship.core.internal.operation.ToolingApiStatus;
import org.eclipse.buildship.core.internal.operation.ToolingApiStatus.ToolingApiStatusType;
import org.eclipse.buildship.core.internal.projectimport.ProjectImportConfiguration;
import org.eclipse.buildship.core.internal.util.binding.Property;
import org.eclipse.buildship.core.internal.util.binding.ValidationListener;
import org.eclipse.buildship.core.internal.util.binding.Validator;
import org.eclipse.buildship.core.internal.util.binding.Validators;
import org.eclipse.buildship.core.internal.util.collections.CollectionsUtils;
import org.eclipse.buildship.core.internal.util.file.FileUtils;
import org.eclipse.buildship.core.internal.workspace.GradleBuild;
import org.eclipse.buildship.core.internal.workspace.NewProjectHandler;
import org.eclipse.buildship.ui.internal.util.workbench.WorkbenchUtils;
import org.eclipse.buildship.ui.internal.util.workbench.WorkingSetUtils;
import org.eclipse.buildship.ui.internal.view.execution.ExecutionsView;
import org.eclipse.buildship.ui.internal.view.task.TaskView;

/**
 * Controller class for the {@link ProjectImportWizard}. Contains all non-UI related calculations
 * the wizard has to perform.
 */
public class ProjectImportWizardController {

    // keys to load/store project properties in the dialog setting
    private static final String SETTINGS_KEY_PROJECT_DIR = "project_location"; //$NON-NLS-1$
    private static final String SETTINGS_KEY_GRADLE_DISTRIBUTION = "gradle_distribution"; //$NON-NLS-1$
    private static final String SETTINGS_KEY_APPLY_WORKING_SETS = "apply_working_sets"; //$NON-NLS-1$
    private static final String SETTINGS_KEY_WORKING_SETS = "working_sets"; //$NON-NLS-1$
    private static final String SETTINGS_KEY_GRADLE_USER_HOME = "gradle_user_home"; //$NON-NLS-1$
    private static final String SETTINGS_KEY_BUILD_SCANS = "build_scans"; //$NON-NLS-1$
    private static final String SETTINGS_KEY_OFFLINE_MODE = "offline_mode"; //$NON-NLS-1$
    private static final String SETTINGS_KEY_AUTO_SYNC = "auto_sync"; //$NON-NLS-1$

    private final ProjectImportConfiguration configuration;

    public ProjectImportWizardController(IWizard projectImportWizard) {
        // assemble configuration object that serves as the data model of the wizard
        Validator<File> projectDirValidator = Validators.and(
                Validators.requiredDirectoryValidator(ProjectWizardMessages.Label_ProjectRootDirectory),
                Validators.nonWorkspaceFolderValidator(ProjectWizardMessages.Label_ProjectRootDirectory));
        Validator<GradleDistributionInfo> gradleDistributionValidator = GradleDistributionInfo.validator();
        Validator<Boolean> applyWorkingSetsValidator = Validators.nullValidator();
        Validator<List<String>> workingSetsValidator = Validators.nullValidator();
        Validator<File> gradleUserHomeValidator = Validators.optionalDirectoryValidator("Gradle user home");

        this.configuration = new ProjectImportConfiguration(projectDirValidator, gradleDistributionValidator, gradleUserHomeValidator, applyWorkingSetsValidator, workingSetsValidator);

        // initialize values from the persisted dialog settings
        IDialogSettings dialogSettings = projectImportWizard.getDialogSettings();
        Optional<File> projectDir = FileUtils.getAbsoluteFile(dialogSettings.get(SETTINGS_KEY_PROJECT_DIR));
        String gradleDistribution = dialogSettings.get(SETTINGS_KEY_GRADLE_DISTRIBUTION);
        Optional<File> gradleUserHome = FileUtils.getAbsoluteFile(dialogSettings.get(SETTINGS_KEY_GRADLE_USER_HOME));
        boolean applyWorkingSets = dialogSettings.get(SETTINGS_KEY_APPLY_WORKING_SETS) != null && dialogSettings.getBoolean(SETTINGS_KEY_APPLY_WORKING_SETS);
        List<String> workingSets = ImmutableList.copyOf(CollectionsUtils.nullToEmpty(dialogSettings.getArray(SETTINGS_KEY_WORKING_SETS)));
        boolean buildScansEnabled = dialogSettings.getBoolean(SETTINGS_KEY_BUILD_SCANS);
        boolean offlineMode = dialogSettings.getBoolean(SETTINGS_KEY_OFFLINE_MODE);
        boolean autoSync = dialogSettings.getBoolean(SETTINGS_KEY_AUTO_SYNC);

        this.configuration.setProjectDir(projectDir.orNull());
        this.configuration.setOverwriteWorkspaceSettings(false);
        this.configuration.setDistributionInfo(GradleDistributionInfo.deserializeFromString(gradleDistribution).toGradleDistributionOrDefault().getDistributionInfo());
        this.configuration.setGradleUserHome(gradleUserHome.orNull());
        this.configuration.setApplyWorkingSets(applyWorkingSets);
        this.configuration.setWorkingSets(workingSets);
        this.configuration.setBuildScansEnabled(buildScansEnabled);
        this.configuration.setOfflineMode(offlineMode);
        this.configuration.setAutoSync(autoSync);

        // store the values every time they change
        saveFilePropertyWhenChanged(dialogSettings, SETTINGS_KEY_PROJECT_DIR, this.configuration.getProjectDir());
        saveDistributionInfoPropertyWhenChanged(dialogSettings, this.configuration.getDistributionInfo());
        saveFilePropertyWhenChanged(dialogSettings, SETTINGS_KEY_GRADLE_USER_HOME, this.configuration.getGradleUserHome());
        saveBooleanPropertyWhenChanged(dialogSettings, SETTINGS_KEY_APPLY_WORKING_SETS, this.configuration.getApplyWorkingSets());
        saveStringArrayPropertyWhenChanged(dialogSettings, SETTINGS_KEY_WORKING_SETS, this.configuration.getWorkingSets());
        saveBooleanPropertyWhenChanged(dialogSettings, SETTINGS_KEY_BUILD_SCANS, this.configuration.getBuildScansEnabled());
        saveBooleanPropertyWhenChanged(dialogSettings, SETTINGS_KEY_OFFLINE_MODE, this.configuration.getOfflineMode());
        saveBooleanPropertyWhenChanged(dialogSettings, SETTINGS_KEY_AUTO_SYNC, this.configuration.getAutoSync());
    }

    private void saveBooleanPropertyWhenChanged(final IDialogSettings settings, final String settingsKey, final Property<Boolean> target) {
        target.addValidationListener(new ValidationListener() {

            @Override
            public void validationTriggered(Property<?> source, Optional<String> validationErrorMessage) {
                settings.put(settingsKey, target.getValue());
            }
        });
    }

    private void saveStringArrayPropertyWhenChanged(final IDialogSettings settings, final String settingsKey, final Property<List<String>> target) {
        target.addValidationListener(new ValidationListener() {

            @Override
            public void validationTriggered(Property<?> source, Optional<String> validationErrorMessage) {
                List<String> value = target.getValue();
                settings.put(settingsKey, value.toArray(new String[value.size()]));
            }
        });
    }

    private void saveFilePropertyWhenChanged(final IDialogSettings settings, final String settingsKey, final Property<File> target) {
        target.addValidationListener(new ValidationListener() {

            @Override
            public void validationTriggered(Property<?> source, Optional<String> validationErrorMessage) {
                settings.put(settingsKey, FileUtils.getAbsolutePath(target.getValue()).orNull());
            }
        });
    }

    private void saveDistributionInfoPropertyWhenChanged(final IDialogSettings settings, final Property<GradleDistributionInfo> target) {
        target.addValidationListener(new ValidationListener() {

            @Override
            public void validationTriggered(Property<?> source, Optional<String> validationErrorMessage) {
                settings.put(SETTINGS_KEY_GRADLE_DISTRIBUTION, target.getValue().serializeToString());
            }
        });
    }

    public ProjectImportConfiguration getConfiguration() {
        return this.configuration;
    }

    public boolean performImportProject(IWizardContainer container, final NewProjectHandler newProjectHandler) {
        try {
            container.run(true, true, new IRunnableWithProgress() {

                @Override
                public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                    BuildConfiguration buildConfig = ProjectImportWizardController.this.configuration.toBuildConfig();
                    GradleBuild build = CorePlugin.gradleWorkspaceManager().getGradleBuild(buildConfig);
                    ImportWizardNewProjectHandler workingSetsAddingNewProjectHandler = new ImportWizardNewProjectHandler(newProjectHandler, ProjectImportWizardController.this.configuration);

                    InitializeNewProjectOperation initializeOperation = new InitializeNewProjectOperation(buildConfig);
                    ToolingApiOperation synchronizeOperation = new SynchronizeOperation(build, workingSetsAddingNewProjectHandler);

                    try {
                        CorePlugin.operationManager().run(ToolingApiOperations.concat(initializeOperation, synchronizeOperation), monitor);
                    } catch (Exception e) {
                        throw new InvocationTargetException(e);
                    }
                }
            });
        } catch (InvocationTargetException e) {
            ToolingApiStatus status = WizardHelper.containerExceptionToToolingApiStatus(e);
            status.log();
            return !ToolingApiStatusType.IMPORT_ROOT_DIR_FAILED.matches(status);
        } catch (InterruptedException ignored) {
            return false;
        }

        return true;
    }

    /**
     * Executes the synchronization on the target Gradle build.
     */
    private static class SynchronizeOperation extends BaseToolingApiOperation {

        private final GradleBuild gradleBuild;
        private final ImportWizardNewProjectHandler workingSetsAddingNewProjectHandler;

        public SynchronizeOperation(GradleBuild gradleBuild, ImportWizardNewProjectHandler workingSetsAddingNewProjectHandler) {
            super("Synchronize project " + gradleBuild.getBuildConfig().getRootProjectDirectory().getName());
            this.gradleBuild = gradleBuild;
            this.workingSetsAddingNewProjectHandler = workingSetsAddingNewProjectHandler;
        }

        @Override
        public void runInToolingApi(CancellationTokenSource tokenSource, IProgressMonitor monitor) throws Exception {
            this.gradleBuild.synchronize(this.workingSetsAddingNewProjectHandler, tokenSource, monitor);
        }

        @Override
        public ISchedulingRule getRule() {
            return ResourcesPlugin.getWorkspace().getRoot();
        }
    }

    /**
     * A delegating {@link NewProjectHandler} which adds workingsets to the imported projects and
     * ensures that the Gradle views are visible.
     *
     * @author Stefan Oehme
     */
    private static final class ImportWizardNewProjectHandler implements NewProjectHandler {

        private final ProjectImportConfiguration configuration;
        private final NewProjectHandler importedBuildDelegate;

        private volatile boolean gradleViewsVisible;

        private ImportWizardNewProjectHandler(NewProjectHandler delegate, ProjectImportConfiguration configuration) {
            this.importedBuildDelegate = delegate;
            this.configuration = configuration;
        }

        @Override
        public boolean shouldImportNewProjects() {
            return this.importedBuildDelegate.shouldImportNewProjects();
        }

        @Override
        public void afterProjectImported(IProject project) {
            this.importedBuildDelegate.afterProjectImported(project);
            addWorkingSets(project);
            ensureGradleViewsAreVisible();
        }

        private void addWorkingSets(IProject project) {
            List<String> workingSetNames = this.configuration.getApplyWorkingSets().getValue() ? ImmutableList.copyOf(this.configuration.getWorkingSets().getValue())
                    : ImmutableList.<String> of();
            IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
            IWorkingSet[] workingSets = WorkingSetUtils.toWorkingSets(workingSetNames);
            workingSetManager.addToWorkingSets(project, workingSets);
        }

        private void ensureGradleViewsAreVisible() {
            PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

                @Override
                public void run() {
                    if (!ImportWizardNewProjectHandler.this.gradleViewsVisible) {
                        ImportWizardNewProjectHandler.this.gradleViewsVisible = true;
                        WorkbenchUtils.showView(TaskView.ID, null, IWorkbenchPage.VIEW_ACTIVATE);
                        WorkbenchUtils.showView(ExecutionsView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
                    }
                }
            });
        }
    }

}
