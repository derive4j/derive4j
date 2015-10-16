/*
 * Copyright (c) 2015, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Flavour;
import org.derive4j.Visibility;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.MessageLocalization;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DeriveContext;
import org.derive4j.processor.derivator.BuiltinDerivator;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.derive4j.processor.Unit.unit;

@com.google.auto.service.AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("org.derive4j.Data")
public class DerivingProcessor extends AbstractProcessor {

  private final Set<String> remainingElements = new HashSet<String>();
  private final List<String> errors = new ArrayList<String>();

  private static List<Derivator> derivators() { //TODO
    return StreamSupport.stream(ServiceLoader.load(Derivator.class).spliterator(), false).collect(Collectors.toList());
  }

  private static String deduceDerivedClassName(Derive deriveConf, TypeElement typeElement) {
    return deriveConf.inClass().equals(":auto") ? typeElement.getSimpleName().toString() + "s" : deriveConf.inClass();
  }

  @Override
  public boolean process(final Set<? extends TypeElement> annotations,
                         final RoundEnvironment roundEnv) {
    try {
      if (roundEnv.processingOver()) {
        errors.addAll(remainingElements.stream().map(path -> "Unable to process " + path).collect(Collectors.toList()));
        for (final String error : errors) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error);
        }
      } else {
        final Set<TypeElement> elements = roundEnv.getElementsAnnotatedWith(Data.class).stream().map(element -> (TypeElement) element).collect(Collectors.toSet());
        elements.addAll(remainingElements.stream().map(path -> processingEnv.getElementUtils().getTypeElement(path)).collect(Collectors.toList()));
        remainingElements.clear();
        processElements(elements);
      }
    } catch (final IOException ex) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, ex.getMessage());
    }
    return true;
  }

  private void processElements(final Set<? extends TypeElement> elements) throws IOException {
    AdtParser parser = new AdtParser(processingEnv.getTypeUtils(), processingEnv.getElementUtils());
    BiFunction<AlgebraicDataType, DeriveContext, DeriveResult<DerivedCodeSpec>> derivator = BuiltinDerivator.derivator(parser);
    for (final TypeElement element : elements) {
      try {
        Data dataAnnotation = element.getAnnotation(Data.class);

        DeriveContext deriveContext = new DeriveContext() {
          @Override
          public Flavour flavour() {
            return dataAnnotation.flavour();
          }

          @Override
          public String targetPackage() {
            return Utils.getPackage.visit(element).getQualifiedName().toString();
          }

          @Override
          public String targetClassName() {
            return deduceDerivedClassName(dataAnnotation.value(), element);
          }
        };

        DeriveResult<AlgebraicDataType> parseResult = parser.parseAlgebraicDataType(element);
        Supplier<Unit> effect = parseResult.bind(adt -> derivator.apply(adt, deriveContext))
            .match(
                message -> () -> message.match((msg, localizations) -> {
                  if (localizations.isEmpty()) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
                  } else {
                    localizations.forEach(msgL -> msgL.match(new MessageLocalization.Cases<Unit>() {
                      @Override
                      public Unit onElement(Element e) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
                        return unit;
                      }

                      @Override
                      public Unit onAnnotation(Element e, AnnotationMirror a) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e, a);
                        return unit;
                      }

                      @Override
                      public Unit onAnnotationValue(Element e, AnnotationMirror a, AnnotationValue v) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e, a, v);
                        return unit;
                      }
                    }));
                  }
                  return unit;
                }),
                codeSpec -> () -> {
                  TypeSpec classSpec = codeSpec.match((classes, fields, methods, infos, warnings) ->
                      TypeSpec.classBuilder(deriveContext.targetClassName())
                          .addModifiers(Modifier.FINAL, dataAnnotation.value().withVisbility() == Visibility.Package
                              ? Modifier.FINAL : element.getModifiers().contains(Modifier.PUBLIC) ? Modifier.PUBLIC : Modifier.FINAL)
                          .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                          .addTypes(classes)
                          .addFields(fields)
                          .addMethods(methods).build());
                  JavaFile javaFile = JavaFile.builder(deriveContext.targetPackage(), classSpec).build();
                  try {
                    javaFile.writeTo(processingEnv.getFiler());
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                  return unit;
                });

        effect.get();

      } catch (final RuntimeException ex) {
        errors.add(element + ": " + ex.getMessage());
        ex.printStackTrace(System.err);
      }
    }
  }
}
