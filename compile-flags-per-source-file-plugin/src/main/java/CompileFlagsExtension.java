package com.xilinx.infra.cpp.model;

import java.io.File;

import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

public interface CompileFlagsExtension {
    CompileFlags forSource(Spec<? super File> filterAction);
    CompileFlags forSource(FileCollection cppSource);
    CompileFlags forSourceUnder(File directory);
    CompileFlags forSourceUnder(String directory);

    interface CompileFlags {
        CompileFlags addOnLnx(String item);

        CompileFlags addOnLnx(Provider<? extends String> item);

        CompileFlags addAllOnLnx(String... items);

        CompileFlags addAllOnLnx(Iterable<? extends String> items);

        CompileFlags addAllOnLnx(Provider<? extends Iterable<? extends String>> items);

        CompileFlags addOnWin(String item);

        CompileFlags addOnWin(Provider<? extends String> item);

        CompileFlags addAllOnWin(String... items);

        CompileFlags addAllOnWin(Iterable<? extends String> items);

        CompileFlags addAllOnWin(Provider<? extends Iterable<? extends String>> items);
    }
}
