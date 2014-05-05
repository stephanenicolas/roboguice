package afterburner;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;

import com.github.drochetti.javassist.maven.ClassTransformer;

public class PostProcessor extends ClassTransformer {

    private String appendValue = "steff";

    @Override
    protected void applyTransformations(CtClass classToTransform) throws Exception {
        // Actually you must test if it exists, but it's just an example...
        CtMethod toStringMethod;
        try {
            toStringMethod = classToTransform.getDeclaredMethod("toString");
            classToTransform.removeMethod(toStringMethod);
        } catch (Exception e) {
            e.printStackTrace();
        }

        CtMethod hackedToStringMethod = CtNewMethod.make(
                "public String toString() { return \"toString() hacked by Javassist"+(this.appendValue  != null ? this.appendValue:"")+"\"; }",
                classToTransform);
        classToTransform.addMethod(hackedToStringMethod);
    }

}
