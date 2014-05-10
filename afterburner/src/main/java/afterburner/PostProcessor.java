package afterburner;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
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
import android.view.View;

import com.github.drochetti.javassist.maven.ClassTransformer;

/**
 * Will inject all fields and fragments from XML.
 *
 * <pre>
 * <ul>
 *   <li>for activities :
 *     <ul>
 *       <li>if they use @ContentView : right after super.onCreate
 *       <li>if they don't use @ContentView : right after setContentView invocation in onCreate
 *       <li>it doesn't matter if you supply your own version of onCreate or setContenView or not.
 *     </ul>
 *   <li>for fragments :
 *     <ul>
 *       <li>right after onViewCreated
 *       <li>views are destroyed right after onViewDestroyed
 *     </ul>
 *   <li>for views :
 *     <ul>
 *       <li>right after onFinishInflate
 *       <li>onFinishInflate is called automatically by Android when inflating a view from XML
 *       <li>onFinishInflate must be called manually in constructors of views with a single context
 * argument. You should invoke it after inflating your layout manually.
 *     </ul>
 * </ul>
 * </pre>
 * @author SNI
 */
public class PostProcessor extends ClassTransformer {

    @Override
    protected boolean filter(CtClass candidateClass) throws Exception {
        boolean isActivity = candidateClass.subclassOf(ClassPool.getDefault().get(Activity.class.getName()));
        boolean isFragment = candidateClass.subclassOf(ClassPool.getDefault().get(Fragment.class.getName()));
        boolean isSupportFragment = candidateClass.subclassOf(ClassPool.getDefault().get(android.support.v4.app.Fragment.class.getName()));
        boolean isView = candidateClass.subclassOf(ClassPool.getDefault().get(View.class.getName()));
        boolean hasAfterBurner = checkIfAfterBurnerAlreadyActive(candidateClass);
        final List<CtField> views = getAllInjectedFieldsForAnnotation(candidateClass, InjectView.class);
        final List<CtField> fragments = getAllInjectedFieldsForAnnotation(candidateClass, InjectFragment.class);
        boolean hasViewsOrFragments = !(views.isEmpty() && fragments.isEmpty());
        return !hasAfterBurner && (isActivity || isFragment || isSupportFragment || isView || hasViewsOrFragments);
    }

    @Override
    protected void applyTransformations(final CtClass classToTransform) throws Exception {
        // Actually you must test if it exists, but it's just an example...
        getLogger().debug("Analysing " + classToTransform);
        boolean isActivity = classToTransform.subclassOf(ClassPool.getDefault().get(Activity.class.getName()));
        boolean isFragment = classToTransform.subclassOf(ClassPool.getDefault().get(Fragment.class.getName()));
        boolean isSupportFragment = classToTransform.subclassOf(ClassPool.getDefault().get(android.support.v4.app.Fragment.class.getName()));
        boolean isView = classToTransform.subclassOf(ClassPool.getDefault().get(View.class.getName()));

        if (isActivity) {
            injectStuffInActivity(classToTransform);
        } else if (isFragment || isSupportFragment) {
            injectStuffInFragment(classToTransform);
        } else if (isView) {
            injectStuffInView(classToTransform);
        } else {
            // in other classes (like view holders)
            injectStuffInClass(classToTransform);
        }
    }

    private void injectStuffInActivity(final CtClass classToTransform) throws NotFoundException, ClassNotFoundException, CannotCompileException {
        int layoutId = getLayoutId(classToTransform);
        final List<CtField> views = getAllInjectedFieldsForAnnotation(classToTransform, InjectView.class);
        final List<CtField> fragments = getAllInjectedFieldsForAnnotation(classToTransform, InjectFragment.class);
        if (views.isEmpty() && fragments.isEmpty()) {
            return;
        }
        CtMethod onCreateMethod = extractExistingMethod(classToTransform, "onCreate");
        if (onCreateMethod != null) {
            boolean isCallingSetContentView = checkIfMethodIsInvoked(classToTransform, onCreateMethod, "setContentView");
            String insertionMethod = "onCreate";
            if (isCallingSetContentView) {
                layoutId = -1;
                insertionMethod = "setContentView";
            }
            InjectorEditor injectorEditor = new InjectorEditor(classToTransform, fragments, views, layoutId, insertionMethod);
            onCreateMethod.instrument(injectorEditor);
        } else {
            classToTransform.addMethod(CtNewMethod.make(createOnCreateMethod(classToTransform, views, fragments, layoutId), classToTransform));
        }
        classToTransform.detach();
        injectStuffInActivity(classToTransform.getSuperclass());
    }

