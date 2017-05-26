package org.eclipse.buildship.core.configuration.internal

import spock.lang.Shared
import spock.lang.Subject

import com.gradleware.tooling.toolingclient.GradleDistribution

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource

import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.test.fixtures.WorkspaceSpecification

class BuildConfigurationPersistenceTest extends WorkspaceSpecification {

    @Shared
    @Subject
    BuildConfigurationPersistence persistence = new BuildConfigurationPersistence()

    IProject project
    File projectDir

    def setup() {
        project = newProject("sample-project")
        projectDir = dir("external")
    }

    def "save validates input"() {
        when:
        persistence.saveBuildConfiguration(project, null)

        then:
        thrown NullPointerException

        when:
        persistence.saveBuildConfiguration((File) null, validProperties(projectDir))

        then:
        thrown NullPointerException

        when:
        persistence.saveBuildConfiguration(projectDir, null)

        then:
        thrown NullPointerException

        when:
        persistence.saveBuildConfiguration((IProject) null, validProperties(project))

        then:
        thrown NullPointerException
    }

    def "can save and read preferences for workspace project"() {
        setup:
        DefaultBuildConfigurationProperties properties = validProperties(project)
        persistence.saveBuildConfiguration(project, properties)

        expect:
        persistence.readBuildConfiguratonProperties(project) == properties
    }

    def "can save and read preferences for external project"() {
        setup:
        DefaultBuildConfigurationProperties properties = validProperties(projectDir)
        persistence.saveBuildConfiguration(projectDir, properties)

        expect:
        persistence.readBuildConfiguratonProperties(projectDir) == properties
    }

    def "reading build configuration validates input"() {
        when:
        persistence.readBuildConfiguratonProperties((IProject) null)

        then:
        thrown NullPointerException

        when:
        persistence.readBuildConfiguratonProperties((File) null)

        then:
        thrown NullPointerException
    }

    def "Reading nonexisting build configuration returns default"() {
        when:
        DefaultBuildConfigurationProperties properties = persistence.readBuildConfiguratonProperties(project)

        then:
        properties.rootProjectDirectory == project.location.toFile()
        properties.overrideWorkspaceSettings == false
        properties.gradleDistribution == GradleDistribution.fromBuild()
        properties.gradleUserHome == null
        properties.buildScansEnabled == false
        properties.offlineMode == false

        when:
        properties = persistence.readBuildConfiguratonProperties(projectDir)

        then:
        properties.rootProjectDirectory == projectDir
        properties.overrideWorkspaceSettings == false
        properties.gradleDistribution == GradleDistribution.fromBuild()
        properties.gradleUserHome == null
        properties.buildScansEnabled == false
        properties.offlineMode == false
    }

    def "Reading broken build configuration results in runtime exception"() {
        setup:
        String prefsFileContent = """override.workspace.settings=true
connection.gradle.distribution=INVALID_GRADLE_DISTRO"""
        fileTree(project.location.toFile()) { dir('.settings') { file "${CorePlugin.PLUGIN_ID}.prefs", prefsFileContent } }
        fileTree(projectDir) { dir('.settings') { file "${CorePlugin.PLUGIN_ID}.prefs", prefsFileContent } }
        project.refreshLocal(IResource.DEPTH_INFINITE, null)

        when:
        persistence.readBuildConfiguratonProperties(project)

        then:
        thrown RuntimeException

        when:
        persistence.readBuildConfiguratonProperties(projectDir)

        then:
        thrown RuntimeException
    }

