/*
 * Copyright (c) 2017, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.derive4j.Flavour;
import org.derive4j.Flavours;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.EitherModel;
import org.derive4j.processor.api.EitherModels;
import org.derive4j.processor.api.ObjectModel;
import org.derive4j.processor.api.OptionModel;
import org.derive4j.processor.api.OptionModels;
import org.derive4j.processor.api.SamInterface;
import org.derive4j.processor.api.SamInterfaces;
import org.derive4j.processor.api.model.TypeRestriction;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.derive4j.processor.P2.p2;
import static org.derive4j.processor.Utils.asDeclaredType;
import static org.derive4j.processor.Utils.asExecutableElement;
import static org.derive4j.processor.Utils.asTypeElement;
import static org.derive4j.processor.Utils.asTypeVariable;
import static org.derive4j.processor.Utils.findOnlyOne;
import static org.derive4j.processor.Utils.getMethods;
import static org.derive4j.processor.Utils.optionalAsStream;
import static org.derive4j.processor.api.EitherModels.EitherModel;
import static org.derive4j.processor.api.ObjectModels.ObjectModel;
import static org.derive4j.processor.api.SamInterfaces.SamInterface;

final class DeriveUtilsImpl implements DeriveUtils {

  private final Elements Elements;
  private final Types Types;
  private final ObjectModel objectModel;

  private final Function<Flavour, SamInterface> function0Model;
  private final Function<Flavour, SamInterface> function1Model;
  private final Function<Flavour, OptionModel> optionModel;
  private final Function<Flavour, Optional<EitherModel>> eitherModel;

  DeriveUtilsImpl(Elements Elements, Types Types) {

    this.Elements = Elements;
    this.Types = Types;

    TypeElement object = Elements.getTypeElement(Object.class.getName());
    List<ExecutableElement> objectMethods = ElementFilter.methodsIn(object.getEnclosedElements());

    objectModel = ObjectModel(object,
        objectMethods.stream().filter(e -> e.getSimpleName().contentEquals("equals")).findAny().get(),
        objectMethods.stream().filter(e -> e.getSimpleName().contentEquals("hashCode")).findAny().get(),
        objectMethods.stream().filter(e -> e.getSimpleName().contentEquals("toString")).findAny().get());

    SamInterface jdkSupplier = samInterface(Supplier.class.getName()).get();
    SamInterface guavaSupplier = lazySamInterface("com.google.common.base.Supplier");

    function0Model = Flavours.cases()
        .Jdk_(jdkSupplier)
        .Fj_(lazySamInterface("fj.F0"))
        .Fugue_(jdkSupplier)
        .Fugue2_(guavaSupplier)
        .Javaslang_(jdkSupplier)
        .HighJ_(jdkSupplier)
        .Guava_(guavaSupplier);

    SamInterface jdkFunction = samInterface(Function.class.getName()).get();
    SamInterface guavaFunction = lazySamInterface("com.google.common.base.Function");

    function1Model = Flavours.cases()
        .Jdk_(jdkFunction)
        .Fj_(lazySamInterface("fj.F"))
        .Fugue_(jdkFunction)
        .Fugue2_(guavaFunction)
        .Javaslang_(lazySamInterface("javaslang.Function1"))
        .HighJ_(lazySamInterface("org.highj.function.F1"))
        .Guava_(guavaFunction);

    optionModel = Flavours.cases()
        .Jdk_(lazyOptionModel(Optional.class.getName(), "empty", "of"))
        .Fj_(lazyOptionModel("fj.data.Option", "none", "some"))
        .Fugue_(lazyOptionModel("io.atlassian.fugue.Option", "none", "some"))
        .Fugue2_(lazyOptionModel("com.atlassian.fugue.Option", "none", "some"))
        .Javaslang_(lazyOptionModel("javaslang.control.Option", "none", "some"))
        .HighJ_(lazyOptionModel("org.highj.data.Maybe", "Nothing", "Just"))
        .Guava_(lazyOptionModel("com.google.common.base.Optional", "absent", "of"));

    eitherModel = Flavours.cases()
        .Jdk_(Optional.<EitherModel>empty())
        .Fj_(eitherModel("fj.data.Either", "left", "right"))
        .Fugue_(eitherModel("io.atlassian.fugue.Either", "left", "right"))
        .Fugue2_(eitherModel("com.atlassian.fugue.Either", "left", "right"))
        .Javaslang_(eitherModel("javaslang.control.Either", "left", "right"))
        .HighJ_(eitherModel("org.highj.data.Either", "Left", "Right"))
        .Guava_(Optional.empty());
  }

  @Override
  public Types types() {

    return Types;
  }

  @Override
  public Elements elements() {

    return Elements;
  }

  @Override
  public TypeName resolveToTypeName(TypeMirror typeMirror, Function<TypeVariable, Optional<TypeName>> typeArgs) {

    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().isEmpty()
            ? TypeName.get(dt)
            : ParameterizedTypeName.get(ClassName.get(asTypeElement.visit(dt.asElement()).get()),
                dt.getTypeArguments().stream().map(ta -> resolveToTypeName(ta, typeArgs)).toArray(TypeName[]::new)))
        .orElse(asTypeVariable.visit(typeMirror).flatMap(typeArgs).orElse(TypeName.get(typeMirror)));
  }

  @Override
  public Function<TypeVariable, Optional<TypeMirror>> typeRestrictions(List<TypeRestriction> typeRestrictions) {

    return tv -> typeRestrictions.stream()
        .filter(tr -> Types.isSameType(tr.restrictedTypeVariable(), tv))
        .findFirst()
        .map(TypeRestriction::refinementType);
  }

  @Override
  public TypeMirror resolve(TypeMirror typeMirror, Function<TypeVariable, Optional<TypeMirror>> typeArgs) {

    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().isEmpty()
            ? dt
            : Types.getDeclaredType(asTypeElement.visit(dt.asElement()).get(),
                dt.getTypeArguments().stream().map(ta -> resolve(ta, typeArgs)).toArray(TypeMirror[]::new))).<TypeMirror>map(
            dt -> dt).orElse(asTypeVariable.visit(typeMirror).flatMap(typeArgs).orElse(typeMirror));
  }

  @Override
  public DeclaredType resolve(DeclaredType declaredType, Function<TypeVariable, Optional<TypeMirror>> typeArgs) {

    return declaredType.getTypeArguments().isEmpty()
        ? declaredType
        : Types.getDeclaredType(asTypeElement.visit(declaredType.asElement()).get(),
            declaredType.getTypeArguments().stream().map(ta -> resolve(ta, typeArgs)).toArray(TypeMirror[]::new));
  }

  @Override
  public MethodSpec.Builder overrideMethodBuilder(ExecutableElement abstractMethod, DeclaredType declaredType) {

    return MethodSpec.overriding(abstractMethod, declaredType, Types);
  }

  @Override
  public List<TypeVariable> typeVariablesIn(TypeMirror typeMirror) {
    List<TypeVariable> typeVariables = new ArrayList<>();

    typeVariablesIn0(typeMirror).forEach(tv -> {
      if (typeVariables.stream().noneMatch(predTv -> Types.isSameType(predTv, tv))) {
        typeVariables.add(tv);
      }
    });
    return typeVariables;
  }

  @Override
  public List<ExecutableElement> allAbstractMethods(DeclaredType declaredType) {

    return asTypeElement.visit(declaredType.asElement()).map(typeElement -> {

      List<P2<ExecutableElement, ExecutableType>> unorderedAbstractMethods = getMethods(
          Elements.getAllMembers(typeElement)).filter(this::abstractMehod)
          .map(e -> p2(e, (ExecutableType) Types.asMemberOf(declaredType, e)))
          .collect(toList());

      Set<ExecutableElement> deduplicatedUnorderedAbstractMethods = IntStream.range(0, unorderedAbstractMethods.size())
          .filter(i -> unorderedAbstractMethods.subList(0, i)
              .stream()
              .noneMatch(m -> m.match((predExecutableElement, predExecutableType) -> unorderedAbstractMethods.get(i)
                  .match((executableElement, executableType) -> predExecutableElement.getSimpleName()
                      .equals(executableElement.getSimpleName()) && Types.isSubsignature(predExecutableType, executableType)))))
          .mapToObj(i -> unorderedAbstractMethods.get(i).match((executableElement, __) -> executableElement))
          .collect(toSet());

      return Stream.concat(getSuperTypeElements(typeElement), Stream.of(typeElement))
          .flatMap(te -> te.getEnclosedElements().stream())
          .map(asExecutableElement::visit)
          .flatMap(Utils::optionalAsStream)
          .filter(deduplicatedUnorderedAbstractMethods::contains)
          .collect(toList());

    }).orElse(Collections.emptyList());
  }

  @Override
  public List<ExecutableElement> allAbstractMethods(TypeElement typeElement) {

    return allAbstractMethods((DeclaredType) typeElement.asType());
  }

  @Override
  public ObjectModel object() {
    return objectModel;
  }

  @Override
  public Optional<SamInterface> samInterface(String qualifiedClassName) {
    return Optional.ofNullable(Elements.getTypeElement(qualifiedClassName))
        .flatMap(
            typeElement -> findOnlyOne(allAbstractMethods(typeElement)).map(samMethod -> SamInterface(typeElement, samMethod)));
  }

  @Override
  public SamInterface function0Model(Flavour flavour) {
    return function0Model.apply(flavour);
  }

  @Override
  public SamInterface function1Model(Flavour flavour) {
    return function1Model.apply(flavour);
  }

  @Override
  public OptionModel optionModel(Flavour flavour) {
    return optionModel.apply(flavour);
  }

  @Override
  public Optional<EitherModel> eitherModel(Flavour flavour) {
    return eitherModel.apply(flavour);
  }

  private OptionModel lazyOptionModel(String optionClassQualifiedName, String noneConstructor, String someConstructor) {
    return OptionModels.lazy(() -> Optional.ofNullable(Elements.getTypeElement(optionClassQualifiedName))
        .map(typeElement -> OptionModels.optionModel(typeElement, typeElement.getEnclosedElements()
                .stream()
                .flatMap(e -> optionalAsStream(asExecutableElement.visit(e)))
                .filter(e -> e.getParameters().isEmpty() &&
                    (e.getTypeParameters().size() == 1) &&
                    e.getModifiers().contains(Modifier.STATIC) &&
                    e.getModifiers().contains(Modifier.PUBLIC) &&
                    e.getSimpleName().contentEquals(noneConstructor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Constructor not found at " + optionClassQualifiedName + '#' + noneConstructor)),

            typeElement.getEnclosedElements()
                .stream()
                .flatMap(e -> optionalAsStream(asExecutableElement.visit(e)))
                .filter(e -> (e.getParameters().size() == 1) &&
                    (e.getTypeParameters().size() == 1) &&
                    e.getModifiers().contains(Modifier.STATIC) &&
                    e.getModifiers().contains(Modifier.PUBLIC) &&
                    e.getSimpleName().contentEquals(someConstructor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Constructor not found at " + optionClassQualifiedName + '#' + someConstructor))))

        .orElseThrow(() -> new IllegalArgumentException(optionClassQualifiedName + " not found in classpath")));
  }

  private Optional<EitherModel> eitherModel(String eitherClassQualifiedName, String leftConstructor, String rightConstructor) {
    return Optional.ofNullable(Elements.getTypeElement(eitherClassQualifiedName))
        .map(typeElement -> EitherModels.lazy(() -> EitherModel(typeElement, typeElement.getEnclosedElements()
                .stream()
                .flatMap(e -> optionalAsStream(asExecutableElement.visit(e)))
                .filter(e -> (e.getParameters().size() == 1) &&
                    (e.getTypeParameters().size() == 2) &&
                    e.getModifiers().contains(Modifier.STATIC) &&
                    e.getModifiers().contains(Modifier.PUBLIC) &&
                    e.getSimpleName().contentEquals(leftConstructor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Constructor not found at " + eitherClassQualifiedName + '#' + leftConstructor)),

            typeElement.getEnclosedElements()
                .stream()
                .flatMap(e -> optionalAsStream(asExecutableElement.visit(e)))
                .filter(e -> (e.getParameters().size() == 1) &&
                    (e.getTypeParameters().size() == 2) &&
                    e.getModifiers().contains(Modifier.STATIC) &&
                    e.getModifiers().contains(Modifier.PUBLIC) &&
                    e.getSimpleName().contentEquals(rightConstructor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Constructor not found at " + eitherClassQualifiedName + '#' + rightConstructor)))));
  }

  private SamInterface lazySamInterface(String samInterfaceQualifiedName) {
    return SamInterfaces.lazy(() -> samInterface(samInterfaceQualifiedName).orElseThrow(
        () -> new IllegalArgumentException(samInterfaceQualifiedName + " not found in classpath")));
  }

  private Stream<TypeVariable> typeVariablesIn0(TypeMirror typeMirror) {

    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().stream().flatMap(this::typeVariablesIn0))
        .orElseGet(() -> asTypeVariable.visit(typeMirror).map(Stream::of).orElse(Stream.empty()));
  }

  private boolean abstractMehod(ExecutableElement e) {
    return e.getModifiers().contains(Modifier.ABSTRACT) &&
        !((e.getEnclosingElement().getKind() == ElementKind.INTERFACE) &&
              (Elements.overrides(e, objectModel.equalsMethod(), objectModel.classModel()) ||
                   Elements.overrides(e, objectModel.hashCodeMethod(), objectModel.classModel()) ||
                   Elements.overrides(e, objectModel.toStringMethod(), objectModel.classModel())));
  }

  private static Stream<TypeElement> getSuperTypeElements(TypeElement e) {

    return Stream.concat(Stream.of(e.getSuperclass()), e.getInterfaces().stream())
        .map(asDeclaredType::visit)
        .flatMap(Utils::optionalAsStream)
        .map(DeclaredType::asElement)
        .map(asTypeElement::visit)
        .flatMap(Utils::optionalAsStream)
        .flatMap(te -> Stream.concat(getSuperTypeElements(te), Stream.of(te)));
  }

}

