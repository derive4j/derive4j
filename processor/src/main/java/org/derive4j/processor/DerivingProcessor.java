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
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.MessageLocalizations;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DeriveConfig;
import org.derive4j.processor.api.model.DeriveVisibilities;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static org.derive4j.processor.P2s.P2;
import static org.derive4j.processor.api.DerivedCodeSpecs.getClasses;
import static org.derive4j.processor.api.DerivedCodeSpecs.getFields;
import static org.derive4j.processor.api.DerivedCodeSpecs.getMethods;
import static org.derive4j.processor.api.model.DeriveVisibilities.caseOf;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("*")
public final class DerivingProcessor extends AbstractProcessor {

  private final Set<String> remainingElements = new HashSet<>();
  private final List<String> errors = new ArrayList<>();

  private Derivator builtinDerivator;
  private AdtParser adtParser;
  private DeriveConfigBuilder deriveConfigBuilder;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    DeriveUtilsImpl deriveUtils = new DeriveUtilsImpl(processingEnv.getElementUtils(), processingEnv.getTypeUtils());
    builtinDerivator = BuiltinDerivator.derivator(deriveUtils);
    adtParser = new AdtParser(deriveUtils);
    deriveConfigBuilder = new DeriveConfigBuilder(deriveUtils);
  }

  @Override
  public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

    if (roundEnv.processingOver()) {
      errors.addAll(remainingElements.stream().map(path -> "Unable to process " + path).collect(toList()));
      for (final String error : errors) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error);
      }
    } else {
      final Map<TypeElement, DeriveConfig> elements = concat(
          remainingElements.stream().map(path -> processingEnv.getElementUtils().getTypeElement(path)),
          findAllElements(roundEnv.getRootElements().stream()).filter(e -> (e.getKind() == ElementKind.CLASS) ||
              (e.getKind() == ElementKind.INTERFACE) ||
              (e.getKind() == ElementKind.ENUM))).flatMap(e -> deriveConfigBuilder.findDeriveConfig((TypeElement) e))
          .collect(Collectors.toMap(P2s::get_1, P2s::get_2));

      remainingElements.clear();
      processElements(elements);
    }
    return false;
  }

  private void processElements(final Map<TypeElement, DeriveConfig> dataTypes) {

    dataTypes.entrySet().stream().<P2<TypeElement, Runnable>>map(entry -> {
      TypeElement element = entry.getKey();
      try {
        DeriveConfig deriveConfig = entry.getValue();
        DeriveResult<AlgebraicDataType> parseResult = adtParser.parseAlgebraicDataType(element, deriveConfig);

        Runnable effect = parseResult.bind(builtinDerivator::derive)
            .match(message -> message.<Runnable>match((msg, localizations) -> {
              Runnable report;
              if (localizations.isEmpty()) {
                report = () -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
              } else {
                List<Runnable> reports = localizations.stream()
                    .map(MessageLocalizations.cases().<Runnable>onElement(
                        e -> () -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e)).onAnnotation(
                        (e, annotation) -> () -> processingEnv.getMessager()
                            .printMessage(Diagnostic.Kind.ERROR, msg, e, annotation))
                        .onAnnotationValue((e, annotation, annotationValue) -> () -> processingEnv.getMessager()
                            .printMessage(Diagnostic.Kind.ERROR, msg, e, annotation, annotationValue)))
                    .collect(toList());
                report = () -> reports.forEach(Runnable::run);
              }
              return report;
            }), codeSpec -> {
              TypeSpec classSpec = TypeSpec.classBuilder(deriveConfig.targetClass().className())
                  .addModifiers(Modifier.FINAL, caseOf(deriveConfig.targetClass().visibility())
                      .Package_(Modifier.FINAL)
                      .otherwise_(Modifier.PUBLIC))
                  .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                  .addTypes(getClasses(codeSpec))
                  .addFields(getFields(codeSpec))
                  .addMethods(getMethods(codeSpec))
                  .build();

              JavaFile javaFile = JavaFile.builder(deriveConfig.targetClass().className().packageName(), classSpec).build();
              return () -> {
                try {
                  javaFile.writeTo(processingEnv.getFiler());
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              };
            });
        return P2(element, effect);
      } catch (final RuntimeException | Error ex) {
        return P2(element, () -> {
          reportError(element, ex);
        });
      }
    }).forEachOrdered(p -> {
      try {
        p._2().run();
      } catch (final RuntimeException | Error ex) {
        reportError(p._1(), ex);
      }
    });
  }

  private void reportError(TypeElement element, Throwable ex) {
    errors.add(element + ": " + ex.getMessage());
    ex.printStackTrace(System.err);
  }

  private static List<Derivator> derivators() { //TODO
    return StreamSupport.stream(ServiceLoader.load(Derivator.class).spliterator(), false).collect(toList());
  }

  private static Stream<Element> findAllElements(Stream<? extends Element> elements) {
    return elements.flatMap(e -> concat(Stream.of(e), findAllElements(e.getEnclosedElements().stream())));
  }
}
