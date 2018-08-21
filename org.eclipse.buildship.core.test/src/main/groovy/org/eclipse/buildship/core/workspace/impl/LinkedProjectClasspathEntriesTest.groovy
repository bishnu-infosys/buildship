package org.eclipse.buildship.core.workspace.impl

import org.eclipse.jdt.core.IJavaProject

import org.eclipse.buildship.core.test.fixtures.ProjectSynchronizationSpecification
import org.eclipse.buildship.core.test.fixtures.TestEnvironment.*

class LinkedProjectClasspathEntriesTest extends ProjectSynchronizationSpecification {

    def "Can include linked files in the classpath"(String type) {
        setup:
        def parent = dir('another')
        def linkedResource = type == '1' ? file('another/file.jar') : dir('another/dir')
        def linkedResourcePath = linkedResource.absolutePath.replace('\\', "\\\\")
        File location = dir('project') {
            file 'build.gradle', """
                import org.gradle.plugins.ide.eclipse.model.Library
                apply plugin: 'java'
                apply plugin: 'eclipse'

                eclipse {
                   project.linkedResource name: 'linked-resource', type: '${type}', location: '${linkedResourcePath}'
                    classpath.file.whenMerged {
                        entries += new Library(fileReference('linked-resource'))
                    }
                }
            """
        }

        when:
        importAndWait(location)
        IJavaProject project = findJavaProject('project')

        then:
        project.getResolvedClasspath(false).find { it.path.toPortableString() == '/project/linked-resource' }

        where:
        type << ['1', '2']
    }
}
