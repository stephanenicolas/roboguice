/**
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.internal;

import com.google.common.base.Preconditions;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.collect.Lists;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Message;
import com.google.inject.util.Modules;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Creates bindings to methods annotated with {@literal @}{@link Provides}. Use the scope and
 * binding annotations on the provider method to configure the binding.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class ProviderMethodsModule implements Module {
  private final Module delegate;
  private final TypeResolver typeResolver;

  private ProviderMethodsModule(Module delegate) {
    this.delegate = checkNotNull(delegate, "delegate");
    this.typeResolver = new TypeResolver(this.delegate.getClass());
  }

  /**
   * Returns a module which creates bindings for provider methods from the given module.
   */
  public static Module forModule(Module module) {
    // avoid infinite recursion, since installing a module always installs itself
    if (module instanceof ProviderMethodsModule) {
      return Modules.EMPTY_MODULE;
    }

    // don't install provider methods for private modules, they take care of that manually
    if (isPrivateModule(module)) {
      return Modules.EMPTY_MODULE;
    }

    return new ProviderMethodsModule(module);
  }

  private static boolean isPrivateModule(Module module) {
    // use the ugly class name to avoid an even uglier dependency. If private modules ever get
    // incorporated into core, we could use a single instanceof instead of this loop
    for (Class<?> c = module.getClass(); c != Object.class; c = c.getSuperclass()) {
      if (c.getName().equals("com.google.inject.privatemodules.PrivateModule")) {
        return true;
      }
    }
    return false;
  }

  /** See {@link com.google.inject.privatemodules.PrivateModule}. */
  public static ProviderMethodsModule forPrivateModule(Module privateModule) {
    checkArgument(isPrivateModule(privateModule));
    return new ProviderMethodsModule(privateModule);
  }

  public synchronized void configure(Binder binder) {
    for (ProviderMethod<?> providerMethod : getProviderMethods(binder)) {
      providerMethod.configure(binder);
    }
  }

  public List<ProviderMethod<?>> getProviderMethods(Binder binder) {
    List<ProviderMethod<?>> result = Lists.newArrayList();
    for (Class<?> c = delegate.getClass(); c != Object.class; c = c.getSuperclass()) {
      for (Method method : c.getDeclaredMethods()) {
        if (method.isAnnotationPresent(Provides.class)) {
          result.add(createProviderMethod(binder, method));
        }
      }
    }
    return result;
  }

  <T> ProviderMethod<T> createProviderMethod(Binder binder, final Method method) {
    binder = binder.withSource(method);
    Errors errors = new Errors(method);

    // prepare the parameter providers
    List<Provider<?>> parameterProviders = Lists.newArrayList();
    List<Type> parameterTypes = typeResolver.getParameterTypes(method);
    Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    for (int i = 0; i < parameterTypes.size(); i++) {
      Key<?> key = getKey(errors, TypeLiteral.get(parameterTypes.get(i)),
          method, parameterAnnotations[i]);
      parameterProviders.add(binder.getProvider(key));
    }

    // Define T as the method's return type.
    @SuppressWarnings("unchecked") TypeLiteral<T> returnType
        = (TypeLiteral<T>) TypeLiteral.get(typeResolver.getReturnType(method));

    Key<T> key = getKey(errors, returnType, method, method.getAnnotations());
    Class<? extends Annotation> scopeAnnotation
        = Annotations.findScopeAnnotation(errors, method.getAnnotations());

    for (Message message : errors.getMessages()) {
      binder.addError(message);
    }

    return new ProviderMethod<T>(key, method, delegate, parameterProviders, scopeAnnotation);
  }

  <T> Key<T> getKey(Errors errors, TypeLiteral<T> type, Member member, Annotation[] annotations) {
    Annotation bindingAnnotation = Annotations.findBindingAnnotation(errors, member, annotations);
    return bindingAnnotation == null ? Key.get(type) : Key.get(type, bindingAnnotation);
  }

  @Override public boolean equals(Object o) {
    return o instanceof ProviderMethodsModule
        && ((ProviderMethodsModule) o).delegate == delegate;
  }

  @Override public int hashCode() {
    return delegate.hashCode();
  }
}
