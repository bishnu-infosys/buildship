/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.internal.invocation;

import java.util.List;

/**
 * Defines extra attributes to set for each Gradle invocations.
 * <p/>
 * The interface is used in the {@code org.eclipse.buildship.core.internal.invocationcustomizers} extension point.
 *
 * @author Donat Csikos
 * @since 2.0
 */
// TODO (donat) move back to org.eclipse.buildship.core.invocation package.
public interface InvocationCustomizer {

    /**
     * Returns the list of extra arguments for the Gradle invocations.
     *
     * @return the extra arguments
     */
    List<String> getExtraArguments();
}
