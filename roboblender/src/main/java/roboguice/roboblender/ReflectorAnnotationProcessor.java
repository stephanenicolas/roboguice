package roboguice.roboblender;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

public class ReflectorAnnotationProcessor implements Processor {
    @Override public Set<String> getSupportedOptions() {
        return new HashSet<String>();
    }

    @Override public Set<String> getSupportedAnnotationTypes() {
        return new HashSet<String>(Arrays.asList("com.google.inject.Inject", "javax.inject.Inject"));
    }

    @Override public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported().latest();
    }

    @Override public void init(ProcessingEnvironment processingEnv) {
        System.out.println("Inside Reflector");
    }

    @Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }

    @Override public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return null;
    }
}
