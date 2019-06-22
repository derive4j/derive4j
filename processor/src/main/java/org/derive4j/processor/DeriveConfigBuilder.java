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

import com.squareup.javapoet.ClassName;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.AbstractAnnotationValueVisitor8;
import javax.lang.model.util.Elements;
import org.derive4j.ArgOption;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Flavour;
import org.derive4j.Instances;
import org.derive4j.Make;
import org.derive4j.Makes;
import org.derive4j.Visibilities;
import org.derive4j.Visibility;
import org.derive4j.processor.api.model.DeriveConfig;
import org.derive4j.processor.api.model.DeriveConfigs;
import org.derive4j.processor.api.model.DeriveVisibilities;
import org.derive4j.processor.api.model.DeriveVisibility;
import org.derive4j.processor.api.model.DerivedInstanceConfig;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;
import static org.derive4j.Make.constructors;
import static org.derive4j.Make.lambdaVisitor;
import static org.derive4j.processor.Utils.get;
import static org.derive4j.processor.Utils.getPackage;
import static org.derive4j.processor.api.model.DeriveConfigs.Config;
import static org.derive4j.processor.api.model.DeriveConfigs.modArgOptions;
import static org.derive4j.processor.api.model.DeriveConfigs.modMakes;
import static org.derive4j.processor.api.model.DeriveConfigs.modTargetClass;
import static org.derive4j.processor.api.model.DeriveConfigs.setFlavour;
import static org.derive4j.processor.api.model.DeriveConfigs.setMakes;
import static org.derive4j.processor.api.model.DeriveTargetClasses.TargetClass;
import static org.derive4j.processor.api.model.DeriveTargetClasses.setClassName;
import static org.derive4j.processor.api.model.DeriveTargetClasses.setExtend;
import static org.derive4j.processor.api.model.DeriveTargetClasses.setVisibility;
import static org.derive4j.processor.api.model.DerivedInstanceConfigs.InstanceConfig;

final class DeriveConfigBuilder {

  private static final AnnotationValueVisitor<Object, Void> getValue = new AbstractAnnotationValueVisitor8<Object, Void>() {
    @Override
    public Object visitBoolean(boolean b, Void aVoid) {
      return b;
    }

    @Override
    public Object visitByte(byte b, Void aVoid) {
      return b;
    }

    @Override
    public Object visitChar(char c, Void aVoid) {
      return c;
    }

    @Override
    public Object visitDouble(double d, Void aVoid) {
      return d;
    }

    @Override
    public Object visitFloat(float f, Void aVoid) {
      return f;
    }

    @Override
    public Object visitInt(int i, Void aVoid) {
      return i;
    }

    @Override
    public Object visitLong(long i, Void aVoid) {
      return i;
    }

    @Override
    public Object visitShort(short s, Void aVoid) {
      return s;
    }

    @Override
    public Object visitString(String s, Void aVoid) {
      return s;
    }

    @Override
    public Object visitType(TypeMirror t, Void aVoid) {
      return Utils.asDeclaredType.visit(t).flatMap(dt -> Utils.asTypeElement.visit(dt.asElement())).orElseThrow(
          IllegalArgumentException::new);
    }

    @Override
    public Object visitEnumConstant(VariableElement c, Void aVoid) {
      return c.getSimpleName().toString();
    }

    @Override
    public Object visitAnnotation(AnnotationMirror a, Void aVoid) {
      return a.getElementValues();
    }

    @Override
    public Object visitArray(List<? extends AnnotationValue> vals, Void aVoid) {
      return vals.stream().map(this::visit).collect(toList());
    }
  };

  private static final Function<Make, Stream<Make>> makeDependencies = Makes.cases()
      .lambdaVisitor(Stream::<Make>of)
      .constructors(Stream::of)
      .lazyConstructor(Stream::of)
      .casesMatching(() -> of(lambdaVisitor))
      .caseOfMatching(() -> of(lambdaVisitor))
      .getters(() -> of(lambdaVisitor))
      .modifiers(() -> of(lambdaVisitor, constructors))
      .catamorphism(() -> of(lambdaVisitor))
      .factory(() -> of(lambdaVisitor));

