/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 *     Simon Scholz <simon.scholz@vogella.com> - Bug 479243
 */

package org.eclipse.buildship.ui.internal.view.task;

import org.eclipse.buildship.ui.internal.util.nodeselection.NodeSelection;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.ISources;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Base class for all {@code AbstractHandler} classes that contain execution logic that depends on the current selection.
 */
 abstract class SelectionDependentHandler extends AbstractHandler {

    @Override
    public void setEnabled(Object evaluationContext) {
        boolean enabled = false;
        if (evaluationContext instanceof IEvaluationContext) {
            IEvaluationContext evalContext = (IEvaluationContext) evaluationContext;
            // getting the current ISelection from the IEvaluationContext
            // See https://wiki.eclipse.org/Command_Core_Expressions
            Object variable = evalContext.getVariable(ISources.ACTIVE_CURRENT_SELECTION_NAME);
            if (variable instanceof NodeSelection) {
                enabled = isEnabledFor((NodeSelection) variable);
            }
        }
        setBaseEnabled(enabled);
    }

    protected NodeSelection getSelectionHistory(ExecutionEvent event) {
        ISelection currentSelection = HandlerUtil.getCurrentSelection(event);
        return NodeSelection.from(currentSelection);
    }

    protected abstract boolean isEnabledFor(NodeSelection selection);

}
