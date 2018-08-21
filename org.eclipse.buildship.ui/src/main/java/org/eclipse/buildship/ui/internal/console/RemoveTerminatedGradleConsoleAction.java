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

package org.eclipse.buildship.ui.internal.console;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import org.eclipse.buildship.core.internal.console.ProcessDescription;
import org.eclipse.buildship.ui.internal.PluginImage;
import org.eclipse.buildship.ui.internal.PluginImages;

import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.Action;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;

/**
 * Removes the finished {@link org.eclipse.debug.core.ILaunch} instance associated with a given {@link GradleConsole}
 * instance. The action is only enabled if the launch instance has terminated.
 *
 * Note: the implementation is somewhat along the lines of
 * {@code org.eclipse.debug.internal.ui.views.console.ConsoleRemoveLaunchAction}.
 */
public final class RemoveTerminatedGradleConsoleAction extends Action  {

    private final GradleConsole gradleConsole;

    public RemoveTerminatedGradleConsoleAction(GradleConsole gradleConsole) {
        this.gradleConsole = Preconditions.checkNotNull(gradleConsole);

        setToolTipText(ConsoleMessages.Action_RemoveTerminatedConsole_Tooltip);
        setImageDescriptor(PluginImages.REMOVE_CONSOLE.withState(PluginImage.ImageState.ENABLED).getImageDescriptor());
        setDisabledImageDescriptor(PluginImages.REMOVE_CONSOLE.withState(PluginImage.ImageState.DISABLED).getImageDescriptor());

        registerJobChangeListener();
    }

    private void registerJobChangeListener() {
        Optional<ProcessDescription> processDescription = this.gradleConsole.getProcessDescription();
        if (processDescription.isPresent()) {
            Job job = processDescription.get().getJob();
            job.addJobChangeListener(new JobChangeAdapter() {

                @Override
                public void done(IJobChangeEvent event) {
                    update();
                }
            });
            update();
        } else {
            // if no job is associated with the console, never enable this action
            setEnabled(false);
        }
    }

    private void update() {
        setEnabled(this.gradleConsole.isCloseable() && this.gradleConsole.isTerminated());
    }

    @Override
    public void run() {
        ConsolePlugin.getDefault().getConsoleManager().removeConsoles(new IConsole[] { this.gradleConsole });
    }

    public void dispose() {
    }

}