    def "If workspace override is not set then overridden configuration properties are ignored"(boolean buildScansEnabled, boolean offlineMode) {
        setup:
        DefaultBuildConfigurationProperties properties = new DefaultBuildConfigurationProperties(projectDir, GradleDistribution.fromBuild(), null, false, buildScansEnabled, offlineMode)
        persistence.saveBuildConfiguration(project, properties)
        persistence.saveBuildConfiguration(projectDir, properties)

        when:
        DefaultBuildConfigurationProperties workspaceConfig = persistence.readBuildConfiguratonProperties(project)
        DefaultBuildConfigurationProperties externalConfig = persistence.readBuildConfiguratonProperties(projectDir)

        then:
        workspaceConfig.buildScansEnabled == false
        workspaceConfig.offlineMode == false
        externalConfig.buildScansEnabled == false
        externalConfig.offlineMode == false

        where:
        buildScansEnabled | offlineMode
        false             | false
        false             | true
        true              | false
        true              | true
    }

    def "If workspace override is set then overridden configuration properties are persisted"(boolean buildScansEnabled, boolean offlineMode) {
        setup:
        DefaultBuildConfigurationProperties properties = new DefaultBuildConfigurationProperties(projectDir, GradleDistribution.fromBuild(), null, true, buildScansEnabled, offlineMode)
        persistence.saveBuildConfiguration(project, properties)
        persistence.saveBuildConfiguration(projectDir, properties)

        when:
        DefaultBuildConfigurationProperties workspaceConfig = persistence.readBuildConfiguratonProperties(project)
        DefaultBuildConfigurationProperties externalConfig = persistence.readBuildConfiguratonProperties(projectDir)

        then:
        workspaceConfig.buildScansEnabled == buildScansEnabled
        workspaceConfig.offlineMode == offlineMode
        externalConfig.buildScansEnabled == buildScansEnabled
        externalConfig.offlineMode == offlineMode

        where:
        buildScansEnabled | offlineMode
        false             | false
        false             | true
        true              | false
        true              | true
    }

    def "pathToRoot methods validate input"() {
        when:
        persistence.readPathToRoot(null)

        then:
        thrown NullPointerException

        when:
        persistence.savePathToRoot(project, null)

        then:
        thrown NullPointerException

        when:
        persistence.savePathToRoot(projectDir, null)

        then:
        thrown NullPointerException

        when:
        persistence.savePathToRoot((IProject) null, '.')

        then:
        thrown NullPointerException

        when:
        persistence.savePathToRoot((File) null, '.')

        then:
        thrown NullPointerException

        when:
        persistence.deletePathToRoot((IProject) null)

        then:
        thrown NullPointerException

        when:
        persistence.deletePathToRoot((File) null)

        then:
        thrown NullPointerException
    }

    def "reading nonexisting path to root results in runtime exception"() {
        when:
        persistence.readPathToRoot(project)

        then:
        thrown RuntimeException

        when:
        persistence.readPathToRoot(projectDir)

        then:
        thrown RuntimeException
    }

    def "can read and save path to root on workspace project"() {
        when:
        persistence.savePathToRoot(project, 'path-to-root')

        then:
        persistence.readPathToRoot(project) == 'path-to-root'
    }

    def "can read and save path to root on external project"() {
        when:
        persistence.savePathToRoot(projectDir, 'path-to-root')

        then:
        persistence.readPathToRoot(projectDir) == 'path-to-root'
    }

    def "can delete path to root on workspace project"() {
        setup:
        persistence.savePathToRoot(project, 'path-to-root')
        persistence.deletePathToRoot(project)

        when:
        persistence.readPathToRoot(project)

        then:
        thrown RuntimeException
    }

    def "can delete path to root on external project"() {
        setup:
        persistence.savePathToRoot(projectDir, 'path-to-root')
        persistence.deletePathToRoot(projectDir)

        when:
        persistence.readPathToRoot(projectDir)

        then:
        thrown RuntimeException
    }

    private DefaultBuildConfigurationProperties validProperties(IProject project) {
        new DefaultBuildConfigurationProperties(project.getLocation().toFile(), GradleDistribution.fromBuild(), null, false, false, false)
    }

    private DefaultBuildConfigurationProperties validProperties(File projectDir) {
        new DefaultBuildConfigurationProperties(projectDir, GradleDistribution.fromBuild(), null, false, false, false)
    }
}
