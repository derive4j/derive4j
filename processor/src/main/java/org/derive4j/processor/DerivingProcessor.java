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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
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
import org.derive4j.processor.api.DeriveMessages;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.MessageLocalizations;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DeriveConfig;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static org.derive4j.processor.IO.effect;
import static org.derive4j.processor.P2s.P2;
import static org.derive4j.processor.api.DerivedCodeSpecs.getClasses;
import static org.derive4j.processor.api.DerivedCodeSpecs.getFields;
import static org.derive4j.processor.api.DerivedCodeSpecs.getMethods;
import static org.derive4j.processor.api.model.DeriveVisibilities.caseOf;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("*")
public final class DerivingProcessor extends AbstractProcessor {

  private static final Set<ElementKind> scannedElementKinds = EnumSet.of(ElementKind.CLASS, ElementKind.INTERFACE,
      ElementKind.ENUM);
  private List<P2<String, RuntimeException>> remainingElements = Collections.emptyList();
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
      remainingElements.forEach(e -> printErrorMessage(e._1(), e._2()));
    } else {
      final Stream<P2<TypeElement, DeriveConfig>> dataTypeElements = concat(
          remainingElements.stream().map(e -> processingEnv.getElementUtils().getTypeElement(e._1())),
          findAllElements(roundEnv.getRootElements().parallelStream())).sequential()
          .flatMap(e -> deriveConfigBuilder.findDeriveConfig((TypeElement) e));

      remainingElements = new ArrayList<>();
      dataTypeElements.map(e -> {
        String qualifiedName = e._1().getQualifiedName().toString();
        try {
          return P2(qualifiedName, derivation(e._1(), e._2()));
        } catch (RuntimeException err) {
          return P2(qualifiedName, effect(() -> remainingElements.add(P2(qualifiedName, err))));
        }
      }).forEach(io -> {
        try {
          io._2().run();
        } catch (IOException ioe) {
          printErrorMessage(io._1(), ioe);
        }
      });
    }
    return false;
  }

  private IO<Unit> derivation(TypeElement element, DeriveConfig deriveConfig) {

    DeriveResult<AlgebraicDataType> parseResult = adtParser.parseAlgebraicDataType(element, deriveConfig);

    return parseResult.bind(builtinDerivator::derive)
        .match(DeriveMessages.cases()
            .message((msg, localizations) -> localizations.isEmpty()
                ? effect(() -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element))
                : IO.traverse(localizations, MessageLocalizations.cases()
                    .onElement(e -> effect(() -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e)))
                    .onAnnotation((e, annotation) -> effect(
                        () -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e, annotation)))
                    .onAnnotationValue((e, annotation, annotationValue) -> effect(() -> processingEnv.getMessager()
                        .printMessage(Diagnostic.Kind.ERROR, msg, e, annotation, annotationValue)))).voided()), codeSpec -> {
          TypeSpec classSpec = TypeSpec.classBuilder(deriveConfig.targetClass().className())
              .addModifiers(Modifier.FINAL,
                  caseOf(deriveConfig.targetClass().visibility()).Package_(Modifier.FINAL).otherwise_(Modifier.PUBLIC))
              .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
              .addTypes(getClasses(codeSpec))
              .addFields(getFields(codeSpec))
              .addMethods(getMethods(codeSpec))
              .build();

          JavaFile javaFile = JavaFile.builder(deriveConfig.targetClass().className().packageName(), classSpec).build();
          return effect(() -> javaFile.writeTo(processingEnv.getFiler()));
        });
  }

  private void printErrorMessage(String typeElement, Throwable error) {
    processingEnv.getMessager()
        .printMessage(Diagnostic.Kind.ERROR,
            "Derive4J: unable to process " + typeElement + " due to " + error.getMessage() + "\n" + showStackTrace(error));
  }

  private static List<Derivator> derivators() { //TODO
    return StreamSupport.stream(ServiceLoader.load(Derivator.class).spliterator(), false).collect(toList());
  }

  private static Stream<Element> findAllElements(Stream<? extends Element> elements) {
    return elements.filter(e -> scannedElementKinds.contains(e.getKind()))
        .flatMap(e -> concat(Stream.of(e), findAllElements(e.getEnclosedElements().stream())));
  }

  private static String showStackTrace(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    return sw.toString();
  }
}
