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
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.TypeRestriction;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.derive4j.processor.P2.p2;
import static org.derive4j.processor.Utils.asDeclaredType;
import static org.derive4j.processor.Utils.asExecutableElement;
import static org.derive4j.processor.Utils.asTypeElement;
import static org.derive4j.processor.Utils.asTypeVariable;
import static org.derive4j.processor.Utils.getMethods;
import static org.derive4j.processor.Utils.p;

final class DeriveUtilsImpl implements DeriveUtils {

  private final Elements Elements;
  private final Types Types;
  private final ExecutableElement objectEquals;
  private final ExecutableElement objectHashCode;
  private final ExecutableElement objectToString;
  private final AdtParser adtParser;
  private final TypeElement object;

  DeriveUtilsImpl(Elements Elements, Types Types) {

    this.Elements = Elements;
    this.Types = Types;

    object = Elements.getTypeElement(Object.class.getName());
    objectEquals = getMethods(object.getEnclosedElements()).filter(e -> "equals".equals(e.getSimpleName().toString())).findFirst().get();
    objectHashCode = getMethods(object.getEnclosedElements()).filter(e -> "hashCode".equals(e.getSimpleName().toString())).findFirst().get();
    objectToString = getMethods(object.getEnclosedElements()).filter(e -> "toString".equals(e.getSimpleName().toString())).findFirst().get();

    adtParser = new AdtParser(this);
  }

  @Override public Types types() {

    return Types;
  }

  @Override public Elements elements() {

    return Elements;
  }

  @Override public DeriveResult<AlgebraicDataType> parseAlgebraicDataType(TypeElement typeElement) {

    return adtParser.parseAlgebraicDataType(typeElement);
  }

  @Override public TypeName resolveToTypeName(TypeMirror typeMirror, Function<TypeVariable, Optional<TypeName>> typeArgs) {

    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().isEmpty()
                   ? TypeName.get(dt)
                   : ParameterizedTypeName.get(ClassName.get(asTypeElement.visit(dt.asElement()).get()),
                       dt.getTypeArguments().stream().map(ta -> resolveToTypeName(ta, typeArgs)).toArray(TypeName[]::new)))
        .orElse(asTypeVariable.visit(typeMirror).flatMap(typeArgs).orElse(TypeName.get(typeMirror)));
  }

  @Override public Function<TypeVariable, Optional<TypeMirror>> typeArgs(DeclaredType dt) {

    return tv -> IntStream.range(0, dt.getTypeArguments().size())
        .filter(i -> Types.isSameType(tv, asTypeElement.visit(dt.asElement()).get().getTypeParameters().get(i).asType()))
        .mapToObj(i -> dt.getTypeArguments().get(i))
        .findFirst()
        .map(tm -> tm);
  }

  @Override public Function<TypeVariable, Optional<TypeMirror>> typeRestrictions(List<TypeRestriction> typeRestrictions) {

    return tv -> typeRestrictions.stream()
        .filter(tr -> Types.isSameType(tr.restrictedTypeVariable(), tv))
        .findFirst()
        .map(TypeRestriction::refinementType);
  }

  @Override public TypeMirror resolve(TypeMirror typeMirror, Function<TypeVariable, Optional<TypeMirror>> typeArgs) {

    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().isEmpty()
                   ? dt
                   : Types.getDeclaredType(asTypeElement.visit(dt.asElement()).get(),
                       dt.getTypeArguments().stream().map(ta -> resolve(ta, typeArgs)).toArray(TypeMirror[]::new))).<TypeMirror>map(dt -> dt).orElse(
            asTypeVariable.visit(typeMirror).flatMap(typeArgs).orElse(typeMirror));
  }

  @Override public DeclaredType resolve(DeclaredType declaredType, Function<TypeVariable, Optional<TypeMirror>> typeArgs) {

    return declaredType.getTypeArguments().isEmpty()
           ? declaredType
           : Types.getDeclaredType(asTypeElement.visit(declaredType.asElement()).get(),
               declaredType.getTypeArguments().stream().map(ta -> resolve(ta, typeArgs)).toArray(TypeMirror[]::new));
  }

  @Override public MethodSpec.Builder overrideMethodBuilder(ExecutableElement abstractMethod, DeclaredType declaredType) {

    return MethodSpec.overriding(abstractMethod, declaredType, Types);
  }

  @Override public List<TypeVariable> typeVariablesIn(TypeMirror typeMirror) {
    List<TypeVariable> typeVariables = new ArrayList<>();

    typeVariablesIn0(typeMirror).forEach(tv -> {
      if (typeVariables.stream().noneMatch(predTv -> Types.isSameType(predTv, tv))) {
        typeVariables.add(tv);
      }
    });
    return  typeVariables;
  }

  private Stream<TypeVariable> typeVariablesIn0(TypeMirror typeMirror) {

    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().stream().flatMap(this::typeVariablesIn0))
        .orElseGet(() -> asTypeVariable.visit(typeMirror).map(Stream::of).orElse(Stream.empty()));
  }

  @Override public List<ExecutableElement> allAbstractMethods(DeclaredType declaredType) {

    return asTypeElement.visit(declaredType.asElement()).map(typeElement -> {

      List<P2<ExecutableElement, ExecutableType>> unorderedAbstractMethods = getMethods(Elements.getAllMembers(typeElement))
          .filter(this::abstractMehod)
          .map(e -> p2(e, (ExecutableType) Types.asMemberOf(declaredType, e)))
          .collect(toList());

      Set<ExecutableElement> deduplicatedUnorderedAbstractMethods = IntStream.range(0, unorderedAbstractMethods.size())
          .filter(i -> unorderedAbstractMethods.subList(0, i)
              .stream()
              .noneMatch(m -> m.match((predExecutableElement, predExecutableType) -> unorderedAbstractMethods.get(i)
                  .match((executableElement, executableType) -> predExecutableElement.getSimpleName().equals(executableElement.getSimpleName()) &&
                      Types.isSubsignature(predExecutableType, executableType)))))
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

  private boolean abstractMehod(ExecutableElement e) {
    return e.getModifiers().contains(Modifier.ABSTRACT) &&
        !((e.getEnclosingElement().getKind() == ElementKind.INTERFACE) &&
              (Elements.overrides(e, objectEquals, object) ||
                   Elements.overrides(e, objectHashCode, object) ||
                   Elements.overrides(e, objectToString, object)));
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

  @Override public List<ExecutableElement> allAbstractMethods(TypeElement typeElement) {

    return allAbstractMethods((DeclaredType) typeElement.asType());
  }

  @Override public TypeElement object() {

    return object;
  }

  @Override public ExecutableElement objectEquals() {

    return objectEquals;
  }

  @Override public ExecutableElement objectHashCode() {

    return objectHashCode;
  }

  @Override public ExecutableElement objectToString() {

    return objectToString;
  }

}
