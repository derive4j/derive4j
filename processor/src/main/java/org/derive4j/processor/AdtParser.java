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

import com.squareup.javapoet.*;
import org.derive4j.FieldNames;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.model.*;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static org.derive4j.processor.api.DeriveMessage.message;
import static org.derive4j.processor.api.DeriveResult.error;
import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.api.MessageLocalization.onAnnotation;
import static org.derive4j.processor.api.MessageLocalization.onElement;
import static org.derive4j.processor.api.model.AlgebraicDataType.adt;
import static org.derive4j.processor.api.model.DataArgument.argument;
import static org.derive4j.processor.api.model.DataConstruction.*;
import static org.derive4j.processor.api.model.DataConstructor.constructor;
import static org.derive4j.processor.api.model.DataConstructors.visitorDispatch;
import static org.derive4j.processor.api.model.DataDeconstructor.deconstructor;
import static org.derive4j.processor.api.model.MatchMethod.matchMethod;
import static org.derive4j.processor.api.model.TypeConstructor.typeConstructor;
import static org.derive4j.processor.api.model.TypeRestriction.typeRestriction;
import static org.derive4j.processor.P2.p2;
import static org.derive4j.processor.Utils.*;

public final class AdtParser implements DeriveUtils {

  private final Types types;
  private final Elements elements;

  public AdtParser(final Types types, Elements elements) {
    this.types = types;
    this.elements = elements;
  }

  private static Predicate<ExecutableElement> isEqualHashcodeToString(Elements elements) {

    TypeElement object = elements.getTypeElement(Object.class.getName());
    List<ExecutableElement> objectMethods = Utils.getMethods(object.getEnclosedElements()).collect(Collectors.toList());

    ExecutableElement equals = objectMethods.stream().filter(e -> e.getSimpleName().toString().equals("equals")).findFirst().get();
    ExecutableElement hashCode = objectMethods.stream().filter(e -> e.getSimpleName().toString().equals("hashCode")).findFirst().get();
    ExecutableElement toString = objectMethods.stream().filter(e -> e.getSimpleName().toString().equals("toString")).findFirst().get();
    return e -> elements.overrides(e, equals, object) || elements.overrides(e, hashCode, object) || elements.overrides(e, toString, object);
  }

  @Override
  public Types types() {
    return types;
  }

  @Override
  public Elements elements() {
    return elements;
  }

  public DeriveResult<AlgebraicDataType> parseAlgebraicDataType(final TypeElement adtTypeElement) {
    return fold(adtTypeElement.asType().accept(Utils.asDeclaredType, Unit.unit),
        error(message("Invalid annotated type", onElement(adtTypeElement))),

        declaredType ->
            fold(traverseOptional(declaredType.getTypeArguments(), ta -> asTypeVariable.visit(ta)
                    .filter(tv -> types.isSameType(elements.getTypeElement("java.lang.Object").asType(), tv.getUpperBound()))),
                error(message("Please use only type variable without bounds as type parameter", onElement(adtTypeElement))),

                adtTypeVariables ->
                    fold(findOnlyOne(getAbstractMethods(adtTypeElement.getEnclosedElements()).stream()
                            .filter(isEqualHashcodeToString(elements()).negate()).collect(Collectors.toList())),
                        error(message("One, and only one, abstract method should be define on the data type", getAbstractMethods(adtTypeElement.getEnclosedElements()).stream().map(el -> onElement(el)).collect(Collectors.toList()))),

                        adtAcceptMethod ->
                            fold(findOnlyOne(adtAcceptMethod.getTypeParameters())
                                    .filter(t -> findOnlyOne(t.getBounds()).filter(b -> types.isSameType(elements.getTypeElement("java.lang.Object").asType(), b)).isPresent())
                                    .map(TypeParameterElement::asType).flatMap(asTypeVariable::visit)
                                    .filter(tv -> types.isSameType(tv, adtAcceptMethod.getReturnType())),
                                error(message("Method must have one, and only one, type variable (without bounds) that should also be the method return type.", onElement(adtAcceptMethod))),

                                expectedReturnType ->
                                    parseDataConstruction(declaredType, adtTypeVariables, adtAcceptMethod, expectedReturnType).bind(dc ->
                                        validateFieldTypeUniformity(dc)
                                            .map(fields -> adt(typeConstructor(adtTypeElement, declaredType, adtTypeVariables), matchMethod(adtAcceptMethod, expectedReturnType), dc, fields))
                                    )
                            ))));
  }

