/*
 * Copyright (c) 2018 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.internal;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildActionExecuter.Builder;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.TestLauncher;
import org.gradle.tooling.model.build.BuildEnvironment;

import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.buildship.core.internal.configuration.GradleArguments;
import org.eclipse.buildship.core.internal.gradle.GradleProgressAttributes;

final class IdeAttachedProjectConnection implements ProjectConnection {

    private final ProjectConnection delegate;
    private final GradleArguments gradleArguments;
    private final GradleProgressAttributes progressAttributes;

    private IdeAttachedProjectConnection(ProjectConnection connection, GradleArguments gradleArguments, GradleProgressAttributes progressAttributes) {
        this.delegate = connection;
        this.gradleArguments = gradleArguments;
        this.progressAttributes = progressAttributes;
    }

    @Override
    public BuildLauncher newBuild() {
        return configureOperation(this.delegate.newBuild());
    }

    @Override
    public TestLauncher newTestLauncher() {
        return configureOperation(this.delegate.newTestLauncher());
    }

    @Override
    public <T> ModelBuilder<T> model(Class<T> modelType) {
        return configureOperation(this.delegate.model(modelType));
    }

    @Override
    public <T> BuildActionExecuter<T> action(BuildAction<T> buildAction) {
        return configureOperation(this.delegate.action(buildAction));
    }

    private <T extends LongRunningOperation> T configureOperation(T operation) {
        BuildEnvironment buildEnvironment = this.delegate.getModel(BuildEnvironment.class);
        this.gradleArguments.applyTo(operation, buildEnvironment);
        this.gradleArguments.describe(this.progressAttributes, buildEnvironment);
        this.progressAttributes.applyTo(operation);
        return operation;
    }

    @Override
    public void close() {
        this.delegate.close();
    }

    @Override
    public Builder action() {
        throw new UnsupportedOperationException(explainUsage("action()", "action(BuildAction)"));
    }

    @Override
    public <T> T getModel(Class<T> modelType) throws GradleConnectionException, IllegalStateException {
        throw new UnsupportedOperationException(explainUsage("getModel(Class)", "model(Class)"));
    }

    @Override
    public <T> void getModel(Class<T> modelType, ResultHandler<? super T> handler) throws IllegalStateException {
        throw new UnsupportedOperationException(explainUsage("getModel(Class, ResultHandler)", "model(Class)"));
    }

    private static String explainUsage(String methodSignature, String alternativeSignature) {
        return "Cannot call ProjectConnection." + methodSignature + " as it is not possible to hook its progress into the IDE. Use ProjectConnection." + alternativeSignature + " instead";
    }

    public static ProjectConnection newInstance(CancellationTokenSource tokenSource, GradleArguments gradleArguments, IProgressMonitor monitor) {
        GradleConnector connector = GradleConnector.newConnector();
        gradleArguments.applyTo(connector);
        ProjectConnection connection = connector.connect();

        GradleProgressAttributes progressAttributes = GradleProgressAttributes.builder(tokenSource, monitor)
                .forBackgroundProcess()
                .withFullProgress()
                .build();

        return new IdeAttachedProjectConnection(connection, gradleArguments, progressAttributes);
    }
}
