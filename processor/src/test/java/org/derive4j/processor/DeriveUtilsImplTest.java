/*
 * Copyright (c) 2019, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * This file is part of "Derive4J - Annotation Processor".
 *
 * "Derive4J - Annotation Processor" is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * "Derive4J - Annotation Processor" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "Derive4J - Annotation Processor".  If not, see <http://www.gnu.org/licenses/>.
 */
package org.derive4j.processor;

import com.google.common.collect.Sets;
import com.google.common.truth.Truth;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import org.junit.Test;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

public class DeriveUtilsImplTest {

  @Test
  public void allAbstractMethods_should_return_abstract_override() {

    Truth.assert_()
        .about(javaSource())
        .that(JavaFileObjects.forSourceString("org.derive4j.processor.TestF",
            "public abstract class TestF<A,B> implements com.google.common.base.Function<A,B>, java.util.function.Function<A,B> {}"))
        .processedWith(new AbstractProcessor() {
          @Override
          public Set<String> getSupportedAnnotationTypes() {

            return Sets.newHashSet("*");
          }

          @Override
          public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

            if (!roundEnv.processingOver()) {
              DeriveUtilsImpl deriveUtils = new DeriveUtilsImpl(
                  processingEnv.getElementUtils(),
                  processingEnv.getTypeUtils(),
                  processingEnv.getSourceVersion(),
                  new DeriveConfigBuilder(processingEnv.getElementUtils()));
              for (TypeElement typeElement : ElementFilter.typesIn(roundEnv.getRootElements())) {
                List<ExecutableElement> abstractMethods = deriveUtils
                    .allAbstractMethods((DeclaredType) typeElement.asType());
                Truth.assertThat(abstractMethods).hasSize(1);
              }

            }
            return false;
          }
        })
        .compilesWithoutError();
  }

}
