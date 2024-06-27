import org.gradle.api.DomainObjectSet;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

abstract class DefaultCompileFlagsExtension implements CompileFlagsExtension {
    private int nextEntry = 0;
    private final ObjectFactory objects;
    private FileTree defaultSources;
    private final Map<String, CompileFlagsForSingleFileEntry> entries = new LinkedHashMap<>();

    @Inject
    public DefaultCompileFlagsExtension(ObjectFactory objects, FileCollection source) {
        this.objects = objects;
        this.defaultSources = memoize(source).getAsFileTree();
        getCppSource().from((Callable<?>) () -> defaultSources);
    }

    private FileCollection memoize(FileCollection s) {
        SetProperty<FileSystemLocation> result = objects.setProperty(FileSystemLocation.class);
        result.value(s.getElements()).finalizeValueOnRead();
        return objects.fileCollection().from(result);
    }

    public abstract ConfigurableFileCollection getCppSource();

    public CompileFlags forSource(Spec<? super File> filterAction) {
        SourceFilterSpec specEntry = objects.newInstance(SourceFilterSpec.class, filterAction);
        getSpecs().add(specEntry);
        getSourceCompileFlags().withType(CompileFlagsForSingleFileEntry.class).all(entry -> {
            entry.getAdditionalCompileFlags().addAll(entry.getCppSourceFile().map(it -> {
                if (filterAction.isSatisfiedBy(it.getAsFile())) {
                    return null; // use orElse to enforce conditional implicit task dependencies
                } else {
                    return Collections.<String>emptySet();
                }
            }).orElse(specEntry.getAdditionalCompileFlags()));
        });
        return specEntry.dsl;
    }

    public CompileFlags forSource(String fileName) {
        final CompileFlagsForSingleFileEntry entry = entries.computeIfAbsent(fileName, __ -> {
            final CompileFlagsForSingleFileEntry result = objects.newInstance(CompileFlagsForSingleFileEntry.class, "sources" + nextEntry++);
            final FileCollection cppSource = memoize(defaultSources.filter(byName(fileName)));

            final Provider<File> cppSourceFile = cppSource.getElements().map(it -> {
                final Iterator<FileSystemLocation> iter = it.iterator();
                if (!iter.hasNext()) {
                    return null;
                }
                final File sourceFile = iter.next().getAsFile();
                assert !iter.hasNext() : "expect only one file match";
                return sourceFile;
            });
            result.getCppSourceFile().fileProvider(cppSourceFile);
            this.defaultSources = memoize(defaultSources.minus(cppSource)).getAsFileTree();
            getSourceCompileFlags().add(result);
            return result;
        });

        return entry.dsl;
    }

    private static Spec<File> byName(String fileName) {
        return it -> it.getName().equals(fileName);
    }

    public void finalizeExtension() {
        getSpecs().all(spec -> {
            final CompileFlagsForSpecEntry result = objects.newInstance(CompileFlagsForSpecEntry.class, "sources" + nextEntry++);
            final FileCollection cppSource = memoize(defaultSources.filter(spec.spec));
            result.getCppSourceFiles().from(cppSource);
            this.defaultSources = memoize(defaultSources.minus(cppSource)).getAsFileTree();
            getSourceCompileFlags().add(result);
        });
    }

    public abstract DomainObjectSet<CompileFlagsBucket> getSourceCompileFlags();

    public abstract DomainObjectSet<SourceFilterSpec> getSpecs();

    public interface CompileFlagsBucket {
        String getIdentifier();
        Object getCppSource();
        Provider<Set<String>> getAdditionalCompileFlags();
    }

    private static abstract class AbstractCompileFlagsEntry {
        final CompileFlags dsl;

        public AbstractCompileFlagsEntry() {
            this.dsl = new DefaultCompileFlags();
        }

        public abstract SetProperty<String> getAdditionalCompileFlags();

        private class DefaultCompileFlags implements CompileFlags {
            @Override
            public CompileFlags add(String item) {
                getAdditionalCompileFlags().add(item);
                return this;
            }

            @Override
            public CompileFlags add(Provider<? extends String> item) {
                getAdditionalCompileFlags().add(item);
                return this;
            }

            @Override
            public CompileFlags addAll(String... items) {
                getAdditionalCompileFlags().addAll(items);
                return this;
            }

            @Override
            public CompileFlags addAll(Iterable<? extends String> items) {
                getAdditionalCompileFlags().addAll(items);
                return this;
            }

            @Override
            public CompileFlags addAll(Provider<? extends Iterable<? extends String>> items) {
                getAdditionalCompileFlags().addAll(items);
                return this;
            }
        }
    }

    public static abstract class CompileFlagsForSingleFileEntry extends AbstractCompileFlagsEntry implements CompileFlagsBucket {
        private final String identifier;

        @Inject
        public CompileFlagsForSingleFileEntry(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }

        public abstract RegularFileProperty getCppSourceFile();

        @Override
        public Object getCppSource() {
            return getCppSourceFile();
        }
    }

    public static abstract class CompileFlagsForSpecEntry extends AbstractCompileFlagsEntry implements CompileFlagsBucket {
        private final String identifier;

        @Inject
        public CompileFlagsForSpecEntry(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }

        public abstract ConfigurableFileCollection getCppSourceFiles();

        @Override
        public Object getCppSource() {
            return getCppSourceFiles();
        }
    }

    public static abstract class SourceFilterSpec extends AbstractCompileFlagsEntry {
        private final Spec<? super File> spec;

        @Inject
        public SourceFilterSpec(Spec<? super File> spec) {
            this.spec = spec;
        }
    }
}