  private final TypeElement       dataAnnotation;
  private final TypeElement       deriveAnnotation;
  private final ExecutableElement flavour;
  private final ExecutableElement arguments;
  private final ExecutableElement deriveValue;
  private final ExecutableElement inClass;
  private final ExecutableElement withVisibility;
  private final ExecutableElement extend;
  private final ExecutableElement make;
  private final ExecutableElement instances;
  private final ExecutableElement instancesClasses;
  private final ExecutableElement instancesInClass;
  private final ExecutableElement instancesSelector;

  DeriveConfigBuilder(Elements elements) {
    dataAnnotation = elements.getTypeElement(Data.class.getName());
    flavour = unsafeGetExecutableElement(dataAnnotation, "flavour");
    arguments = unsafeGetExecutableElement(dataAnnotation, "arguments");
    deriveValue = unsafeGetExecutableElement(dataAnnotation, "value");

    deriveAnnotation = elements.getTypeElement(Derive.class.getName());
    inClass = unsafeGetExecutableElement(deriveAnnotation, "inClass");
    withVisibility = unsafeGetExecutableElement(deriveAnnotation, "withVisibility");
    extend = unsafeGetExecutableElement(deriveAnnotation, "extend");
    make = unsafeGetExecutableElement(deriveAnnotation, "make");
    instances = unsafeGetExecutableElement(deriveAnnotation, "value");
    TypeElement instanceAnnotation = elements.getTypeElement(Instances.class.getName());
    instancesClasses = unsafeGetExecutableElement(instanceAnnotation, "value");
    instancesInClass = unsafeGetExecutableElement(instanceAnnotation, "inClass");
    instancesSelector = unsafeGetExecutableElement(instanceAnnotation, "selector");
  }

  Optional<P2<TypeElement, DeriveConfig>> findDeriveConfig(TypeElement typeElement) {
    return deriveConfigs(typeElement, typeElement, new HashSet<>()).reduce(Function::andThen)
        .map(customConfig -> P2s.P2(typeElement, customConfig.apply(defaultConfig(typeElement))));
  }

  ClassName deduceDerivedClassName(String inClassAnnotationValue, TypeElement typeElement) {

    String packageName = getPackage.visit(typeElement).getQualifiedName().toString();

    String simpleClassName = ":auto".equals(inClassAnnotationValue)
        ? autoGeneratedClassName(typeElement.getSimpleName().toString())
        : inClassAnnotationValue.replace("{ClassName}", typeElement.getSimpleName());

    return ClassName.get(packageName, simpleClassName);
  }

  private Stream<Function<DeriveConfig, DeriveConfig>> annotationConfig(TypeElement typeElement,
      AnnotationMirror annotationMirror) {
    Element annotationElement = annotationMirror.getAnnotationType().asElement();
    return annotationElement.equals(dataAnnotation)
        ? of(dataConfig(typeElement, annotationMirror.getElementValues()))
        : (annotationElement.equals(deriveAnnotation)
            ? of(addToDeriveConfig(typeElement, annotationMirror.getElementValues()))
            : Stream.empty());
  }

  private Stream<Function<DeriveConfig, DeriveConfig>> deriveConfigs(TypeElement typeElement, Element element,
      HashSet<AnnotationMirror> seenAnnotations) {
    return element.getAnnotationMirrors().stream().sequential().filter(a -> !seenAnnotations.contains(a)).flatMap(a -> {
      seenAnnotations.add(a);
      return concat(deriveConfigs(typeElement, a.getAnnotationType().asElement(), seenAnnotations),
          annotationConfig(typeElement, a));
    });
  }

