import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Named;
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;

abstract class DefaultCompileFlagsExtension implements CompileFlagsExtension {
    private int nextEntry = 0;
    private final ObjectFactory objects;
    private FileTree defaultSources;
    private final Map<File, SingleSourceFileBucket> entries = new HashMap<>();
    public DomainObjectSet<SourceFilterSpec> sourceSpecs;

    @Inject
    public DefaultCompileFlagsExtension(ObjectFactory objects, FileCollection source) {
        this.objects = objects;
        this.defaultSources = memoize(source).getAsFileTree();
        this.sourceSpecs = objects.domainObjectSet(SourceFilterSpec.class);
        getCppSource().from((Callable<?>) () -> defaultSources);

        getSourceCompileFlags().all(bucket -> {
            bucket.getIdentifier().value("sources" + nextEntry++).disallowChanges();
        });
    }

    private FileCollection memoize(FileCollection s) {
        SetProperty<FileSystemLocation> result = objects.setProperty(FileSystemLocation.class);
        result.value(s.getElements()).finalizeValueOnRead();
        return objects.fileCollection().from(result);
    }

    public abstract ConfigurableFileCollection getCppSource();

    public CompileFlags forSource(Spec<? super File> filterAction) {
        SourceFilterSpec specEntry = objects.newInstance(SourceFilterSpec.class, filterAction);
        sourceSpecs.add(specEntry);

        // Add additional compile flags to matching single source bucket
        getSourceCompileFlags().withType(SingleSourceFileBucket.class).all(new Action<>() {
            @Override
            public void execute(SingleSourceFileBucket entry) {
                entry.getAdditionalCompileFlags().addAll(entry.getCppSourceFile()
                        .map(asFile())
                        .map(filter(negate(specEntry.filterAction)))
                        .map(toConstant(Collections.<String>emptySet()))
                        .orElse(specEntry.getAdditionalCompileFlags().toProvider()));
            }

            private /*static*/ Transformer<File, FileSystemLocation> asFile() {
                return FileSystemLocation::getAsFile;
            }

            // Adapt Predicate#negate() to Gradle Spec
            private /*static*/ <T> Spec<T> negate(Spec<? super T> spec) {
                return it -> !spec.isSatisfiedBy(it);
            }

            private /*static*/ <OUT, IN> Transformer<OUT, IN> toConstant(OUT value) {
                return __ -> value;
            }

            // Backport of Provider#filter(Spec)
            private /*static*/ <T> Transformer<T, T> filter(Spec<? super T> spec) {
                return it -> {
                    if (spec.isSatisfiedBy(it)) {
                        return it;
                    } else {
                        return null;
                    }
                };
            }
        });
        return specEntry.getAdditionalCompileFlags();
    }

    public CompileFlags forSource(File file) {
        final SingleSourceFileBucket entry = entries.computeIfAbsent(file, new Function<>() {
            @Override
            public SingleSourceFileBucket apply(File __) {
                final SingleSourceFileBucket result = objects.newInstance(SingleSourceFileBucket.class);
                final FileCollection cppSource = DefaultCompileFlagsExtension.this.memoize(defaultSources.filter(file::equals));
                result.getCppSourceFile().fileProvider(singleFile(cppSource));
                DefaultCompileFlagsExtension.this.defaultSources = DefaultCompileFlagsExtension.this.memoize(defaultSources.minus(cppSource)).getAsFileTree();
                DefaultCompileFlagsExtension.this.getSourceCompileFlags().add(result);
                return result;
            }

            private /*static*/ Provider<File> singleFile(FileCollection target) {
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
        });
        return entry.getAdditionalCompileFlags();
    }

    public void finalizeExtension() {
        sourceSpecs.all(spec -> {
            final SourceSpecBucket result = objects.newInstance(SourceSpecBucket.class );
            final FileCollection cppSource = memoize(defaultSources.filter(spec.filterAction));
            result.getCppSourceFiles().from(cppSource);
            this.defaultSources = memoize(defaultSources.minus(cppSource)).getAsFileTree();
            getSourceCompileFlags().add(result);
        });
    }

    public abstract DomainObjectSet<CompileFlagsBucket> getSourceCompileFlags();

    public static class DefaultCompileFlags implements CompileFlags {
        private final SetProperty<String> additionalCompileFlags;

        @Inject
        public DefaultCompileFlags(ObjectFactory objects) {
            this.additionalCompileFlags = objects.setProperty(String.class);
        }

        public Provider<Set<String>> toProvider() {
            return additionalCompileFlags;
        }

        @Override
        public CompileFlags add(String item) {
            additionalCompileFlags.add(item);
            return this;
        }

        @Override
        public CompileFlags add(Provider<? extends String> item) {
            additionalCompileFlags.add(item);
            return this;
        }

        @Override
        public CompileFlags addAll(String... items) {
            additionalCompileFlags.addAll(items);
            return this;
        }

        @Override
        public CompileFlags addAll(Iterable<? extends String> items) {
            additionalCompileFlags.addAll(items);
            return this;
        }

        @Override
        public CompileFlags addAll(Provider<? extends Iterable<? extends String>> items) {
            additionalCompileFlags.addAll(items);
            return this;
        }
    }

    public interface CompileFlagsBucket extends Named {
        default String getName() {
            return getIdentifier().get();
        }

        Property<String> getIdentifier();
        Object getCppSource();

        @Nested
        DefaultCompileFlags getAdditionalCompileFlags();
    }

    public static abstract class SingleSourceFileBucket implements CompileFlagsBucket {
        public abstract RegularFileProperty getCppSourceFile();

        @Override
        public Object getCppSource() {
            return getCppSourceFile().map(Collections::singletonList).orElse(Collections.emptyList());
        }
    }

    public static abstract class SourceSpecBucket implements CompileFlagsBucket {
        public abstract ConfigurableFileCollection getCppSourceFiles();

        @Override
        public Object getCppSource() {
            return getCppSourceFiles();
        }
    }

    public static abstract class SourceFilterSpec {
        private final Spec<? super File> filterAction;

        @Inject
        public SourceFilterSpec(Spec<? super File> filterAction) {
            this.filterAction = filterAction;
        }

        @Nested
        public abstract DefaultCompileFlags getAdditionalCompileFlags();
    }
}
