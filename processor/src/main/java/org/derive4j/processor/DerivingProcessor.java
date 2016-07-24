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

import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Flavour;
import org.derive4j.Make;
import org.derive4j.Visibility;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.MessageLocalizations;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DeriveContext;
import org.derive4j.processor.derivator.BuiltinDerivator;

import static org.derive4j.processor.Unit.unit;
import static org.derive4j.processor.api.DerivedCodeSpecs.getClasses;
import static org.derive4j.processor.api.DerivedCodeSpecs.getFields;
import static org.derive4j.processor.api.DerivedCodeSpecs.getMethods;

@AutoService(Processor.class) @SupportedSourceVersion(SourceVersion.RELEASE_8) @SupportedAnnotationTypes("org.derive4j" +
                                                                                                             ".Data") public final class
DerivingProcessor
    extends AbstractProcessor {

  private final Set<String> remainingElements = new HashSet<>();
  private final List<String> errors = new ArrayList<>();

  private static List<Derivator> derivators() { //TODO
    return StreamSupport.stream(ServiceLoader.load(Derivator.class).spliterator(), false).collect(Collectors.toList());
  }

  private static String deduceDerivedClassName(Derive deriveConf, TypeElement typeElement) {

    return ":auto".equals(deriveConf.inClass())
           ? (typeElement.getSimpleName().toString() + 's')
           : deriveConf.inClass();
  }

  @Override public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

    try {
      if (roundEnv.processingOver()) {
        errors.addAll(remainingElements.stream().map(path -> "Unable to process " + path).collect(Collectors.toList()));
        for (final String error : errors) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error);
        }
      } else {
        final Set<TypeElement> elements = roundEnv.getElementsAnnotatedWith(Data.class)
            .stream()
            .map(element -> (TypeElement) element)
            .collect(Collectors.toSet());
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

    DeriveUtils deriveUtils = new DeriveUtilsImpl(processingEnv.getElementUtils(), processingEnv.getTypeUtils());
    BiFunction<AlgebraicDataType, DeriveContext, DeriveResult<DerivedCodeSpec>> derivator = BuiltinDerivator.derivator(deriveUtils);
    for (final TypeElement element : elements) {
      try {
        Data dataAnnotation = element.getAnnotation(Data.class);

        Set<Make> makes = BuiltinDerivator.makeWithDpendencies(dataAnnotation.value().make());

        DeriveContext deriveContext = new DeriveContext() {
          @Override public Flavour flavour() {

            return dataAnnotation.flavour();
          }

          @Override public Visibility visibility() {

            return dataAnnotation.value().withVisibility();
          }

          @Override public String targetPackage() {

            return Utils.getPackage.visit(element).getQualifiedName().toString();
          }

          @Override public String targetClassName() {

            return deduceDerivedClassName(dataAnnotation.value(), element);
          }

          @Override public Set<Make> makes() {

            return makes;
          }
        };

        DeriveResult<AlgebraicDataType> parseResult = deriveUtils.parseAlgebraicDataType(element);
        Supplier<Unit> effect = parseResult.bind(adt -> derivator.apply(adt, deriveContext))
            .match(message -> () -> message.match((msg, localizations) -> {
              if (localizations.isEmpty()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
              } else {
                localizations.forEach(MessageLocalizations.cases().onElement(e -> {
                  processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
                  return unit;
                }).onAnnotation((e, annotation) -> {
                  processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e, annotation);
                  return unit;
                }).onAnnotationValue((e, annotation, annotationValue) -> {
                  processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e, annotation, annotationValue);
                  return unit;
                })::apply);
              }
              return unit;
            }), codeSpec -> () -> {
              TypeSpec classSpec = TypeSpec.classBuilder(deriveContext.targetClassName())
                  .addModifiers(Modifier.FINAL, (deriveContext.visibility() == Visibility.Package)
                                                ? Modifier.FINAL
                                                : (element.getModifiers().contains(Modifier.PUBLIC)
                                                   ? Modifier.PUBLIC
                                                   : Modifier.FINAL))
                  .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                  .addTypes(getClasses(codeSpec))
                  .addFields(getFields(codeSpec))
                  .addMethods(getMethods(codeSpec))
                  .build();
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
