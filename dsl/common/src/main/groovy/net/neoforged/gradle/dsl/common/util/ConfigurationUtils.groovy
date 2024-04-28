package net.neoforged.gradle.dsl.common.util

import groovy.transform.CompileStatic
import net.neoforged.gradle.dsl.common.extensions.dependency.replacement.DependencyReplacement
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

import java.util.concurrent.atomic.AtomicInteger

@CompileStatic
class ConfigurationUtils {

    private static AtomicInteger temporaryConfigurationCounter = new AtomicInteger(0)

    private ConfigurationUtils() {
        throw new IllegalStateException("Can not instantiate an instance of: ConfigurationUtils. This is a utility class")
    }

    /**
     * Creates an attached configuration that can be resolved, but not consumed.
     *
     * @param project The project to create the configuration for
     * @param dependencies The dependencies to add to the configuration
     * @return The detached configuration
     */
    static Configuration temporaryAttachedConfiguration(final Project project, final Dependency... dependencies) {
        final Configuration configuration = project.getConfigurations().create("ng_do_not_use_this_configuration_${temporaryConfigurationCounter.incrementAndGet()}", new Action<Configuration>() {
            @Override
            void execute(Configuration files) {
                files.setCanBeConsumed(false)
                files.setCanBeResolved(true)
                files.getDependencies().addAll(dependencies)

                final DependencyReplacement dependencyReplacement = project.getExtensions().getByType(DependencyReplacement.class)
                dependencyReplacement.handleConfiguration(files)
            }
        });

        return configuration
    }

    /**
     * Creates a detached configuration that can be resolved, but not consumed.
     *
     * @param project The project to create the configuration for
     * @param dependencies The dependencies to add to the configuration
     * @return The detached configuration
     */
    static Configuration temporaryConfiguration(final Project project, final Dependency... dependencies) {
        final Configuration configuration = project.getConfigurations().detachedConfiguration(dependencies);

        configuration.setCanBeConsumed(false)
        configuration.setCanBeResolved(true)

        final DependencyReplacement dependencyReplacement = project.getExtensions().getByType(DependencyReplacement.class)
        dependencyReplacement.handleConfiguration(configuration)

        return configuration
    }

    /**
     * Creates a detached configuration that can be resolved, but not consumed.
     *
     * @param configurations The configuration handler.
     * @param dependencies The dependencies to add to the configuration
     * @return The detached configuration
     */
    static Configuration temporaryUnhandledConfiguration(final ConfigurationContainer configurations, final Dependency... dependencies) {
        final Configuration configuration = configurations.detachedConfiguration(dependencies)
        configuration.setCanBeConsumed(false)
        configuration.setCanBeResolved(true)

        return configuration
    }

    /**
     * Creates a provider that will resolve a temporary configuration containing the given dependency.
     */
    static Provider<File> getArtifactProvider(Project project, Provider<? extends Object> dependencyNotationProvider) {
        return dependencyNotationProvider.flatMap(dependencyNotation -> {
            Configuration configuration = temporaryConfiguration(project, project.getDependencies().create(dependencyNotation));
            configuration.transitive = false;
            return configuration.getElements().map(files -> files.iterator().next().getAsFile());
        });
    }

    static List<Configuration> findReplacementConfigurations(final Project project, final Configuration configuration) {
        final Set<Configuration> resultContainer = new HashSet<>();

        resultContainer.addAll(findCompileClasspathSourceSet(project, configuration))
        resultContainer.addAll(findRuntimeClasspathSourceSet(project, configuration))

        return resultContainer.toList()
    }

    static List<Configuration> findCompileClasspathSourceSet(final Project project, final Configuration configuration) {
        final SourceSetContainer sourceSetContainer = project.getExtensions().getByType(SourceSetContainer.class)
        final List<Configuration> targets = new ArrayList<>();

        sourceSetContainer.forEach {sourceSet -> {
            final Configuration sourceSetConfiguration = project.getConfigurations().findByName(sourceSet.getCompileClasspathConfigurationName())
            if (sourceSetConfiguration == null)
                return;

            if (configuration == sourceSetConfiguration) {
                targets.clear()
                targets.add(sourceSetConfiguration)
                return targets
            }

            final Set<Configuration> supers = getAllSuperConfigurations(sourceSetConfiguration)
            if (supers.contains(configuration)) {
                targets.add(sourceSetConfiguration)
            }
        }}

        return targets
    }

    static List<Configuration> findRuntimeClasspathSourceSet(final Project project, final Configuration configuration) {
        final SourceSetContainer sourceSetContainer = project.getExtensions().getByType(SourceSetContainer.class)
        final List<Configuration> targets = new ArrayList<>();

        sourceSetContainer.forEach {sourceSet -> {
            final Configuration sourceSetConfiguration = project.getConfigurations().findByName(sourceSet.getRuntimeClasspathConfigurationName())
            if (sourceSetConfiguration == null)
                return;

            if (configuration == sourceSetConfiguration) {
                targets.clear()
                targets.add(sourceSetConfiguration)
                return targets
            }

            final Set<Configuration> supers = getAllSuperConfigurations(sourceSetConfiguration)
            if (supers.contains(configuration)) {
                targets.add(sourceSetConfiguration)
            }
        }}

        return targets
    }

    static Set<Configuration> getAllSuperConfigurations(final Configuration configuration) {
        final Set<Configuration> supers = new HashSet<>();

        getAllSuperConfigurationsRecursive(configuration, supers)

        return supers
    }


    private static void getAllSuperConfigurationsRecursive(final Configuration configuration, final Set<Configuration> supers) {
        configuration.getExtendsFrom().forEach {config -> {
            if (supers.add(config)) {
                getAllSuperConfigurationsRecursive(config, supers)
            }
        }}
    }
}
