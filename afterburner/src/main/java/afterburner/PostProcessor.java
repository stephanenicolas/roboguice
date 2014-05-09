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
import android.app.Fragment;

import com.github.drochetti.javassist.maven.ClassTransformer;

public class PostProcessor extends ClassTransformer {

    @Override
    protected void applyTransformations(final CtClass classToTransform) throws Exception {
        // Actually you must test if it exists, but it's just an example...
        getLogger().debug("Analysing "+classToTransform);
        injectStuffInClass(classToTransform);
    }

    @Override
    protected boolean filter(CtClass candidateClass) throws Exception {
        boolean isActivity = candidateClass.subclassOf(ClassPool.getDefault().get(Activity.class.getName()));
        boolean hasAfterBurner = checkIfAfterBurnerAlreadyActive(candidateClass);

        return isActivity && !hasAfterBurner;
    }    

    private void injectStuffInClass(final CtClass classToTransform) throws NotFoundException, ClassNotFoundException, CannotCompileException {
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
    }

    private String createOnCreateBody(CtClass classToTransform, List<CtField> views, List<CtField> fragments, int layoutId) throws ClassNotFoundException, NotFoundException {
        return "public void onCreate(android.os.Bundle bundle) { \n"
                + "super.onCreate(bundle);\n"
                + createInjectedBody(classToTransform, views, fragments, layoutId, false)
                + "}";

    }

    private CtMethod extractOnCreateMethod(final CtClass classToTransform) {
        try {
            return classToTransform.getDeclaredMethod("onCreate");
        } catch (Exception e) {
            return null;
        }
    }

    private int getLayoutId(final CtClass classToTransform) {
        try {
            return ((ContentView)classToTransform.getAnnotation(ContentView.class)).value();
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean checkIfAfterBurnerAlreadyActive(final CtClass classToTransform) {
        try {
            classToTransform.getDeclaredField("afterBurnerActive");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void markAfterBurnerActiveInClass(final CtClass classToTransform) throws CannotCompileException {
        classToTransform.addField(new CtField(CtClass.booleanType,"afterBurnerActive", classToTransform));
    }


    private String injectContentView(int layoutId) {
        return "setContentView("+layoutId+");\n";
    }

    private String injectFragmentStatements(List<CtField> fragments) throws ClassNotFoundException, NotFoundException {
        StringBuffer buffer = new StringBuffer();
        for( CtField field : fragments ) {
            int id = ((InjectFragment)field.getAnnotation(InjectFragment.class)).value();
            String tag = ((InjectFragment)field.getAnnotation(InjectFragment.class)).tag();
            boolean isUsingId = id != -1;
            buffer.append( field.getName() );
            buffer.append( " = " );
            buffer.append( '(' );
            CtClass fragmentType = field.getType();
            buffer.append( fragmentType.getName() );
            buffer.append( ')' );
            boolean isUsingSupport = !fragmentType.subclassOf(ClassPool.getDefault().get(Fragment.class.getName()));
            String getFragmentManagerString = isUsingSupport ? "getSupportFragmentManager()" : "getFragmentManager()";
            String getFragmentString = isUsingId ? ".findFragmentById("+id+")" : ".findFragmentByTag("+tag+")";
            buffer.append( getFragmentManagerString+getFragmentString+";\n" );
        }
        return buffer.toString();
    }

    private String injectViewStatements(List<CtField> views) throws ClassNotFoundException, NotFoundException {
        StringBuffer buffer = new StringBuffer();
        for( CtField field : views ) {
            int id;
            id = ((InjectView)field.getAnnotation(InjectView.class)).value();
            String tag = ((InjectView)field.getAnnotation(InjectView.class)).tag();
            boolean isUsingId = id != -1;

            buffer.append( field.getName() );
            buffer.append( " = " );
            buffer.append( '(' );
            buffer.append( field.getType().getName() );
            buffer.append( ')' );

            String findViewString = isUsingId ? "findViewById("+id+")" : "findViewByTag("+tag+")";
            buffer.append( findViewString+";\n" );
        }
        return buffer.toString();
    }

    private List<CtField> getAllInjectedFieldsForAnnotation(CtClass clazz, Class<? extends Annotation> annotationClazz) {
        List<CtField> result = new ArrayList<CtField>();
        CtField[] allFields = clazz.getDeclaredFields();
        getLogger().debug("Scanning fields in "+clazz.getName());
        for (CtField field : allFields) {
            getLogger().debug("Discovered field "+field.getName());
            if( field.hasAnnotation(annotationClazz) ) {
                result.add(field);
            }
        }
        return result;
    }

    private String createInjectedBody(CtClass classToTransform, List<CtField> views, List<CtField> fragments, int layoutId, boolean isCallingSetContentView) throws ClassNotFoundException, NotFoundException {
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
            try {
                getLogger().debug("method call "+m.getMethodName());
                String insertionMethod = isCallingSetContentView ? "setContentView" : "onCreate";
                if( m.getMethodName().equals(insertionMethod) ) {
                    getLogger().debug("insertion method "+m.getMethodName());

                    String string;
                    string = "$_ = $proceed($$);\n"+createInjectedBody(m.getEnclosingClass(), views, fragments, layoutId, isCallingSetContentView );
                    getLogger().debug("Injected : " + string);

                    m.replace(string );
                    //mark class to avoid duplicate
                    markAfterBurnerActiveInClass(classToTransform);
                    getLogger().info("Class {} has been enhanced.", classToTransform.getName());
                }
            } catch (ClassNotFoundException e) {
                throw new CannotCompileException("Class not found during class transformation",e);
            } catch (NotFoundException e) {
                throw new CannotCompileException("Annotation not found during class transformation",e);
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
