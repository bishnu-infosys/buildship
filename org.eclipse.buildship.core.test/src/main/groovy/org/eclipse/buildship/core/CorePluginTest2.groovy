package org.eclipse.buildship.core

import spock.lang.Specification;

class CorePluginTest2 extends Specification {

    def "Services exposed from core plugin are available"() {
        expect:
        CorePlugin.getInstance() != null
        CorePlugin.logger() != null
        CorePlugin.publishedGradleVersions() != null
        CorePlugin.workspaceOperations() != null
        CorePlugin.configurationManager() != null
        CorePlugin.processStreamsProvider() != null
        CorePlugin.gradleLaunchConfigurationManager() != null
    }

}
