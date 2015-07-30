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

import com.google.inject.internal.InjectorImpl.JitLimitation;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.reflection_no_reflection.runtime.BaseReflector;

/**
 * Sets an injectable field.
 */
final class SingleFieldInjector implements SingleMemberInjector {
  final Field field;
  final InjectionPoint injectionPoint;
  final Dependency<?> dependency;
  final BindingImpl<?> binding;

  public SingleFieldInjector(InjectorImpl injector, InjectionPoint injectionPoint, Errors errors)
      throws ErrorsException {
    this.injectionPoint = injectionPoint;
    this.field = (Field) injectionPoint.getMember();
    this.dependency = injectionPoint.getDependencies().get(0);

    // Ewwwww...
    field.setAccessible(true);
    binding = injector.getBindingOrThrow(dependency.getKey(), errors, JitLimitation.NO_JIT);
  }

  public InjectionPoint getInjectionPoint() {
    return injectionPoint;
  }

  public void inject(Errors errors, InternalContext context, Object o) {
    errors = errors.withSource(dependency);

    Dependency previous = context.pushDependency(dependency, binding.getSource());
    try {
      Object value = binding.getInternalFactory().get(errors, context, dependency, false);
        BaseReflector reflector = injectionPoint.getReflector();
        if(reflector!=null) {
            if (!field.getType().isPrimitive()) {
                reflector.setObjectField(o, field.getName(), value);
            }
        } else {
            field.set(o, value);
        }
    } catch (ErrorsException e) {
      errors.withSource(injectionPoint).merge(e.getErrors());
    } catch (IllegalAccessException e) {
      throw new AssertionError(e); // a security manager is blocking us, we're hosed
    } finally {
      context.popStateAndSetDependency(previous);
    }
  }
}
