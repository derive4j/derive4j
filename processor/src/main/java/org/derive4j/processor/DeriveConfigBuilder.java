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

import com.squareup.javapoet.ClassName;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import org.derive4j.ArgOption;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Flavour;
import org.derive4j.Make;
import org.derive4j.Makes;
import org.derive4j.Visibilities;
import org.derive4j.Visibility;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.model.DeriveConfig;
import org.derive4j.processor.api.model.DeriveVisibilities;
import org.derive4j.processor.api.model.DeriveVisibility;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;
import static org.derive4j.Make.constructors;
import static org.derive4j.Make.lambdaVisitor;
import static org.derive4j.processor.Utils.getPackage;
import static org.derive4j.processor.Utils.optionalAsStream;
import static org.derive4j.processor.api.model.DeriveConfigs.Config;
import static org.derive4j.processor.api.model.DeriveConfigs.modArgOptions;
import static org.derive4j.processor.api.model.DeriveConfigs.modTargetClass;
import static org.derive4j.processor.api.model.DeriveConfigs.setFlavour;
import static org.derive4j.processor.api.model.DeriveConfigs.setMakes;
import static org.derive4j.processor.api.model.DeriveTargetClasses.TargetClass;
import static org.derive4j.processor.api.model.DeriveTargetClasses.setClassName;
import static org.derive4j.processor.api.model.DeriveTargetClasses.setVisibility;