  public DeriveResult<List<DataArgument>> validateFieldTypeUniformity(DataConstruction construction) {
    return construction.match(new Cases<DeriveResult<List<DataArgument>>>() {
      @Override
      public DeriveResult<List<DataArgument>> multipleConstructors(DataConstructors constructors) {
        List<DataConstructor> allConstructors = constructors.match(new DataConstructors.Cases<List<DataConstructor>>() {
          @Override
          public List<DataConstructor> visitorDispatch(VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors) {
            return constructors;

          }

          @Override
          public List<DataConstructor> functionsDispatch(List<DataConstructor> constructors) {
            return constructors;
          }
        });

        Map<String, List<DataArgument>> fieldsMap = allConstructors.stream().flatMap(c -> c.arguments().stream()).collect(Collectors.groupingBy(DataArgument::fieldName));
        List<String> fieldsWithNonUniformType = fieldsMap.entrySet().stream()
            .filter(e -> e.getValue().stream().anyMatch(da -> !types.isSameType(da.type(), e.getValue().get(0).type())))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        DeriveResult<List<DataArgument>> res;
        if (!fieldsWithNonUniformType.isEmpty()) {
          res = error(message("Field(s) " + fieldsWithNonUniformType + " should have uniform type across all constructors"));
        } else {
          res = result(allConstructors.stream().flatMap(c -> c.arguments().stream().map(DataArgument::fieldName)).distinct().map(fieldName -> fieldsMap.get(fieldName).get(0)).collect(Collectors.toList()));
        }
        return res;
      }

      @Override
      public DeriveResult<List<DataArgument>> oneConstructor(DataConstructor constructor) {
        return result(constructor.arguments());
      }

      @Override
      public DeriveResult<List<DataArgument>> noConstructor() {
        return result(emptyList());
      }
    });
  }

  private DeriveResult<DataConstruction> parseDataConstruction(DeclaredType adtDeclaredType, List<TypeVariable> adtTypeVariables, ExecutableElement adtAcceptMethod, TypeVariable adtAcceptMethodReturnType) {


    Optional<List<P2<VariableElement, DeclaredType>>> parameters = traverseOptional(adtAcceptMethod.getParameters(),
        ve -> asDeclaredType.visit(ve.asType())
            .flatMap(dt -> asTypeElement.visit(dt.asElement()).filter(te -> te.getKind() == ElementKind.INTERFACE).map(te -> p2(dt, te)))
            .flatMap(visitor -> visitor.match(
                (dt, te) -> IntStream.range(0, dt.getTypeArguments().size())
                    .filter(i -> types.isSameType(adtAcceptMethodReturnType, dt.getTypeArguments().get(i)))
                    .mapToObj(i -> p2(dt, te.getTypeParameters().get(i)))
                    .findFirst()
            ))
            .filter(visitor -> visitor.match((dt, expectedReturnType) -> getAbstractMethods(dt.asElement().getEnclosedElements()).stream()
                .allMatch(e -> types.isSameType(expectedReturnType.asType(), e.getReturnType()) && e.getTypeParameters().isEmpty())))
            .map(visitor -> visitor.match((dt, __) -> p2(ve, dt)))
    );

    return fold(parameters,
        error(message("All parameters must be interfaces whose abstract methods must not have any type parameter and should all return the same type variable " + adtAcceptMethodReturnType, onElement(adtAcceptMethod))),

        ps -> findOnlyOne(ps)
            .map(p -> p.<DeriveResult<DataConstruction>>match((ve, dt) -> parseDataConstructionOneArg(adtDeclaredType, adtTypeVariables, ve, dt)))
            .orElseGet(
                () -> ps.isEmpty()
                    ? result(DataConstruction.noConstructor())
                    : parseDataConstructionMultipleAgs(adtDeclaredType, adtTypeVariables, ps)
            )
    );
  }


