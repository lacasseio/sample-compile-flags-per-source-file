import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Transformer;
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
        getSourceCompileFlags().withType(CompileFlagsForSingleFileEntry.class).all(new Action<CompileFlagsForSingleFileEntry>() {
            @Override
            public void execute(CompileFlagsForSingleFileEntry entry) {
                entry.getAdditionalCompileFlags().addAll(entry.getCppSourceFile()
                        .map(asFile())
                        .map(filter(negate(specEntry.filterAction)))
                        .map(toConstant(Collections.<String>emptySet()))
                        .orElse(specEntry.getAdditionalCompileFlags()));
            }

            private Transformer<File, FileSystemLocation> asFile() {
                return FileSystemLocation::getAsFile;
            }

            // Adapt Predicate#negate() to Gradle Spec
            private <T> Spec<T> negate(Spec<? super T> spec) {
                return it -> !spec.isSatisfiedBy(it);
            }

            private <OUT, IN> Transformer<OUT, IN> toConstant(OUT value) {
                return __ -> value;
            }

            // Backport of Provider#filter(Spec)
            private <T> Transformer<T, T> filter(Spec<? super T> spec) {
                return it -> {
                    if (spec.isSatisfiedBy(it)) {
                        return it;
                    } else {
                        return null;
                    }
                };
            }
        });
        return specEntry.dsl;
    }

    public CompileFlags forSource(String fileName) {
        final CompileFlagsForSingleFileEntry entry = entries.computeIfAbsent(fileName, __ -> {
            final CompileFlagsForSingleFileEntry result = objects.newInstance(CompileFlagsForSingleFileEntry.class, "sources" + nextEntry++);
            final FileCollection cppSource = memoize(defaultSources.filter(byName(fileName)));
            result.getCppSourceFile().fileProvider(singleFile(cppSource));
            this.defaultSources = memoize(defaultSources.minus(cppSource)).getAsFileTree();
            getSourceCompileFlags().add(result);
            return result;
        });
        return entry.dsl;
    }

    private static Provider<File> singleFile(FileCollection target) {
        return target.getElements().map(it -> {
            final Iterator<FileSystemLocation> iter = it.iterator();
            if (!iter.hasNext()) {
                return null;
            }
            final File sourceFile = iter.next().getAsFile();
            assert !iter.hasNext() : "expect only one file match";
            return sourceFile;
        });
    }

    private static Spec<File> byName(String fileName) {
        return it -> it.getName().equals(fileName);
    }

    public void finalizeExtension() {
        getSpecs().all(spec -> {
            final CompileFlagsForSpecEntry result = objects.newInstance(CompileFlagsForSpecEntry.class, "sources" + nextEntry++);
            final FileCollection cppSource = memoize(defaultSources.filter(spec.filterAction));
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
        private final Spec<? super File> filterAction;

        @Inject
        public SourceFilterSpec(Spec<? super File> filterAction) {
            this.filterAction = filterAction;
        }
    }
}
