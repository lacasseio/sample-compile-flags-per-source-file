import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.NativeToolChain;

import java.io.File;

public interface CompileFlagsExtension {
    CompileFlags forSource(Spec<? super File> filterAction);
    CompileFlags forSource(File file);

    interface CompileInformation {
        Provider<NativeToolChain> getToolChain();
        Provider<NativePlatform> getTargetPlatform();
    }

    interface CompileFlags extends CompileInformation {
        CompileFlags add(String item);

        CompileFlags add(Provider<? extends String> item);

        CompileFlags addAll(String... items);

        CompileFlags addAll(Iterable<? extends String> items);

        CompileFlags addAll(Provider<? extends Iterable<? extends String>> items);
    }
}