  private Function<DeriveConfig, DeriveConfig> deriveConfig(TypeElement typeElement,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {

    Optional<Function<DeriveConfig, DeriveConfig>> setInClass = inClass(typeElement, elementValues);

    Optional<Function<DeriveConfig, DeriveConfig>> setVisibility = visibility(typeElement, elementValues);

    Optional<Function<DeriveConfig, DeriveConfig>> setExtend = extend(typeElement, elementValues);

    @SuppressWarnings("unchecked")
    Optional<Function<DeriveConfig, DeriveConfig>> setMake = get(make, elementValues)
        .map(makeValue -> (List<String>) getValue.visit(makeValue))
        .map(newMakes -> setMakes(makeWithDependencies(newMakes.stream().map(Make::valueOf))));

    Optional<Function<DeriveConfig, DeriveConfig>> modInstances = instances(typeElement, elementValues)
        .map(DeriveConfigs::modDerivedInstances);

    return of(setInClass, setVisibility, setExtend, setMake, modInstances).flatMap(Utils::optionalAsStream)
        .reduce(Function::andThen)
        .orElse(identity());
  }

  private Function<DeriveConfig, DeriveConfig> addToDeriveConfig(TypeElement typeElement,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {

    Optional<Function<DeriveConfig, DeriveConfig>> setInClass = inClass(typeElement, elementValues);

    Optional<Function<DeriveConfig, DeriveConfig>> setVisibility = visibility(typeElement, elementValues);

    Optional<Function<DeriveConfig, DeriveConfig>> setExtend = extend(typeElement, elementValues);

    Optional<Function<DeriveConfig, DeriveConfig>> modInstances = instances(typeElement, elementValues)
        .map(DeriveConfigs::modDerivedInstances);

    @SuppressWarnings("unchecked")
    Optional<Function<DeriveConfig, DeriveConfig>> modMake = get(make, elementValues)
        .map(makeValue -> (List<String>) getValue.visit(makeValue))
        .map(newMakes -> modMakes(
            makes -> makeWithDependencies(concat(makes.stream(), newMakes.stream().map(Make::valueOf)))));

    return of(setInClass, setVisibility, setExtend, modMake, modInstances).flatMap(Utils::optionalAsStream)
        .reduce(Function::andThen)
        .orElse(identity());
  }

  private Optional<Function<DeriveConfig, DeriveConfig>> visibility(TypeElement typeElement,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
    return get(withVisibility, elementValues).map(withVisibilityValue -> modTargetClass(setVisibility(
        deduceDeriveVisibility(typeElement, Visibility.valueOf(getValue.visit(withVisibilityValue).toString())))));
  }

  private Optional<Function<DeriveConfig, DeriveConfig>> inClass(TypeElement typeElement,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
    return get(inClass, elementValues).map(inClassValue -> modTargetClass(
        setClassName(deduceDerivedClassName(getValue.visit(inClassValue).toString(), typeElement))));
  }

  private Optional<Function<DeriveConfig, DeriveConfig>> extend(TypeElement typeElement,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
    return get(extend, elementValues).map(extend -> modTargetClass(setExtend(
        Optional.of(ClassName.get((TypeElement) getValue.visit(extend))))));
  }

  private Optional<Function<Map<ClassName, DerivedInstanceConfig>, Map<ClassName, DerivedInstanceConfig>>> instances(
      TypeElement typeElement, Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
    return get(instances, elementValues).map(instances -> {
      @SuppressWarnings("unchecked")
      List<Map<? extends ExecutableElement, ? extends AnnotationValue>> instanceAnnotations = (List<Map<? extends ExecutableElement, ? extends AnnotationValue>>) getValue
          .visit(instances);

      return instanceAnnotations.isEmpty() ? __ -> Collections.emptyMap() : currentConfig -> {
        Map<ClassName, DerivedInstanceConfig> newConfig = new HashMap<>(currentConfig);
        instanceAnnotations
            .forEach(instanceAnnotation -> newConfig.putAll(parseInstanceConfig(typeElement, instanceAnnotation)));
        return Collections.unmodifiableMap(newConfig);
      };

    });
  }

  @SuppressWarnings("unchecked")
  private Map<ClassName, DerivedInstanceConfig> parseInstanceConfig(TypeElement typeElement,
      Map<? extends ExecutableElement, ? extends AnnotationValue> instanceAnnotation) {

    return get(instancesClasses, instanceAnnotation)
        .map(instancesClassesAnnotationValue -> (List<TypeElement>) getValue.visit(instancesClassesAnnotationValue))
        .filter(l -> !l.isEmpty())
        .map(instancesClasses -> {

          Optional<ClassName> targetClass = get(instancesInClass, instanceAnnotation)
              .map(a -> (String) getValue.visit(a))
              .map(inClass -> deduceDerivedClassName(inClass, typeElement));

          Optional<String> selector = get(instancesSelector, instanceAnnotation).map(a -> (String) getValue.visit(a));

          DerivedInstanceConfig instanceConfig = InstanceConfig(selector, targetClass);

          return instancesClasses.stream().collect(toMap(ClassName::get, __ -> instanceConfig));

        })
        .orElse(Collections.emptyMap());
  }

  private Function<DeriveConfig, DeriveConfig> dataConfig(TypeElement typeElement,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {

    Optional<Function<DeriveConfig, DeriveConfig>> setFlavour = get(flavour, elementValues)
        .map(flavourValue -> setFlavour(Flavour.valueOf(getValue.visit(flavourValue).toString())));

    @SuppressWarnings("unchecked")
    Optional<Function<DeriveConfig, DeriveConfig>> modArguments = get(arguments, elementValues)
        .map(argumentsValue -> (List<String>) getValue.visit(argumentsValue))
        .map(newArgOptions -> modArgOptions(argOptions -> newArgOptions.isEmpty()
            ? EnumSet.noneOf(ArgOption.class)
            : EnumSet.copyOf(newArgOptions.stream().map(ArgOption::valueOf).collect(toList()))));

    @SuppressWarnings("unchecked")
    Optional<Function<DeriveConfig, DeriveConfig>> deriveConfig = get(deriveValue, elementValues)
        .map(value -> deriveConfig(typeElement,
            (Map<? extends ExecutableElement, ? extends AnnotationValue>) getValue.visit(value)));

    return of(setFlavour, modArguments, deriveConfig).flatMap(Utils::optionalAsStream).reduce(Function::andThen).orElse(
        identity());
  }

  private static DeriveConfig defaultConfig(TypeElement typeElement) {
    return Config(Flavour.JDK,
        TargetClass(
            ClassName.get(getPackage.visit(typeElement).getQualifiedName().toString(),
                autoGeneratedClassName(typeElement.getSimpleName().toString())),
            deduceDeriveVisibility(typeElement, Visibility.Same),
            Optional.empty()),
        EnumSet.of(constructors, Make.lazyConstructor, lambdaVisitor, Make.getters, Make.modifiers, Make.catamorphism,
            Make.factory, Make.casesMatching, Make.caseOfMatching),
        EnumSet.noneOf(ArgOption.class), Collections.emptyMap());
  }

  private static DeriveVisibility deduceDeriveVisibility(TypeElement typeElement, Visibility visibility) {
    return Visibilities.caseOf(visibility)
        .Same(() -> typeElement.getModifiers().contains(Modifier.PUBLIC)
            ? DeriveVisibilities.Public()
            : DeriveVisibilities.Package())
        .Package_(DeriveVisibilities.Package())
        .Smart(() -> typeElement.getModifiers().contains(Modifier.PUBLIC)
            ? DeriveVisibilities.Smart()
            : DeriveVisibilities.Package());
  }

  private static ExecutableElement unsafeGetExecutableElement(TypeElement typeElement, String methodName) {
    return (ExecutableElement) typeElement.getEnclosedElements()
        .stream()
        .filter(e -> e.getSimpleName().contentEquals(methodName))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException(typeElement + "#" + methodName));
  }

  private static String autoGeneratedClassName(String adtClassName) {
    return (adtClassName.endsWith("y")
        && !(adtClassName.endsWith("ay") || adtClassName.endsWith("ey") || adtClassName.endsWith("oy")))
            ? (adtClassName.substring(0, adtClassName.length() - 1) + "ies")
            : ((adtClassName.endsWith("s") || adtClassName.endsWith("x") || adtClassName.endsWith("z")
                || adtClassName.endsWith("ch") || adtClassName.endsWith("sh"))
                    ? (adtClassName + "es")
                    : (adtClassName + 's'));
  }

  private static Set<Make> makeWithDependencies(Stream<Make> makes) {
    EnumSet<Make> makeSet = EnumSet.noneOf(Make.class);
    makeSet.addAll(makes.flatMap(m -> concat(makeDependencies.apply(m), of(m))).collect(toList()));
    return Collections.unmodifiableSet(makeSet);
  }
}
