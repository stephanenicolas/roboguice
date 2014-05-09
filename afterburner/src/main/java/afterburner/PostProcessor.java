package afterburner;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import roboguice.inject.ContentView;
import roboguice.inject.InjectFragment;
import roboguice.inject.InjectView;
import android.app.Activity;

import com.github.drochetti.javassist.maven.ClassTransformer;

public class PostProcessor extends ClassTransformer {

    @Override
    protected void applyTransformations(final CtClass classToTransform) throws Exception {
        // Actually you must test if it exists, but it's just an example...
        System.out.println("Analysing "+classToTransform);
        injectStuffInClass(classToTransform);
    }

    private void injectStuffInClass(final CtClass classToTransform) throws NotFoundException, ClassNotFoundException, CannotCompileException {
        boolean isActivity = classToTransform.subclassOf(ClassPool.getDefault().get(Activity.class.getName()));
        boolean hasAfterBurner = checkIfAfterBurnerAlreadyActive(classToTransform);
        if( isActivity && !hasAfterBurner ) {
            int layoutId = getLayoutId(classToTransform);
            final List<CtField> views = getAllInjectedFieldsForAnnotation(classToTransform, InjectView.class);
            final List<CtField> fragments = getAllInjectedFieldsForAnnotation(classToTransform, InjectFragment.class);
            if( views.isEmpty() && fragments.isEmpty() ) {
                return;
            }
            CtMethod onCreateMethod = extractOnCreateMethod(classToTransform);
            if( onCreateMethod != null ) {
                DetectMethodCallEditor dectedSetContentViewEditor = new DetectMethodCallEditor(classToTransform, "setContentView");
                onCreateMethod.instrument(dectedSetContentViewEditor);
                boolean isCallingSetContentView = dectedSetContentViewEditor.isCallingMethod();
                InjectorEditor injectorEditor = new InjectorEditor(classToTransform, fragments, views, layoutId, isCallingSetContentView);
                onCreateMethod.instrument( injectorEditor);
            } else {
                classToTransform.addMethod( CtNewMethod.make(createOnCreateBody(classToTransform, views, fragments, layoutId)
                                , classToTransform));
            }
            classToTransform.detach();
            injectStuffInClass( classToTransform.getSuperclass() );
        } else {
            //must be thrown, otherwise, breaks bytecode
            throw new RuntimeException("Not an activity");
        }
    }

    private String createOnCreateBody(CtClass classToTransform, List<CtField> views, List<CtField> fragments, int layoutId) {
        return "public void onCreate(android.os.Bundle bundle) { \n"
                + "super.onCreate(bundle);\n"
                + createInjectedBody(classToTransform, views, fragments, layoutId, false)
                + "}";

    }

