package com.xilinx.infra.cpp.model;

import java.io.File;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;

public abstract class DefaultCompileFlagsExtension implements CompileFlagsExtension {
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
        return getDSL(defaultSources.filter(filterAction));
    }

    public CompileFlags forSource(FileCollection source) {
        FileTree souceFileTree = source.getAsFileTree();
        return getDSL(defaultSources.filter(it -> souceFileTree.contains(it)));
    }

    public CompileFlags forSourceUnder(File directory) {
        return forSource(this.objects.fileTree().setDir(directory));
    }

    public CompileFlags forSourceUnder(String directory) {
        return forSourceUnder(new File(directory));
    }

    public CompileFlags getDSL(FileCollection source) {
        final CompileFlagsEntry entry = objects.newInstance(CompileFlagsEntry.class, "sources" + nextEntry++);
        entry.getCppSource().from(source);
        this.defaultSources = defaultSources.minus(source).getAsFileTree();
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
        public abstract SetProperty<String> getAdditionalLinuxCompileFlags();
        public abstract SetProperty<String> getAdditionalWindowsCompileFlags();

        private class DefaultCompileFlags implements CompileFlags {
            @Override
            public CompileFlags addOnLnx(String item) {
                getAdditionalLinuxCompileFlags().add(item);
                return this;
            }

            @Override
            public CompileFlags addOnLnx(Provider<? extends String> item) {
                getAdditionalLinuxCompileFlags().add(item);
                return this;
            }

            @Override
            public CompileFlags addAllOnLnx(String... items) {
                getAdditionalLinuxCompileFlags().addAll(items);
                return this;
            }

            @Override
            public CompileFlags addAllOnLnx(Iterable<? extends String> items) {
                getAdditionalLinuxCompileFlags().addAll(items);
                return this;
            }

            @Override
            public CompileFlags addAllOnLnx(Provider<? extends Iterable<? extends String>> items) {
                getAdditionalLinuxCompileFlags().addAll(items);
                return this;
            }

            @Override
            public CompileFlags addOnWin(String item) {
                getAdditionalWindowsCompileFlags().add(item);
                return this;
            }

            @Override
            public CompileFlags addOnWin(Provider<? extends String> item) {
                getAdditionalWindowsCompileFlags().add(item);
                return this;
            }

            @Override
            public CompileFlags addAllOnWin(String... items) {
                getAdditionalWindowsCompileFlags().addAll(items);
                return this;
            }

            @Override
            public CompileFlags addAllOnWin(Iterable<? extends String> items) {
                getAdditionalWindowsCompileFlags().addAll(items);
                return this;
            }

            @Override
            public CompileFlags addAllOnWin(Provider<? extends Iterable<? extends String>> items) {
                getAdditionalWindowsCompileFlags().addAll(items);
                return this;
            }
        }
    }
}
