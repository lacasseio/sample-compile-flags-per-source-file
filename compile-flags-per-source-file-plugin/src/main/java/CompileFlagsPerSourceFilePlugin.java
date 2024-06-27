import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.VisualCpp;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public /*final*/ abstract class CompileFlagsPerSourceFilePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getComponents().withType(CppComponent.class).configureEach(new Action<>() {
            private FileCollection cppSource(CppComponent component) {
                return project.getObjects().fileCollection().from((Callable<?>) () -> cppSourceOf(component));
            }

            @Override
            public void execute(CppComponent component) {
                // We support shadowing the `CppComponent#cppSource` property to fix the core patterns.
                //   The core plugins filters the default location for: *.cpp, *.c++, *.cc
                final DefaultCompileFlagsExtension extension = ((ExtensionAware) component).getExtensions().create("compileFlags", DefaultCompileFlagsExtension.class, cppSource(component));

                project.afterEvaluate(__ -> extension.finalizeExtension());

                component.getBinaries().configureEach(binary -> {
                    final TaskProvider<CppCompile> compileTask = project.getTasks().named(compileTaskName(binary), CppCompile.class);
                    compileTask.configure(task -> {
                        task.getSource().setFrom(extension.getCppSource()); // Overwrite default sources

                        // users should configure c++ sources using CppComponent#getSource().from(...)
                        task.getSource().disallowChanges();
                    });
                    extension.getSourceCompileFlags().all(new Action<>() {
                        @Override
                        public void execute(DefaultCompileFlagsExtension.CompileFlagsBucket entry) {
                            final TaskProvider<CppCompile> sourceCompileTask = project.getTasks().register(compileTaskName(binary, entry.getIdentifier()), CppCompile.class);

                            sourceCompileTask.configure(copyFrom(compileTask));
                            sourceCompileTask.configure(task -> {
                                task.getCompilerArgs().addAll(entry.getAdditionalCompileFlags());
                                task.getCompilerArgs().disallowChanges();
                                task.getSource().from(entry.getCppSource()).disallowChanges();

                                // We use the temporary directory to ensure the output directories are different
                                task.getObjectFileDir()
                                        .value(project.getLayout().getBuildDirectory().dir("tmp/" + task.getName()))
                                        .disallowChanges();
                            });

                            project.getTasks().named(linkTaskName(binary), AbstractLinkTask.class, task -> {
                                task.source(sourceCompileTask.map(it -> it.getObjectFileDir().getAsFileTree().matching(objectFiles())));
                            });
                        }

                        private Action<PatternFilterable> objectFiles() {
                            return it -> it.include("**/*.o", "**/*.obj");
                        }

                        private Action<CppCompile> copyFrom(Provider<CppCompile> compileTask) {
                            return task -> {
                                task.getCompilerArgs().addAll(compileTask.flatMap(CppCompile::getCompilerArgs));
                                // Do not lock as we allow additional flags

                                task.getIncludes()
                                        .from(compileTask.flatMap(elementsOf(CppCompile::getIncludes)))
                                        .disallowChanges();
                                task.getToolChain()
                                        .value(compileTask.flatMap(CppCompile::getToolChain))
                                        .disallowChanges();
                                // Add macros as flag because CppCompile#macros is not a Gradle property.
                                task.getCompilerArgs().addAll(task.getToolChain().zip(compileTask.flatMap(it -> project.provider(it::getMacros)), this::toMacroFlags));
                                task.getSystemIncludes()
                                        .from(compileTask.flatMap(elementsOf(CppCompile::getSystemIncludes)))
                                        .disallowChanges();
                                task.getTargetPlatform()
                                        .value(compileTask.flatMap(CppCompile::getTargetPlatform))
                                        .disallowChanges();
                            };
                        }

                        private List<String> toMacroFlags(NativeToolChain toolChain, Map<String, String> macros) {
                            return macros.entrySet().stream().map(it -> {
                                final StringBuilder builder = new StringBuilder();

                                if (toolChain instanceof VisualCpp)
                                    builder.append("/D");
                                else
                                    builder.append("-D");

                                builder.append(it.getKey());
                                if (it.getValue() != null) {
                                    builder.append("=").append(it.getValue());
                                }
                                return builder.toString();
                            }).collect(Collectors.toList());
                        }
                    });
                });
            }
        });
    }

    private static FileCollection cppSourceOf(CppComponent component) {
        FileCollection result = null;
        if (component instanceof ExtensionAware) {
            result = (FileCollection) ((ExtensionAware) component).getExtensions().getExtraProperties().getProperties().get("cppSource");
        }

        if (result == null) {
            result = component.getCppSource();
        }

        return result;
    }

    private static <T> Transformer<Provider<Set<FileSystemLocation>>, T> elementsOf(Transformer<? extends FileCollection, ? super T> mapper) {
        return it -> mapper.transform(it).getElements();
    }

    //region Names
    private static String qualifyingName(CppBinary binary) {
        String result = binary.getName();
        if (result.startsWith("main")) {
            result = result.substring("main".length());
        } else if (result.endsWith("Executable")) {
            result = result.substring(0, result.length() - "Executable".length());
        }
        return uncapitalize(result);
    }

    private static String compileTaskName(CppBinary binary) {
        return "compile" + capitalize(qualifyingName(binary)) + "Cpp";
    }

    private static String compileTaskName(CppBinary binary, String sourceSet) {
        return "compile" + capitalize(qualifyingName(binary)) + capitalize(sourceSet) + "Cpp";
    }

    private static String linkTaskName(CppBinary binary) {
        return "link" + capitalize(qualifyingName(binary));
    }
    //endregion

    //region StringUtils
    private static String uncapitalize(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    //endregion
}