    private CtMethod extractOnCreateMethod(final CtClass classToTransform) {
        CtMethod onCreateMethod = null;
        try {
            onCreateMethod = classToTransform.getDeclaredMethod("onCreate");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return onCreateMethod;
    }

    private int getLayoutId(final CtClass classToTransform) {
        int layoutId = -1;
        try {
            layoutId = ((ContentView)classToTransform.getAnnotation(ContentView.class)).value();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return layoutId;
    }

    private boolean checkIfAfterBurnerAlreadyActive(final CtClass classToTransform) {
        boolean hasAfterBurner = false;
        try {
            classToTransform.getDeclaredField("afterBurnerActive");
            hasAfterBurner = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return hasAfterBurner;
    }

    private void markAfterBurnerActiveInClass(final CtClass classToTransform) throws CannotCompileException {
        classToTransform.addField(new CtField(CtClass.booleanType,"afterBurnerActive", classToTransform));
    }


    private String injectContentView(int layoutId) {
        return "setContentView("+layoutId+");\n";
    }

    private String injectFragmentStatements(List<CtField> fragments) {
        StringBuffer buffer = new StringBuffer();
        try {
            for( CtField field : fragments ) {
                int id;
                id = ((InjectFragment)field.getAnnotation(InjectFragment.class)).value();
                buffer.append( field.getName() );
                buffer.append( " = " );
                buffer.append( '(' );
                buffer.append( field.getType().getName() );
                buffer.append( ')' );
                buffer.append( "getFragmentManager().findFragmentById("+id+");\n" );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return buffer.toString();
    }

    private String injectViewStatements(List<CtField> views) {
        StringBuffer buffer = new StringBuffer();
        try {
            for( CtField field : views ) {
                int id;
                id = ((InjectFragment)field.getAnnotation(InjectFragment.class)).value();
                buffer.append( field.getName() );
                buffer.append( " = " );
                buffer.append( '(' );
                buffer.append( field.getType().getName() );
                buffer.append( ')' );
                buffer.append( "findViewById("+id+");\n" );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return buffer.toString();
    }

    private List<CtField> getAllInjectedFieldsForAnnotation(CtClass clazz, Class<? extends Annotation> annotationClazz) {
        List<CtField> result = new ArrayList<CtField>();
        CtField[] allFields = clazz.getDeclaredFields();
        System.out.println("Scanning fields in "+clazz.getName());
        for (CtField field : allFields) {
            System.out.println("Discovered field "+field.getName());
            if( field.hasAnnotation(annotationClazz) ) {
                result.add(field);
            }
        }
        return result;
    }

    private String createInjectedBody(CtClass classToTransform, List<CtField> views, List<CtField> fragments, int layoutId, boolean isCallingSetContentView) {
        StringBuffer buffer = new StringBuffer();
        String message = String.format("Class %s has been enhanced.", classToTransform.getName());
        buffer.append("android.util.Log.d(\"RoboGuice post-processor\",\""+message+"\");\n");

        if( layoutId != -1 && !isCallingSetContentView ) { 
            buffer.append(injectContentView(layoutId));
        }
        if( !views.isEmpty() ) {
            buffer.append(injectViewStatements(views));
        }
        if( !fragments.isEmpty() ) {
            buffer.append(injectFragmentStatements(fragments));
        }
        String string = buffer.toString();
        return string;
    }


    private final class InjectorEditor extends ExprEditor {
        private final CtClass classToTransform;
        private final List<CtField> fragments;
        private final List<CtField> views;
        private final int layoutId;
        private boolean isCallingSetContentView;

        private InjectorEditor(CtClass classToTransform, List<CtField> fragments, List<CtField> views, int layoutId, boolean isCallingSetContentView) {
            this.classToTransform = classToTransform;
            this.fragments = fragments;
            this.views = views;
            this.layoutId = layoutId;
            this.isCallingSetContentView = isCallingSetContentView;
        }

        @Override
        public void edit(MethodCall m) throws CannotCompileException {
            System.out.println("method call "+m.getMethodName());
            String insertionMethod = isCallingSetContentView ? "setContentView" : "onCreate";
            if( m.getMethodName().equals(insertionMethod) ) {
                System.out.println("insertion method "+m.getMethodName());

                String string = "$_ = $proceed($$);\n"+createInjectedBody(m.getEnclosingClass(), views, fragments, layoutId, isCallingSetContentView );
                System.out.println("Injected : " + string);

                m.replace(string );
                //mark class to avoid duplicate
                markAfterBurnerActiveInClass(classToTransform);
            }
        }

    }

    private final class DetectMethodCallEditor extends ExprEditor {

        private String methodName;
        private boolean isCallingMethod;

        private DetectMethodCallEditor(CtClass classToTransform, String methodName ) {
            this.methodName = methodName;
        }

        @Override
        public void edit(MethodCall m) throws CannotCompileException {
            if( m.getMethodName().equals(methodName) ) {
                this.isCallingMethod = true;
            }
        }

        public boolean isCallingMethod() {
            return isCallingMethod;
        }

    }

}