    private void injectStuffInFragment(final CtClass classToTransform) throws NotFoundException, ClassNotFoundException, CannotCompileException {
        final List<CtField> views = getAllInjectedFieldsForAnnotation(classToTransform, InjectView.class);
        final List<CtField> fragments = getAllInjectedFieldsForAnnotation(classToTransform, InjectFragment.class);
        if (views.isEmpty() && fragments.isEmpty()) {
            return;
        }
        // create or complete onViewCreated
        CtMethod onViewCreatedMethod = extractExistingMethod(classToTransform, "onViewCreated");
        System.out.println("onViewCreatedMethod : " + onViewCreatedMethod);
        if (onViewCreatedMethod != null) {
            InjectorEditor injectorEditor = new InjectorEditor(classToTransform, fragments, views, -1, "onViewCreated");
            onViewCreatedMethod.instrument(injectorEditor);
        } else {
            classToTransform.addMethod(CtNewMethod.make(createOnViewCreatedMethod(classToTransform, views, fragments), classToTransform));
        }
        // create or complete onDestroyView
        CtMethod onDestroyViewMethod = extractExistingMethod(classToTransform, "onDestroyView");
        System.out.println("onDestroyView : " + onDestroyViewMethod);
        if (onDestroyViewMethod != null) {
            InjectorEditor injectorEditor = new InjectorEditor(classToTransform, fragments, views, -1, "onDestroyView");
            onDestroyViewMethod.instrument(injectorEditor);
        } else {
            classToTransform.addMethod(CtNewMethod.make(createOnDestroyViewMethod(classToTransform, views), classToTransform));
        }
        classToTransform.detach();
        injectStuffInFragment(classToTransform.getSuperclass());
    }

    private void injectStuffInView(final CtClass classToTransform) throws NotFoundException, ClassNotFoundException, CannotCompileException {
        final List<CtField> views = getAllInjectedFieldsForAnnotation(classToTransform, InjectView.class);
        if (views.isEmpty()) {
            return;
        }
        CtMethod onFinishInflate = extractExistingMethod(classToTransform, "onFinishInflate");
        System.out.println("onFinishInflateMethod : " + onFinishInflate);
        if (onFinishInflate != null) {
            InjectorEditor injectorEditor = new InjectorEditor(classToTransform, new ArrayList<CtField>(), views, -1, "onFinishInflate");
            onFinishInflate.instrument(injectorEditor);
        } else {
            classToTransform.addMethod(CtNewMethod.make(createOnFinishInflateMethod(classToTransform, views), classToTransform));
        }
        classToTransform.detach();
        injectStuffInView(classToTransform.getSuperclass());
    }

    private void injectStuffInClass(final CtClass clazz) throws NotFoundException, ClassNotFoundException, CannotCompileException {
        final List<CtField> views = getAllInjectedFieldsForAnnotation(clazz, InjectView.class);
        final List<CtField> fragments = getAllInjectedFieldsForAnnotation(clazz, InjectFragment.class);
        if (views.isEmpty() && fragments.isEmpty()) {
            return;
        }
        // create or complete onViewCreated
        List<CtConstructor> constructorList = extractExistingConstructors(clazz);
        System.out.println("constructor : " + constructorList.toString());
        if ( !constructorList.isEmpty() ) {
            for (CtConstructor constructor : constructorList) {
                constructor.insertBeforeBody(createInjectedBodyWithParam(clazz, constructor.getParameterTypes()[0],views, fragments, -1));
            }
        } else {
            getLogger().warn("No suitable constructor was found in class {}. Add a constructor with a single argument : Activity, Fragment or View. Don't use non static inner classes.", clazz.getName());
        }
        clazz.detach();
        injectStuffInFragment(clazz.getSuperclass());
    }

