import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

import java.io.File;

public interface CompileFlagsExtension {
    CompileFlags forSource(Spec<? super File> filterAction);

    interface CompileFlags {
        CompileFlags add(String item);

        CompileFlags add(Provider<? extends String> item);

        CompileFlags addAll(String... items);

        CompileFlags addAll(Iterable<? extends String> items);

        CompileFlags addAll(Provider<? extends Iterable<? extends String>> items);
    }
}
