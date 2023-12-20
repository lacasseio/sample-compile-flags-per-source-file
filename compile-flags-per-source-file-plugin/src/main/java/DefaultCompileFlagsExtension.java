import org.gradle.api.DomainObjectSet;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

abstract class DefaultCompileFlagsExtension implements CompileFlagsExtension {
    private int nextEntry = 0;
    private final ObjectFactory objects;
    private FileTree defaultSources;

    @Inject
    public DefaultCompileFlagsExtension(ObjectFactory objects, FileCollection source) {
        this.objects = objects;
        this.defaultSources = source.getAsFileTree();
        getCppSource().from((Callable<?>) () -> defaultSources);
    }

    public abstract ConfigurableFileCollection getCppSource();

    public CompileFlags forSource(Spec<? super File> filterAction) {
        final CompileFlagsEntry entry = objects.newInstance(CompileFlagsEntry.class, "sources" + nextEntry++);
        final FileCollection cppSource = defaultSources.filter(filterAction);
        entry.getCppSource().from(cppSource);
        this.defaultSources = defaultSources.minus(cppSource).getAsFileTree();
        getSourceCompileFlags().add(entry);
        return entry.dsl;
    }

    public abstract DomainObjectSet<CompileFlagsEntry> getSourceCompileFlags();

    public static abstract class CompileFlagsEntry {
        private final CompileFlags dsl;
        private final String identifier;

        @Inject
        public CompileFlagsEntry(String identifier) {
            this.identifier = identifier;
            this.dsl = new DefaultCompileFlags();
        }

        public String getIdentifier() {
            return identifier;
        }

        public abstract ConfigurableFileCollection getCppSource();
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
}