    private boolean checkIfMethodIsInvoked(final CtClass clazz, CtMethod withinMethod, String invokedMEthod) throws CannotCompileException {
        DetectMethodCallEditor dectectSetContentViewEditor = new DetectMethodCallEditor(clazz, invokedMEthod);
        withinMethod.instrument(dectectSetContentViewEditor);
        boolean isCallingSetContentView = dectectSetContentViewEditor.isCallingMethod();
        return isCallingSetContentView;
    }

    private String createOnCreateMethod(CtClass clazz, List<CtField> views, List<CtField> fragments, int layoutId) throws ClassNotFoundException, NotFoundException {
        return "public void onCreate(android.os.Bundle savedInstanceState) { \n" + "super.onCreate(savedInstanceState);\n" + createInjectedBody(clazz, views, fragments, layoutId) + "}";
    }

    private String createOnViewCreatedMethod(CtClass clazz, List<CtField> views, List<CtField> fragments) throws ClassNotFoundException, NotFoundException {
        return "public void onViewCreated(android.view.View view, android.os.Bundle savedInstanceState) { \n" + "super.onViewCreated(view, savedInstanceState);\n"
                + createInjectedBody(clazz, views, fragments, -1) + "}";
    }

    private String createOnFinishInflateMethod(CtClass clazz, List<CtField> views) throws ClassNotFoundException, NotFoundException {
        return "public void onFinishInflate() { \n" + "super.onFinishInflate();\n" + createInjectedBody(clazz, views) + "}";
    }

    private String createOnDestroyViewMethod(CtClass clazz, List<CtField> views) {
        return "public void onDestroyView() { \n" + "super.onDestroyView();\n" + destroyViewStatements(views) + "}";
    }

    private CtMethod extractExistingMethod(final CtClass classToTransform, String methodName) {
        try {
            return classToTransform.getDeclaredMethod(methodName);
        } catch (Exception e) {
            return null;
        }
    }

    private List<CtConstructor> extractExistingConstructors(final CtClass classToTransform) {
        try {
            List<CtConstructor> constructors = new ArrayList<CtConstructor>();
            CtConstructor[] declaredConstructors = classToTransform.getDeclaredConstructors();
            for (CtConstructor constructor : declaredConstructors) {
                CtClass[] paramClasses = constructor.getParameterTypes();
                if( paramClasses.length == 1 ) {
                    if( paramClasses[0].subclassOf(ClassPool.getDefault().get(View.class.getName()))) {
                        constructors.add(constructor);
                    }
                    if( paramClasses[0].subclassOf(ClassPool.getDefault().get(Activity.class.getName()))) {
                        constructors.add(constructor);
                    }
                    if( paramClasses[0].subclassOf(ClassPool.getDefault().get(Fragment.class.getName()))) {
                        constructors.add(constructor);
                    }
                    if( paramClasses[0].subclassOf(ClassPool.getDefault().get(android.support.v4.app.Fragment.class.getName()))) {
                        constructors.add(constructor);
                    }
                }
            }
            return constructors;
        } catch (Exception e) {
            return null;
        }
    }

