/*
 * Copyright 2010 Henry Coles
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */
package org.pitest.extension.common.testsuitefinder;

import static org.pitest.util.Unchecked.translateCheckedException;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.pitest.extension.TestSuiteFinder;
import org.pitest.functional.F;
import org.pitest.functional.FCollection;
import org.pitest.functional.Option;
import org.pitest.functional.predicate.And;
import org.pitest.functional.predicate.False;
import org.pitest.functional.predicate.Not;
import org.pitest.functional.predicate.Or;
import org.pitest.functional.predicate.Predicate;
import org.pitest.functional.predicate.True;
import org.pitest.internal.ClassPath;
import org.pitest.internal.TestClass;
import org.pitest.reflection.IsSubclassOf;

public class ClasspathSuiteFinder implements TestSuiteFinder {

  public Collection<TestClass> apply(final TestClass a) {
    final ClasspathSuite marker = a.getClazz().getAnnotation(
        ClasspathSuite.class);
    if (marker == null) {
      return Collections.emptyList();
    } else {
      return processClass(a.getClazz(), marker.excludeInnerClasses());
    }
  }

  @SuppressWarnings("unchecked")
  private Collection<TestClass> processClass(final Class<?> clazz,
      final boolean excludeInnerClasses) {
    final Predicate<String> regexFilter = getNameFilter(Option.someOrNone(clazz
        .getAnnotation(ClassNameRegexFilter.class)));
    final Predicate<String> globFilter = getGlobFilter(Option.someOrNone(clazz
        .getAnnotation(ClassNameGlobFilter.class)));

    final Predicate<String> filter = Or.instance(regexFilter, globFilter);

    final ClassPath cp = new ClassPath();

    final ClassLoader cl = Thread.currentThread().getContextClassLoader();
    final F<String, TestClass> f = new F<String, TestClass>() {
      public TestClass apply(final String a) {
        try {
          return new TestClass(cl.loadClass(a));
        } catch (final ClassNotFoundException cnf) {
          throw translateCheckedException(cnf);
        }
      }

    };

    final Iterable<String> unfiltered = cp.classNames();
    final List<String> nameMatches = FCollection.filter(unfiltered, filter);
    final F<TestClass, Boolean> p = createPredicate(clazz, excludeInnerClasses);
    return FCollection.filter(FCollection.map(nameMatches, f), p);

  }

  @SuppressWarnings("unchecked")
  private F<TestClass, Boolean> createPredicate(final Class<?> clazz,
      final boolean excludeInnerClasses) {
    return And.instance(createStandardExcludes(clazz, excludeInnerClasses),
        createExcludePredicate(clazz), createIncludePredicate(clazz));
  }

  private Predicate<TestClass> createStandardExcludes(final Class<?> clazz,
      final boolean excludeInnerClasses) {
    return new Predicate<TestClass>() {

      public Boolean apply(final TestClass a) {
        final Class<?> candidateClass = a.getClazz();
        return !Modifier.isAbstract(candidateClass.getModifiers())
            && !clazz.getName().equals(candidateClass.getName())
            && (!excludeInnerClasses || !candidateClass.getName().contains("$"));
      }

    };
  }

  private Predicate<TestClass> createExcludePredicate(final Class<?> clazz) {
    final ExcludeBaseClassFilter baseClasses = clazz
        .getAnnotation(ExcludeBaseClassFilter.class);
    if (baseClasses != null) {
      final F<Class<?>, Predicate<TestClass>> f = new F<Class<?>, Predicate<TestClass>>() {

        public Predicate<TestClass> apply(final Class<?> a) {
          return Not.instance(new IsSubclassOf(a));
        }

      };

      return And.instance(FCollection
          .map(Arrays.asList(baseClasses.value()), f));
    } else {
      return True.instance();
    }

  }

  private Predicate<TestClass> createIncludePredicate(final Class<?> clazz) {
    final BaseClassFilter baseClasses = clazz
        .getAnnotation(BaseClassFilter.class);
    if (baseClasses != null) {
      final F<Class<?>, Predicate<TestClass>> f = new F<Class<?>, Predicate<TestClass>>() {

        public Predicate<TestClass> apply(final Class<?> a) {
          return new IsSubclassOf(a);
        }

      };

      return And.instance(FCollection
          .map(Arrays.asList(baseClasses.value()), f));
    } else {
      return True.instance();
    }

  }

  private Predicate<String> getNameFilter(
      final Option<ClassNameRegexFilter> annotation) {
    if (annotation.hasSome()) {

      final F<String, Predicate<String>> f = new F<String, Predicate<String>>() {
        public Predicate<String> apply(final String pattern) {
          return new Predicate<String>() {
            public Boolean apply(final String a) {
              return a.matches(pattern);
            }

          };
        }

      };
      return getFilter(f, annotation.value().value());
    } else {
      return False.<String> instance();
    }
  }

  private Predicate<String> getGlobFilter(
      final Option<ClassNameGlobFilter> annotation) {
    if (annotation.hasSome()) {
      final F<String, Predicate<String>> f = new F<String, Predicate<String>>() {
        public Predicate<String> apply(final String pattern) {
          return new Predicate<String>() {
            public Boolean apply(final String a) {
              return a.matches(convertGlobToRegex(pattern));
            }

          };
        }
      };
      return getFilter(f, annotation.value().value());
    } else {
      return False.<String> instance();
    }
  }

  private static String convertGlobToRegex(final String glob) {
    String out = "^";
    for (int i = 0; i < glob.length(); ++i) {
      final char c = glob.charAt(i);
      switch (c) {
      case '*':
        out += ".*";
        break;
      case '?':
        out += '.';
        break;
      case '.':
        out += "\\.";
        break;
      case '\\':
        out += "\\\\";
        break;
      default:
        out += c;
      }
    }
    out += '$';
    return out;
  }

  private Predicate<String> getFilter(final F<String, Predicate<String>> f,
      final String[] filter) {
    return Or.<String> instance(FCollection.map(Arrays.asList(filter), f));
  }

}