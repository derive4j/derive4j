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

import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DerivatorFactory;
import org.derive4j.processor.api.DerivatorSelections;
import org.derive4j.processor.api.DeriveMessage;
import org.derive4j.processor.api.DeriveMessages;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.Extension;
import org.derive4j.processor.api.ExtensionFactory;
import org.derive4j.processor.api.MessageLocalizations;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DeriveConfig;
import org.derive4j.processor.api.model.DerivedInstanceConfig;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Stream.concat;
import static org.derive4j.processor.IO.effect;
import static org.derive4j.processor.P2s.P2;
import static org.derive4j.processor.Utils.get;
import static org.derive4j.processor.Utils.optionalAsStream;
import static org.derive4j.processor.api.DeriveMessages.message;
import static org.derive4j.processor.api.DeriveResults.error;
import static org.derive4j.processor.api.DeriveResults.getError;
import static org.derive4j.processor.api.DeriveResults.getResult;
import static org.derive4j.processor.api.DerivedCodeSpecs.getClasses;
import static org.derive4j.processor.api.DerivedCodeSpecs.getFields;
import static org.derive4j.processor.api.DerivedCodeSpecs.getMethods;
import static org.derive4j.processor.api.model.DeriveVisibilities.caseOf;
import static org.derive4j.processor.api.model.DerivedInstanceConfigs.getImplSelector;
import static org.derive4j.processor.api.model.DerivedInstanceConfigs.getTargetClass;

@AutoService(Processor.class)
@SupportedAnnotationTypes("*")
public final class DerivingProcessor extends AbstractProcessor {

  private static final Set<ElementKind>                   scannedElementKinds = EnumSet.of(ElementKind.CLASS,
      ElementKind.INTERFACE, ElementKind.ENUM);
  private final ArrayList<P2<String, RuntimeException>>   remainingElements   = new ArrayList<>();
  private DeriveUtilsImpl                                 deriveUtils;
  private Derivator                                       builtinDerivator;
  private AdtParser                                       adtParser;
  private DeriveConfigBuilder                             deriveConfigBuilder;
  private List<Extension>                                 extensions;
  private Map<P2<ClassName, Optional<String>>, Derivator> derivators;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    deriveConfigBuilder = new DeriveConfigBuilder(processingEnv.getElementUtils());

