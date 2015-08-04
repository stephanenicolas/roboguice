package com.google.inject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.reflection_no_reflection.runtime.BaseReflector;

public abstract class AnnotationDatabase {
    //TODO add the additional packages here and load database impl classes.
    protected AnnotationDatabase() {}

    //TODO rename methods
    public abstract void fillAnnotationClassesAndFieldsNames(HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToFieldSet);
    public abstract void fillAnnotationClassesAndMethods(HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToMethodSet);
    public abstract void fillAnnotationClassesAndConstructors(HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToConstructorSet);
    public abstract void fillClassesContainingInjectionPointSet(HashSet<String> classesContainingInjectionPointsSet);
    public abstract void fillBindableClasses(HashSet<String> injectedClasses);
    public abstract void fillMapClassToReflector(HashMap<String, BaseReflector> injectedClasses);

}