  private DeriveResult<DataConstruction> parseDataConstructionOneArg(DeclaredType adtDeclaredType, List<TypeVariable> adtTypeVariables, final VariableElement visitorArg, DeclaredType visitorType) {

    final DeriveResult<DataConstruction> result;

    List<ExecutableElement> abstractMethods = getAbstractMethods(visitorType.asElement().getEnclosedElements());

    if (abstractMethods.stream().map(e -> e.getSimpleName().toString()).distinct().count() != abstractMethods.size()) {
      result = error(message("All abstract methods of " + visitorType + " must have a unique name", onElement(visitorArg)));
    } else {
      Function<TypeVariable, Optional<TypeMirror>> typeArgs = typeArgs(visitorType);
      result = Utils.traverseResults(abstractMethods, m -> parseDataConstructor(adtDeclaredType, adtTypeVariables, deconstructor(visitorArg, visitorType, m), typeArgs))
          .map(constructors -> constructors.isEmpty()
              ? noConstructor()
              : findOnlyOne(constructors)
              .map(c -> oneConstructor(c))
              .orElseGet(() -> multipleConstructors(visitorDispatch(visitorArg, visitorType, constructors))));
    }

    return result;

  }

  @Override
  public TypeMirror resolve(TypeMirror typeMirror, Function<TypeVariable, Optional<TypeMirror>> typeArgs) {
    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().isEmpty()
            ? dt
            : types.getDeclaredType(asTypeElement.visit(dt.asElement()).get(),
            dt.getTypeArguments().stream().map(ta -> resolve(ta, typeArgs)).toArray(TypeMirror[]::new))).<TypeMirror>map(dt -> dt)
        .orElse(asTypeVariable.visit(typeMirror).flatMap(typeArgs).orElse(typeMirror));
  }

  @Override
  public TypeName resolveToTypeName(TypeMirror typeMirror, Function<TypeVariable, Optional<TypeName>> typeArgs) {
    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().isEmpty()
            ? TypeName.get(dt)
            : ParameterizedTypeName.get(ClassName.get(asTypeElement.visit(dt.asElement()).get()), dt.getTypeArguments().stream().map(ta -> resolveToTypeName(ta, typeArgs)).toArray(TypeName[]::new)))
        .orElse(asTypeVariable.visit(typeMirror).flatMap(typeArgs).orElse(TypeName.get(typeMirror)));
  }


  public Function<TypeVariable, Optional<TypeMirror>> typeArgs(DeclaredType dt) {
    return tv -> IntStream.range(0, dt.getTypeArguments().size())
        .filter(i -> types.isSameType(tv, asTypeElement.visit(dt.asElement()).get().getTypeParameters().get(i).asType()))
        .mapToObj(i -> dt.getTypeArguments().get(i))
        .findFirst().map(tm -> tm);
  }

  @Override
  public Function<TypeVariable, Optional<TypeMirror>> typeRestrictions(List<TypeRestriction> typeRestrictions) {
    return tv -> typeRestrictions.stream()
        .filter(tr -> types.isSameType(tr.restrictedTypeParameter(), tv))
        .findFirst().map(TypeRestriction::type);
  }

  public MethodSpec.Builder overrideMethodBuilder(final ExecutableElement abstractMethod, Function<TypeVariable, Optional<TypeMirror>> typeArgs) {

    return MethodSpec.methodBuilder(abstractMethod.getSimpleName().toString())
        .addAnnotation(Override.class)
        .addModifiers(abstractMethod.getModifiers().stream().filter(m -> m != Modifier.ABSTRACT).collect(Collectors.toList()))
        .addTypeVariables(abstractMethod.getTypeParameters().stream()
            .map(TypeParameterElement::asType)
            .map(asTypeVariable::visit)
            .flatMap(tvOpt -> tvOpt.map(Collections::singleton).orElse(Collections.<TypeVariable>emptySet()).stream())
            .map(TypeVariableName::get)
            .collect(Collectors.toList()))
        .returns(TypeName.get(resolve(abstractMethod.getReturnType(), typeArgs)))
        .addParameters(abstractMethod.getParameters().stream()
            .map(ve -> ParameterSpec.builder(
                TypeName.get(resolve(ve.asType(), typeArgs)),
                ve.getSimpleName().toString()).build()
            ).collect(Collectors.toList()));
  }

  private DeriveResult<DataConstruction> parseDataConstructionMultipleAgs(DeclaredType adtDeclaredType, List<TypeVariable> adtTypeVariables, List<P2<VariableElement, DeclaredType>> caseHandlers) {
    return Utils.traverseResults(caseHandlers,
        p2 -> p2.match(
            (visitorArg, visitorType) -> parseDataConstructionOneArg(adtDeclaredType, adtTypeVariables, visitorArg, visitorType)
                .bind(construction -> construction.match(new Cases<DeriveResult<DataConstructor>>() {
                  @Override
                  public DeriveResult<DataConstructor> multipleConstructors(DataConstructors constructors) {
                    return error(message("Either use one visitor with multiple dispatch method or multiple functions.", onElement(visitorArg)));
                  }

                  @Override
                  public DeriveResult<DataConstructor> oneConstructor(DataConstructor constructor) {
                    return result(DataConstructor.constructor(visitorArg.getSimpleName().toString(),
                        constructor.arguments().size() > 1 || types.isSameType(constructor.deconstructor().visitorType().getEnclosingType(), adtDeclaredType)
                            ? constructor.arguments()
                            : constructor.arguments().stream().map(da -> da.match((name, type) -> argument(visitorArg.getSimpleName().toString(), type))).collect(Collectors.toList()),
                        constructor.typeVariables(), constructor.typeRestrictions(), constructor.deconstructor()));
                  }

                  @Override
                  public DeriveResult<DataConstructor> noConstructor() {
                    return error(message("No abstract method found!", onElement(visitorArg)));
                  }
                }))))
        .map(DataConstructors::functionsDispatch)
        .map(DataConstruction::multipleConstructors);

  }

  private DeriveResult<DataConstructor> parseDataConstructor(DeclaredType adtDeclaredType, List<TypeVariable> adtTypeParameters, DataDeconstructor deconstructor, Function<TypeVariable, Optional<TypeMirror>> typeArgs) {



    ExecutableElement visitorAbstractMethod = deconstructor.visitorMethod();
    List<DataArgument> constructorArguments = new ArrayList<>();
    List<TypeRestriction> typeRestrictions = new ArrayList<>();
    List<TypeVariable> seenVariables = new ArrayList<>();
    for (VariableElement parameter : visitorAbstractMethod.getParameters()) {
      TypeMirror paramType = resolve(parameter.asType(), typeArgs);

      Optional<TypeRestriction> gadtConstraint = parseGadtConstraint(parameter.getSimpleName().toString(), paramType, adtTypeParameters, typeArgs)
          .filter(tr -> !seenVariables.stream().anyMatch(seenTv -> types.isSameType(seenTv, tr.restrictedTypeParameter())));

      typeRestrictions.addAll(gadtConstraint.map(Collections::singleton).orElse(Collections.emptySet()));
      if (!gadtConstraint.isPresent()) {
        if (!typeRestrictions.isEmpty()) {
          return error(message("Please put type constraints exclusively at the end of parameter list", onElement(visitorAbstractMethod)));
        }
        constructorArguments.add(argument(parameter.getSimpleName().toString(), paramType));
      }
      seenVariables.addAll(typeVariablesIn(paramType).filter(tv -> !seenVariables.stream().anyMatch(seenTv -> types.isSameType(seenTv, tv))).collect(Collectors.toList()));
    }

    String constructorName;
    if (getAbstractMethods(deconstructor.visitorType().asElement().getEnclosedElements()).size() == 1 && !types().isSameType(adtDeclaredType, deconstructor.visitorType().asElement().getEnclosingElement().asType())) {
      constructorName = deconstructor.visitorParam().getSimpleName().toString();
    }
    else {
      constructorName = visitorAbstractMethod.getSimpleName().toString();
    }

    return result(constructor(constructorName, constructorArguments, seenVariables, typeRestrictions, deconstructor)).bind(
        constructor -> {
          VariableElement visitorArg = deconstructor.visitorParam();
          Optional<AnnotationMirror> fieldNamesAnnotationMirror = visitorArg.getAnnotationMirrors().stream()
              .filter(am -> types.isSameType(types.getDeclaredType(elements.getTypeElement(FieldNames.class.getName())), am.getAnnotationType()))
              .findFirst().map(Function.<AnnotationMirror>identity());

          return fold(fieldNamesAnnotationMirror,
              result(constructor),
              am -> {
                FieldNames fieldNames = visitorArg.getAnnotation(FieldNames.class);
                int totalNbArgs = constructor.arguments().size() + constructor.typeRestrictions().size();
                return fieldNames.value().length != totalNbArgs
                    ? error(message("wrong number of field names specified: " + totalNbArgs + " expected.", onAnnotation(visitorArg, am)))
                    : result(
                    DataConstructor.constructor(visitorArg.getSimpleName().toString(),
                        IntStream.range(0, constructor.arguments().size())
                            .mapToObj(i -> argument(fieldNames.value()[i], constructor.arguments().get(i).type()))
                            .collect(Collectors.toList()),

                        constructor.typeVariables(),

                        IntStream.range(constructor.arguments().size(), totalNbArgs)
                            .mapToObj(i -> {
                              TypeRestriction typeRestriction = constructor.typeRestrictions().get(i - constructor.arguments().size());
                              return typeRestriction(typeRestriction.restrictedTypeParameter(), typeRestriction.type(),
                                  argument(fieldNames.value()[i], typeRestriction.dataArgument().type()));
                            })
                            .collect(Collectors.toList())
                        , constructor.deconstructor()));
              }

          );
        }

    );
  }

  @Override
  public Stream<TypeVariable> typeVariablesIn(TypeMirror typeMirror) {
    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().stream().flatMap(this::typeVariablesIn))
        .orElseGet(() -> asTypeVariable.visit(typeMirror).map(Stream::of)
            .orElse(Stream.empty()));
  }


  private Optional<TypeRestriction> parseGadtConstraint(String argName, TypeMirror paramType, List<TypeVariable> adtTypeVariables, Function<TypeVariable, Optional<TypeMirror>> typeArgs) {
    return asDeclaredType.visit(paramType).filter(dt -> dt.asElement().getKind() == ElementKind.INTERFACE)
        .flatMap(dt -> findOnlyOne(getAbstractMethods(dt.asElement().getEnclosedElements()))
            .filter(m -> m.getTypeParameters().isEmpty())
            .flatMap(m -> asTypeVariable.visit(m.getReturnType()).flatMap(typeArgs(dt)).flatMap(asTypeVariable::visit).map(rt -> p2(m, rt)))
            .flatMap(p2 -> p2.match((abstractMethod, rt) ->
                findOnlyOne(abstractMethod.getParameters())
                    .map(p -> p.asType())
                    .flatMap(t ->
                        IntStream.range(0, adtTypeVariables.size())
                            .filter(i -> types.isSameType(adtTypeVariables.get(i), rt))
                            .mapToObj(i -> typeRestriction(adtTypeVariables.get(i), resolve(t, typeArgs(dt)), argument(argName, paramType)))
                            .findFirst()
                    )
            )));
  }
}