    deriveUtils = new DeriveUtilsImpl(
        processingEnv.getElementUtils(),
        processingEnv.getTypeUtils(),
        processingEnv.getSourceVersion(),
        deriveConfigBuilder);
    builtinDerivator = BuiltinDerivator.derivator(deriveUtils);
    adtParser = new AdtParser(deriveUtils);
    extensions = loadEextensions(deriveUtils);
    derivators = loadDerivators(deriveUtils);
  }

  @Override
  public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

    if (roundEnv.processingOver()) {
      remainingElements.forEach(e -> printErrorMessage(e._1(), e._2()));
    } else {
      List<P2<TypeElement, DeriveConfig>> parsedRemainingElements = new ArrayList<>();
      remainingElements.forEach(e -> {
        Optional<P2<TypeElement, DeriveConfig>> deriveConfig = deriveConfigBuilder
            .findDeriveConfig(processingEnv.getElementUtils().getTypeElement(e._1()));
        if (!deriveConfig.isPresent()) {
          printErrorMessage(e._1(), e._2());
        }
        deriveConfig.ifPresent(parsedRemainingElements::add);
      });

      final Stream<P2<TypeElement, DeriveConfig>> dataTypeElements = concat(parsedRemainingElements.stream(),
          findAllElements(roundEnv.getRootElements().parallelStream()).sequential()
              .flatMap(e -> optionalAsStream(deriveConfigBuilder.findDeriveConfig((TypeElement) e))));

      remainingElements.clear();
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
        } catch (Exception ioe) {
          printErrorMessage(io._1(), ioe);
        }
      });
    }
    return false;
  }

  private IO<Unit> derivation(TypeElement element, DeriveConfig deriveConfig) {

    DeriveResult<AlgebraicDataType> parseResult = adtParser.parseAlgebraicDataType(element, deriveConfig);

    Function<DeriveMessage, IO<Unit>> messagePrint = mesagePrint(element);

    return parseResult.bind(adt -> builtinDerivator.derive(adt).map(codeSPec -> P2(adt, codeSPec))).match(messagePrint,

        r -> r.match((adt, codeSpec) -> {
          ClassName targetClassName = deriveConfig.targetClass().className();

          IO<Unit> derivedInstances = effect(() -> {
          });
          for (Map.Entry<ClassName, P2<Stream<DeriveMessage>, DerivedCodeSpec>> derivedClass : derivedInstances(adt)
              .entrySet()) {
            ClassName className = derivedClass.getKey();
            if (className.equals(targetClassName)) {
              codeSpec = codeSpec.append(derivedClass.getValue()._2());
            } else {
              TypeSpec classSpec = toTypeSpec(deriveConfig, className, derivedClass.getValue()._2())
                  .addOriginatingElement(element)
                  .build();
              JavaFile javaFile = JavaFile.builder(targetClassName.packageName(), classSpec).build();
              derivedInstances = derivedInstances.then(effect(() -> javaFile.writeTo(processingEnv.getFiler())));
            }
            derivedInstances = derivedClass.getValue()._1().map(messagePrint).reduce(derivedInstances, IO::then);
          }

          TypeSpec classSpec = toTypeSpec(deriveConfig, targetClassName, codeSpec).addOriginatingElement(element)
              .build();

          IO<Unit> extendErrors = effect(() -> {
          });
          for (Extension extension : extensions) {
            DeriveResult<TypeSpec> extendResult = extension.extend(adt, classSpec);
            classSpec = getResult(extendResult).orElse(classSpec);
            extendErrors = getError(extendResult).map(messagePrint).map(extendErrors::then).orElse(extendErrors);
          }

          JavaFile javaFile = JavaFile.builder(targetClassName.packageName(), classSpec).build();

          return effect(() -> javaFile.writeTo(processingEnv.getFiler())).then(derivedInstances).then(extendErrors);
        }));
  }

  private Function<DeriveMessage, IO<Unit>> mesagePrint(TypeElement element) {
    return DeriveMessages.cases()
        .message(
            (msg,
                localizations) -> localizations.isEmpty()
                    ? effect(() -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element))
                    : IO.traverse(localizations, MessageLocalizations.cases()
                        .onElement(
                            e -> effect(() -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e)))
                        .onAnnotation((e, annotation) -> effect(
                            () -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e, annotation)))
                        .onAnnotationValue(
                            (e, annotation,
                                annotationValue) -> effect(() -> processingEnv.getMessager()
                                    .printMessage(Diagnostic.Kind.ERROR, msg, e, annotation, annotationValue))))
                        .voided());
  }

  private TypeSpec.Builder toTypeSpec(DeriveConfig deriveConfig, ClassName targetClassName, DerivedCodeSpec codeSpec) {
    TypeSpec.Builder builder = TypeSpec.classBuilder(targetClassName)
        .addModifiers(Modifier.FINAL,
            caseOf(deriveConfig.targetClass().visibility()).Package_(Modifier.FINAL).otherwise_(Modifier.PUBLIC))
        .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
        .addTypes(getClasses(codeSpec))
        .addFields(getFields(codeSpec))
        .addMethods(getMethods(codeSpec));

    deriveUtils.generatedAnnotation()
        .ifPresent(annotation -> builder.addAnnotation(AnnotationSpec.builder(ClassName.get(annotation))
            .addMember("value", "$S", getClass().getCanonicalName())
            .build()));

    deriveConfig.targetClass().extend().ifPresent(cn -> {
      if (deriveUtils.findTypeElement(cn).get().getKind().isInterface()) {
        builder.addSuperinterface(cn);
      } else {
        builder.superclass(cn);
      }
    });

    return builder;
  }

  private Map<ClassName, P2<Stream<DeriveMessage>, DerivedCodeSpec>> derivedInstances(AlgebraicDataType adt) {
    ClassName targetClassName = adt.deriveConfig().targetClass().className();
    return adt.deriveConfig().derivedInstances().entrySet().stream().map(deriveSelection -> {
      DerivedInstanceConfig derivedInstanceConfig = deriveSelection.getValue();
      ClassName instanceTargetClassName = getTargetClass(derivedInstanceConfig).orElse(targetClassName);
      return get(P2(deriveSelection.getKey(), getImplSelector(derivedInstanceConfig)), derivators)
          .map(derivator -> P2(instanceTargetClassName, derivator.derive(adt)))
          .orElse(P2(instanceTargetClassName, error(message(
              "Could not find instance derivator for " + deriveSelection.getKey() + " and " + derivedInstanceConfig))));
    })
        .collect(toMap(P2s::get_1,
            p2 -> P2(optionalAsStream(getError(p2._2())), getResult(p2._2()).orElse(DerivedCodeSpec.none())),
            (res1, res2) -> P2(concat(res1._1(), res2._1()), res1._2().append(res2._2()))));
  }

  private void printErrorMessage(String typeElement, Throwable error) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
        "Derive4J: unable to process " + typeElement + " due to " + error.getMessage() + "\n" + showStackTrace(error));
  }

  private static List<Extension> loadEextensions(DeriveUtils deriveUtils) {
    return StreamSupport
        .stream(ServiceLoader.load(ExtensionFactory.class, DerivingProcessor.class.getClassLoader()).spliterator(),
            false)
        .flatMap(f -> f.extensions(deriveUtils).stream())
        .collect(toList());
  }

  private static Map<P2<ClassName, Optional<String>>, Derivator> loadDerivators(DeriveUtils deriveUtils) {
    return StreamSupport
        .stream(ServiceLoader.load(DerivatorFactory.class, DerivingProcessor.class.getClassLoader()).spliterator(),
            false)
        .flatMap(f -> f.derivators(deriveUtils).stream())
        .collect(toMap(ds -> P2(DerivatorSelections.getForClass(ds), DerivatorSelections.getSelector(ds)),
            DerivatorSelections::getDerivator));
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
