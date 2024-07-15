import org.gradle.api.Action;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.tasks.CppCompile;

public interface CompileTasks {
    void configureEach(Action<? super CppCompile> action);

    static CompileTasks forBinary(CppBinary binary) {
        return ((ExtensionAware) binary).getExtensions().getByType(CompileTasks.class);
    }
}
