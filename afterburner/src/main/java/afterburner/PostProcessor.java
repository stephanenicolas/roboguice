package afterburner;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import roboguice.inject.ContentView;
import roboguice.inject.InjectFragment;
import roboguice.inject.InjectView;

import com.github.drochetti.javassist.maven.ClassTransformer;

public class PostProcessor extends ClassTransformer {

    @Override
    protected void applyTransformations(final CtClass classToTransform) throws Exception {
        // Actually you must test if it exists, but it's just an example...

        CtMethod onCreateMethod = classToTransform.getDeclaredMethod("onCreate");
        onCreateMethod.instrument( new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                System.out.println("method call "+m.getMethodName());
                if( m.getMethodName().equals("onCreate") ) {
                    int layoutId = -1;
                    try {
                        layoutId = ((ContentView)m.getEnclosingClass().getAnnotation(ContentView.class)).value();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    List<CtField> views = getAllInjectedFieldsForAnnotation(m.getEnclosingClass(), InjectView.class);
                    List<CtField> fragments = getAllInjectedFieldsForAnnotation(m.getEnclosingClass(), InjectFragment.class);
                    String fieldsNameList = extractAllNames(views);
                    System.out.println("super.onCreate call "+m.getMethodName());
                    String string = "$_ = $proceed($$);\n"+
                            "System.out.println(\"Injectable fields in : "+m.getEnclosingClass().getName()+"\");\n"+
                            "System.out.println(\"fields : "+fieldsNameList+"\");\n"+
                            "setContentView("+layoutId+");\n"+
                            injectFragmentStatements(fragments);
                    System.out.println("Injected : " + string);
                    m.replace(string );
                }
            }


        });
        classToTransform.detach();
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
    private String extractAllNames(List<CtField> fields) {
        StringBuffer buffer = new StringBuffer();
        buffer.append('[');
        for (CtField field : fields) {
            buffer.append(field.getName());
            buffer.append(',');
        }
        if( buffer.length() > 1 ) {
            buffer.deleteCharAt(buffer.length()-1);
        }
        buffer.append(']');
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

}