public final class DeriveConfigBuilder {

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
      return Utils.asDeclaredType.visit(t)
          .flatMap(dt -> Utils.asTypeElement.visit(dt.asElement()))
          .orElseThrow(IllegalArgumentException::new);
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
      return vals.stream().map(this::visit).collect(Collectors.toList());
    }
  };
  private final TypeElement dataAnnotation;
  private final TypeElement deriveAnnotation;
  private final ExecutableElement flavour;
  private final ExecutableElement arguments;
  private final ExecutableElement deriveValue;
  private final ExecutableElement inClass;
  private final ExecutableElement withVisibility;
  private final ExecutableElement make;

  DeriveConfigBuilder(DeriveUtils deriveUtils) {
    dataAnnotation = deriveUtils.elements().getTypeElement(Data.class.getName());
    flavour = unsafeGetExecutableElement(dataAnnotation, "flavour");
    arguments = unsafeGetExecutableElement(dataAnnotation, "arguments");
    deriveValue = unsafeGetExecutableElement(dataAnnotation, "value");

    deriveAnnotation = deriveUtils.elements().getTypeElement(Derive.class.getName());
    inClass = unsafeGetExecutableElement(deriveAnnotation, "inClass");
    withVisibility = unsafeGetExecutableElement(deriveAnnotation, "withVisibility");
    make = unsafeGetExecutableElement(deriveAnnotation, "make");
  }

  Stream<P2<TypeElement, DeriveConfig>> findDeriveConfig(TypeElement typeElement) {
    return optionalAsStream(deriveConfigs(typeElement, typeElement, new HashSet<>()).reduce(Function::andThen)
        .map(customConfig -> P2s.P2(typeElement, customConfig.apply(defaultConfig(typeElement)))));
  }

  private Stream<Function<DeriveConfig, DeriveConfig>> annotationConfig(TypeElement typeElement,
      AnnotationMirror annotationMirror) {
    Element annotationElement = annotationMirror.getAnnotationType().asElement();
    return annotationElement.equals(dataAnnotation)
        ? Stream.of(dataConfig(typeElement, annotationMirror.getElementValues()))
        : annotationElement.equals(deriveAnnotation)
            ? Stream.of(deriveConfig(typeElement, annotationMirror.getElementValues()))
            : Stream.empty();
  }

  private Stream<Function<DeriveConfig, DeriveConfig>> deriveConfigs(TypeElement typeElement, Element element,
      HashSet<AnnotationMirror> seenAnnotations) {
    return element.getAnnotationMirrors().stream().sequential().filter(a -> !seenAnnotations.contains(a)).flatMap(a -> {
      seenAnnotations.add(a);
      return Stream.concat(deriveConfigs(typeElement, a.getAnnotationType().asElement(), seenAnnotations),
          annotationConfig(typeElement, a));
    });
  }

  private Function<DeriveConfig, DeriveConfig> deriveConfig(TypeElement typeElement,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {

    Optional<Function<DeriveConfig, DeriveConfig>> setInClass = ofNullable(elementValues.get(inClass)).map(
        inClassValue -> modTargetClass(setClassName(ClassName.get(getPackage.visit(typeElement).getQualifiedName().toString(),
            deduceDerivedClassName(getValue.visit(inClassValue).toString(), typeElement)))));

    Optional<Function<DeriveConfig, DeriveConfig>> setVisibility = ofNullable(elementValues.get(withVisibility)).map(
        withVisibilityValue -> modTargetClass(setVisibility(
            deduceDeriveVisibility(typeElement, Visibility.valueOf(getValue.visit(withVisibilityValue).toString())))));

    Optional<Function<DeriveConfig, DeriveConfig>> modMake = ofNullable(elementValues.get(make)).map(
        makeValue -> (List<String>) getValue.visit(makeValue))
        .map(newMakes -> setMakes(makeWithDependencies(newMakes.stream().map(Make::valueOf))));

    return Stream.of(setInClass, setVisibility, modMake)
        .flatMap(Utils::optionalAsStream)
        .reduce(Function::andThen)
        .orElse(Function.identity());
  }

  private Function<DeriveConfig, DeriveConfig> dataConfig(TypeElement typeElement,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {

    Optional<Function<DeriveConfig, DeriveConfig>> setFlavour = ofNullable(elementValues.get(flavour)).map(
        flavourValue -> setFlavour(Flavour.valueOf(getValue.visit(flavourValue).toString())));

    Optional<Function<DeriveConfig, DeriveConfig>> modArguments = ofNullable(elementValues.get(arguments)).map(
        argumentsValue -> (List<String>) getValue.visit(argumentsValue))
        .map(newArgOptions -> modArgOptions(argOptions -> newArgOptions.size() == 0
            ? EnumSet.noneOf(ArgOption.class)
            : argOptions.size() == ArgOption.values().length
                ? EnumSet.copyOf(newArgOptions.stream().map(ArgOption::valueOf).collect(Collectors.toList()))
                : EnumSet.copyOf(
                    Stream.of(argOptions, newArgOptions.stream().map(ArgOption::valueOf).collect(Collectors.toList()))
                        .flatMap(m -> m.stream())
                        .collect(Collectors.toList()))));

    Optional<Function<DeriveConfig, DeriveConfig>> deriveConfig = ofNullable(elementValues.get(deriveValue)).map(
        value -> deriveConfig(typeElement, (Map<? extends ExecutableElement, ? extends AnnotationValue>) getValue.visit(value)));

    return Stream.of(setFlavour, modArguments, deriveConfig)
        .flatMap(Utils::optionalAsStream)
        .reduce(Function::andThen)
        .orElse(Function.identity());
  }

  private static DeriveConfig defaultConfig(TypeElement typeElement) {
    return Config(Flavour.JDK, TargetClass(ClassName.get(getPackage.visit(typeElement).getQualifiedName().toString(),
        autoGeneratedClassName(typeElement.getSimpleName().toString())), deduceDeriveVisibility(typeElement, Visibility.Same)),
        EnumSet.of(Make.constructors, Make.lazyConstructor, Make.lambdaVisitor, Make.getters, Make.modifiers, Make.catamorphism,
            Make.casesMatching, Make.caseOfMatching), EnumSet.noneOf(ArgOption.class), Collections.emptyMap());
  }

  private static DeriveVisibility deduceDeriveVisibility(TypeElement typeElement, Visibility visibility) {
    return Visibilities.cases()
        .Same(typeElement.getModifiers().contains(Modifier.PUBLIC)
            ? DeriveVisibilities.Public()
            : DeriveVisibilities.Package())
        .Package(DeriveVisibilities.Package())
        .Smart(typeElement.getModifiers().contains(Modifier.PUBLIC)
            ? DeriveVisibilities.Smart()
            : DeriveVisibilities.Package())
        .apply(visibility);
  }

  private static ExecutableElement unsafeGetExecutableElement(TypeElement typeElement, String methodName) {
    return (ExecutableElement) typeElement.getEnclosedElements()
        .stream()
        .filter(e -> e.getSimpleName().contentEquals(methodName))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException(typeElement + "#" + methodName));
  }

  private static String deduceDerivedClassName(String inClassAnnotationValue, TypeElement typeElement) {

    return ":auto".equals(inClassAnnotationValue)
        ? autoGeneratedClassName(typeElement.getSimpleName().toString())
        : inClassAnnotationValue.replace("{ClassName}", typeElement.getSimpleName());
  }

  private static String autoGeneratedClassName(String adtClassName) {
    return (adtClassName.endsWith("y") && !adtClassName.endsWith("Day"))
        ? adtClassName.substring(0, adtClassName.length() - 1) + "ies"
        : adtClassName.endsWith("s") ||
            adtClassName.endsWith("x") ||
            adtClassName.endsWith("z") ||
            adtClassName.endsWith("ch") ||
            adtClassName.endsWith("sh")
            ? adtClassName + "es"
            : adtClassName + 's';
  }

  private static Set<Make> makeWithDependencies(Stream<Make> makes) {
    EnumSet<Make> makeSet = EnumSet.noneOf(Make.class);
    makeSet.addAll(makes.flatMap(m -> concat(makeDependencies.apply(m), of(m))).collect(toList()));
    return Collections.unmodifiableSet(makeSet);
  }

  private static final Function<Make, Stream<Make>> makeDependencies = Makes.cases()
      .lambdaVisitor(Stream::<Make>of)
      .constructors(Stream::of)
      .lazyConstructor(Stream::of)
      .casesMatching(() -> of(lambdaVisitor))
      .caseOfMatching(() -> of(lambdaVisitor))
      .getters(() -> of(lambdaVisitor))
      .modifiers(() -> of(lambdaVisitor, constructors))
      .catamorphism(() -> of(lambdaVisitor))
      .hktCoerce(Stream::of);
}