    private int getLayoutId(final CtClass classToTransform) {
        try {
            return ((ContentView) classToTransform.getAnnotation(ContentView.class)).value();
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
        classToTransform.addField(new CtField(CtClass.booleanType, "afterBurnerActive", classToTransform));
    }

    private String injectContentView(int layoutId) {
        return "setContentView(" + layoutId + ");\n";
    }

    private String injectFragmentStatements(List<CtField> fragments, String root, boolean useChildFragmentManager) throws ClassNotFoundException, NotFoundException {
        StringBuffer buffer = new StringBuffer();
        for (CtField field : fragments) {
            int id = ((InjectFragment) field.getAnnotation(InjectFragment.class)).value();
            String tag = ((InjectFragment) field.getAnnotation(InjectFragment.class)).tag();
            boolean isUsingId = id != -1;
            buffer.append(field.getName());
            buffer.append(" = ");
            buffer.append('(');
            CtClass fragmentType = field.getType();
            buffer.append(fragmentType.getName());
            buffer.append(')');
            boolean isUsingSupport = !fragmentType.subclassOf(ClassPool.getDefault().get(Fragment.class.getName()));
            String getFragmentManagerString;
            if (useChildFragmentManager) {
                getFragmentManagerString = "getChildFragmentManager()";
            } else if (isUsingSupport)
                getFragmentManagerString = "getSupportFragmentManager()";
            else
                getFragmentManagerString = "getFragmentManager()";
            String getFragmentString = isUsingId ? ".findFragmentById(" + id + ")" : ".findFragmentByTag(" + tag + ")";
            buffer.append(root + "." + getFragmentManagerString + getFragmentString + ";\n");
        }
        return buffer.toString();
    }

    private String injectViewStatements(List<CtField> viewsToInject, String root) throws ClassNotFoundException, NotFoundException {
        StringBuffer buffer = new StringBuffer();
        for (CtField field : viewsToInject) {
            int id = ((InjectView) field.getAnnotation(InjectView.class)).value();
            String tag = ((InjectView) field.getAnnotation(InjectView.class)).tag();
            boolean isUsingId = id != -1;

            buffer.append(field.getName());
            buffer.append(" = ");
            buffer.append('(');
            buffer.append(field.getType().getName());
            buffer.append(')');

            String findViewString = isUsingId ? "findViewById(" + id + ")" : "findViewByTag(" + tag + ")";
            buffer.append(root + "." + findViewString + ";\n");
        }
        return buffer.toString();
    }

    private String destroyViewStatements(List<CtField> viewsToInject) {
        StringBuffer buffer = new StringBuffer();
        for (CtField field : viewsToInject) {
            buffer.append(field.getName());
            buffer.append(" = null;\n");
        }
        return buffer.toString();
    }

    private List<CtField> getAllInjectedFieldsForAnnotation(CtClass clazz, Class<? extends Annotation> annotationClazz) {
        List<CtField> result = new ArrayList<CtField>();
        CtField[] allFields = clazz.getDeclaredFields();
        getLogger().debug("Scanning fields in " + clazz.getName());
        for (CtField field : allFields) {
            getLogger().debug("Discovered field " + field.getName());
            if (field.hasAnnotation(annotationClazz)) {
                result.add(field);
            }
        }
        return result;
    }

    private String createInjectedBody(CtClass clazz, List<CtField> views) throws ClassNotFoundException, NotFoundException {
        return createInjectedBody(clazz, views, new ArrayList<CtField>(), -1);
    }

    private String createInjectedBody(CtClass clazz, List<CtField> views, List<CtField> fragments, int layoutId) throws ClassNotFoundException, NotFoundException {
        boolean isActivity = clazz.subclassOf(ClassPool.getDefault().get(Activity.class.getName()));
        boolean isFragment = clazz.subclassOf(ClassPool.getDefault().get(Fragment.class.getName()));
        boolean isSupportFragment = clazz.subclassOf(ClassPool.getDefault().get(android.support.v4.app.Fragment.class.getName()));
        boolean isView = clazz.subclassOf(ClassPool.getDefault().get(View.class.getName()));
        boolean hasViewsOrFragments = !(views.isEmpty() && fragments.isEmpty());

        StringBuffer buffer = new StringBuffer();
        String message = String.format("Class %s has been enhanced.", clazz.getName());
        buffer.append("android.util.Log.d(\"RoboGuice post-processor\",\"" + message + "\");\n");

        if (layoutId != -1) {
            buffer.append(injectContentView(layoutId));
        }
        if (!views.isEmpty()) {
            if (isActivity || isView) {
                buffer.append(injectViewStatements(views, "this"));
            } else if (isFragment || isSupportFragment ) {
                buffer.append(injectViewStatements(views, "$1"));
            }
        }
        if (!fragments.isEmpty()) {
            if (isActivity) {
                buffer.append(injectFragmentStatements(fragments, "this", false));
            } else if (isFragment || isSupportFragment) {
                buffer.append(injectFragmentStatements(fragments, "this", true));
            } else if( hasViewsOrFragments ) {
                buffer.append(injectFragmentStatements(fragments, "$1", true));
            }
        }
        String string = buffer.toString();
        return string;
    }

    private String createInjectedBodyWithParam(CtClass clazz, CtClass paramClass, List<CtField> views, List<CtField> fragments, int layoutId) throws ClassNotFoundException, NotFoundException {
        boolean isActivity = paramClass.subclassOf(ClassPool.getDefault().get(Activity.class.getName()));
        boolean isFragment = paramClass.subclassOf(ClassPool.getDefault().get(Fragment.class.getName()));
        boolean isSupportFragment = paramClass.subclassOf(ClassPool.getDefault().get(android.support.v4.app.Fragment.class.getName()));

        StringBuffer buffer = new StringBuffer();
        String message = String.format("Class %s has been enhanced.", clazz.getName());
        buffer.append("android.util.Log.d(\"RoboGuice post-processor\",\"" + message + "\");\n");

        if (layoutId != -1) {
            buffer.append(injectContentView(layoutId));
        }
        if (!views.isEmpty()) {
            buffer.append(injectViewStatements(views, "$1"));
        }
        if (!fragments.isEmpty()) {
            if (isActivity) {
                buffer.append(injectFragmentStatements(fragments, "$1", false));
            } else if (isFragment || isSupportFragment) {
                buffer.append(injectFragmentStatements(fragments, "$1", true));
            }
        }
        String string = buffer.toString();
        return string;
    }


    private final class InjectorEditor extends ExprEditor {
        private final CtClass classToTransform;
        private final List<CtField> fragments;
        private final List<CtField> views;
        private final int layoutId;
        private String insertionMethod;

        private InjectorEditor(CtClass classToTransform, List<CtField> fragments, List<CtField> views, int layoutId, String insertionMethod) {
            this.classToTransform = classToTransform;
            this.fragments = fragments;
            this.views = views;
            this.layoutId = layoutId;
            this.insertionMethod = insertionMethod;
        }

        @Override
        public void edit(MethodCall m) throws CannotCompileException {
            try {
                getLogger().debug("method call " + m.getMethodName());
                if (m.getMethodName().equals(insertionMethod)) {
                    getLogger().debug("insertion method " + m.getMethodName());

                    String string;
                    string = "$_ = $proceed($$);\n" + createInjectedBody(m.getEnclosingClass(), views, fragments, layoutId);
                    getLogger().debug("Injected : " + string);

                    m.replace(string);
                    // mark class to avoid duplicate
                    markAfterBurnerActiveInClass(classToTransform);
                    getLogger().info("Class {} has been enhanced.", classToTransform.getName());
                }
            } catch (ClassNotFoundException e) {
                throw new CannotCompileException("Class not found during class transformation", e);
            } catch (NotFoundException e) {
                throw new CannotCompileException("Annotation not found during class transformation", e);
            }
        }

    }

    private final class DetectMethodCallEditor extends ExprEditor {

        private String methodName;
        private boolean isCallingMethod;

        private DetectMethodCallEditor(CtClass classToTransform, String methodName) {
            this.methodName = methodName;
        }

        @Override
        public void edit(MethodCall m) throws CannotCompileException {
            if (m.getMethodName().equals(methodName)) {
                this.isCallingMethod = true;
            }
        }

        public boolean isCallingMethod() {
            return isCallingMethod;
        }

    }

}
