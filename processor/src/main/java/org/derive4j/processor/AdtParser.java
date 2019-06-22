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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.derive4j.FieldNames;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.MessageLocalization;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataArgument;
import org.derive4j.processor.api.model.DataConstruction;
import org.derive4j.processor.api.model.DataConstructions;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.DataDeconstructor;
import org.derive4j.processor.api.model.DeriveConfig;
import org.derive4j.processor.api.model.MultipleConstructors;
import org.derive4j.processor.api.model.TypeRestriction;

import static java.util.Collections.emptyList;
import static org.derive4j.processor.P2.p2;
import static org.derive4j.processor.Utils.asDeclaredType;
import static org.derive4j.processor.Utils.asTypeElement;
import static org.derive4j.processor.Utils.asTypeVariable;
import static org.derive4j.processor.Utils.findOnlyOne;
import static org.derive4j.processor.Utils.fold;
import static org.derive4j.processor.Utils.p;
import static org.derive4j.processor.Utils.traverseOptional;
import static org.derive4j.processor.Utils.zip;
import static org.derive4j.processor.api.DeriveMessages.message;
import static org.derive4j.processor.api.DeriveResult.error;
import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.api.MessageLocalization.onAnnotation;
import static org.derive4j.processor.api.MessageLocalization.onElement;
import static org.derive4j.processor.api.model.AlgebraicDataTypes.adt;
import static org.derive4j.processor.api.model.DataArguments.dataArgument;
import static org.derive4j.processor.api.model.DataConstruction.multipleConstructors;
import static org.derive4j.processor.api.model.DataConstruction.noConstructor;
import static org.derive4j.processor.api.model.DataConstructions.caseOf;
import static org.derive4j.processor.api.model.DataConstructors.constructor;
import static org.derive4j.processor.api.model.DataDeconstructors.deconstructor;
import static org.derive4j.processor.api.model.MatchMethod.matchMethod;
import static org.derive4j.processor.api.model.MultipleConstructors.visitorDispatch;
import static org.derive4j.processor.api.model.TypeConstructor.typeConstructor;
import static org.derive4j.processor.api.model.TypeRestriction.typeRestriction;

final class AdtParser {

  private final Types       types;
  private final Elements    elements;
  private final DeriveUtils deriveUtils;

  AdtParser(DeriveUtils deriveUtils) {

    types = deriveUtils.types();
    elements = deriveUtils.elements();
    this.deriveUtils = deriveUtils;
  }

  DeriveResult<AlgebraicDataType> parseAlgebraicDataType(final TypeElement adtTypeElement, DeriveConfig deriveConfig) {

    return fold(
        asDeclaredType.visit(adtTypeElement.asType())
            .filter(t -> (t.asElement().getEnclosingElement().getKind() == ElementKind.PACKAGE)
                || t.asElement().getModifiers().contains(Modifier.STATIC)
                || (t.asElement().getKind() == ElementKind.ENUM) || (t.asElement().getKind() == ElementKind.INTERFACE)),
        error(message("Invalid annotated class (only static classes are supported)", onElement(adtTypeElement))),

        declaredType -> fold(
            traverseOptional(declaredType.getTypeArguments(),
                ta -> asTypeVariable.visit(ta).filter(
                    tv -> types.isSameType(elements.getTypeElement("java.lang.Object").asType(), tv.getUpperBound()))),
            error(message("Please use only type variable without bounds as type parameter", onElement(adtTypeElement))),

            adtTypeVariables -> fold(
                findOnlyOne(deriveUtils.allAbstractMethods(declaredType)
                    .stream()
                    .filter(p(this::isEqualHashcodeToString).negate())
                    .collect(Collectors.toList())),
                error(message("One, and only one, abstract method should be define on the data type",
                    deriveUtils.allAbstractMethods(declaredType).stream().map(MessageLocalization::onElement).collect(
                        Collectors.toList()))),

                adtAcceptMethod -> fold(
                    findOnlyOne(adtAcceptMethod.getTypeParameters()).filter(t -> findOnlyOne(t.getBounds())
                        .filter(b -> types.isSameType(deriveUtils.object().classModel().asType(), b))
                        .isPresent()).map(TypeParameterElement::asType).flatMap(asTypeVariable::visit).filter(
                            tv -> types.isSameType(tv, adtAcceptMethod.getReturnType())),
                    error(message(
                        "Method must have one, and only one, type variable (without bounds) that should also be the method "
                            + "return type.",
                        onElement(adtAcceptMethod))),

                    expectedReturnType -> parseDataConstruction(declaredType, adtTypeVariables, adtAcceptMethod,
                        expectedReturnType)
                            .bind(dc -> validateFieldTypeUniformity(dc).map(fields -> adt(deriveConfig,
                                typeConstructor(adtTypeElement, declaredType, adtTypeVariables),
                                matchMethod(adtAcceptMethod, expectedReturnType), dc, fields)))))));
  }

  private DeriveResult<List<DataArgument>> validateFieldTypeUniformity(DataConstruction construction) {

    return caseOf(construction)

        .multipleConstructors(multipleConstructors -> {

          Map<String, List<DataArgument>> fieldsMap = multipleConstructors.constructors()
              .stream()
              .flatMap(c -> c.arguments().stream())
              .collect(Collectors.groupingBy(DataArgument::fieldName));

          List<String> fieldsWithNonUniformType = fieldsMap.entrySet()
              .stream()
              .filter(
                  e -> e.getValue().stream().anyMatch(da -> !types.isSameType(da.type(), e.getValue().get(0).type())))
              .map(Map.Entry::getKey)
              .collect(Collectors.toList());

          DeriveResult<List<DataArgument>> res = !fieldsWithNonUniformType.isEmpty()
              ? error(
                  message("Field(s) " + fieldsWithNonUniformType + " should have uniform type across all constructors"))
              : result(multipleConstructors.constructors()
                  .stream()
                  .flatMap(c -> c.arguments().stream().map(DataArgument::fieldName))
                  .distinct()
                  .map(fieldName -> fieldsMap.get(fieldName).get(0))
                  .collect(Collectors.toList()));
          return res;
        })

        .oneConstructor(constructor -> result(constructor.arguments()))

        .noConstructor(() -> result(emptyList()));
  }

  private boolean isEqualHashcodeToString(ExecutableElement executableElement) {

    return elements.overrides(executableElement, deriveUtils.object().equalsMethod(), deriveUtils.object().classModel())
        || elements.overrides(executableElement, deriveUtils.object().hashCodeMethod(),
            deriveUtils.object().classModel())
        || elements.overrides(executableElement, deriveUtils.object().toStringMethod(),
            deriveUtils.object().classModel());
  }

  private DeriveResult<DataConstruction> parseDataConstruction(DeclaredType adtDeclaredType,
      List<TypeVariable> adtTypeVariables, ExecutableElement adtAcceptMethod, TypeVariable adtAcceptMethodReturnType) {

    ExecutableType adtAcceptMethodType = (ExecutableType) types.asMemberOf(adtDeclaredType, adtAcceptMethod);

    List<P2<VariableElement, TypeMirror>> acceptMethodParameters = zip(adtAcceptMethod.getParameters(),
        adtAcceptMethodType.getParameterTypes());

    Optional<List<P2<VariableElement, DeclaredType>>> parameters = traverseOptional(acceptMethodParameters,
        acceptParam -> acceptParam
            .match((paramEl, paramType) -> asDeclaredType.visit(paramType)
                .filter(paramDeclaredType -> paramDeclaredType.asElement().getKind() == ElementKind.INTERFACE)
                .map(paramDeclaredType -> p2(paramEl, paramDeclaredType)))
            .filter(visitor -> visitor
                .match((paramEl, paramDeclaredType) -> deriveUtils.allAbstractMethods(paramDeclaredType)
                    .stream()
                    .map(visitorAbstractMethod -> (ExecutableType) types.asMemberOf(paramDeclaredType,
                        visitorAbstractMethod))
                    .allMatch(e -> types.isSameType(adtAcceptMethodType.getReturnType(), e.getReturnType())
                        && e.getTypeVariables().isEmpty()))));

    return fold(parameters, error(message(
        "All parameters must be interfaces whose abstract methods must not have any type parameter and should all return the "
            + "same type variable " + adtAcceptMethodReturnType,
        onElement(adtAcceptMethod))),

        ps -> findOnlyOne(ps)
            .map(p -> p.match((ve, dt) -> parseDataConstructionOneArg(adtDeclaredType, adtTypeVariables, ve, dt)))
            .orElseGet(() -> ps.isEmpty()
                ? result(noConstructor())
                : parseDataConstructionMultipleAgs(adtDeclaredType, adtTypeVariables, ps)));
  }

  private List<TypeVariable> methodTypeVariables(ExecutableType method) {

    return method
        .getParameterTypes()
        .stream()
        .flatMap(t -> deriveUtils.typeVariablesIn(t).stream())
        .distinct()
        .collect(Collectors.toList());
  }

  private DeriveResult<DataConstruction> parseDataConstructionOneArg(DeclaredType adtDeclaredType,
      List<TypeVariable> adtTypeVariables, VariableElement visitorArg, DeclaredType visitorType) {

    final DeriveResult<DataConstruction> result;

    List<ExecutableElement> abstractMethods = deriveUtils.allAbstractMethods(visitorType);

    if (abstractMethods.stream().map(e -> e.getSimpleName().toString()).distinct().count() != abstractMethods.size()) {
      result = error(
          message("All abstract methods of " + visitorType + " must have a unique name", onElement(visitorArg)));
    } else {
      result = Utils
          .traverseResults(abstractMethods,
              m -> {
                ExecutableType visitorMethodType = (ExecutableType) types
                    .asMemberOf(asDeclaredType.visit(visitorType.asElement().asType()).get(), m);
                return parseDataConstructor(adtDeclaredType, adtTypeVariables,
                    deconstructor(visitorArg, visitorType, (ExecutableType) types.asMemberOf(visitorType, m),
                        visitorMethodType, m, methodTypeVariables(visitorMethodType),
                        asTypeVariable.visit(visitorMethodType.getReturnType()).get()),
                    abstractMethods.indexOf(m));
              })
          .map(constructors -> constructors.isEmpty()
              ? noConstructor()
              : findOnlyOne(constructors).map(DataConstruction::oneConstructor)
                  .orElseGet(() -> multipleConstructors(visitorDispatch(visitorArg, visitorType, constructors))));
    }

    return result;

  }

  private DeriveResult<DataConstruction> parseDataConstructionMultipleAgs(DeclaredType adtDeclaredType,
      List<TypeVariable> adtTypeVariables, List<P2<VariableElement, DeclaredType>> caseHandlers) {

    List<VariableElement> variableElements = caseHandlers.stream().map(P2s::get_1).collect(Collectors.toList());

    return Utils.traverseResults(caseHandlers, p2 -> p2.match((visitorArg, visitorType) -> {
      int index = variableElements.indexOf(visitorArg);
      return parseDataConstructionOneArg(adtDeclaredType, adtTypeVariables, visitorArg, visitorType)
          .bind(DataConstructions.cases()

              .multipleConstructors(__ -> DeriveResult.<DataConstructor>error(
                  message("Either use one visitor with multiple dispatch method or " + "multiple functions.",
                      onElement(visitorArg))))

              .oneConstructor(constructor -> result(constructor(visitorArg.getSimpleName().toString(), index,
                  constructor.typeVariables(),
                  ((constructor.arguments().size() > 1)
                      || types.isSameType(constructor.deconstructor().visitorType().getEnclosingType(), adtDeclaredType)
                      || fieldNamesAnnotation(visitorArg).isPresent())
                          ? constructor.arguments()
                          : constructor.arguments()
                              .stream()
                              .map(da -> dataArgument(visitorArg.getSimpleName().toString(), da.type()))
                              .collect(Collectors.toList()),
                  constructor.typeRestrictions(),
                  deriveUtils.resolve(adtDeclaredType, deriveUtils.typeRestrictions(constructor.typeRestrictions())),
                  constructor.deconstructor())))

              .noConstructor(() -> error(message("No abstract method found!", onElement(visitorArg)))));
    })).map(MultipleConstructors::functionsDispatch).map(DataConstruction::multipleConstructors);

  }

  private DeriveResult<DataConstructor> parseDataConstructor(DeclaredType adtDeclaredType,
      List<TypeVariable> adtTypeParameters, DataDeconstructor deconstructor, int index) {

    ExecutableElement visitorMethod = deconstructor.method();
    ExecutableType visitorMethodType = deconstructor.methodType();
    List<DataArgument> constructorArguments = new ArrayList<>();
    List<TypeRestriction> typeRestrictions = new ArrayList<>();
    List<TypeVariable> seenVariables = new ArrayList<>();
    for (P2<VariableElement, TypeMirror> parameter : Utils
        .<VariableElement, TypeMirror>zip(visitorMethod.getParameters(), visitorMethodType.getParameterTypes())) {

      VariableElement paramElement = parameter._1();
      TypeMirror paramType = parameter._2();

      Optional<TypeRestriction> gadtConstraint = parseGadtConstraint(paramElement.getSimpleName().toString(), paramType,
          adtTypeParameters).filter(
              tr -> seenVariables.stream().noneMatch(seenTv -> types.isSameType(seenTv, tr.restrictedTypeVariable())));

      typeRestrictions.addAll(gadtConstraint.map(Collections::singleton).orElse(Collections.emptySet()));
      if (!gadtConstraint.isPresent()) {
        if (!typeRestrictions.isEmpty()) {
          return error(message("Please put type equality constraints exclusively at the end of parameter list",
              onElement(visitorMethod)));
        }
        constructorArguments.add(dataArgument(paramElement.getSimpleName().toString(), paramType));
      }
      seenVariables.addAll(deriveUtils.typeVariablesIn(paramType)
          .stream()
          .filter(tv -> seenVariables.stream().noneMatch(seenTv -> types.isSameType(seenTv, tv)))
          .collect(Collectors.toList()));
    }

    VariableElement visitorArg = deconstructor.visitorParam();
    Optional<AnnotationMirror> fieldNamesAnnotationMirror = fieldNamesAnnotation(visitorArg);

    DeclaredType returnedType = deriveUtils.resolve(adtDeclaredType, deriveUtils.typeRestrictions(typeRestrictions));

    return fold(fieldNamesAnnotationMirror, result(constructor(visitorMethod.getSimpleName().toString(), index,
        seenVariables, constructorArguments, typeRestrictions, returnedType, deconstructor)), am -> {
          FieldNames fieldNames = visitorArg.getAnnotation(FieldNames.class);
          int totalNbArgs = constructorArguments.size() + typeRestrictions.size();
          return (fieldNames.value().length != totalNbArgs)
              ? error(message("wrong number of field names specified: " + totalNbArgs + " expected.",
                  onAnnotation(visitorArg, am)))
              : result(constructor(visitorArg.getSimpleName().toString(), index, seenVariables,
                  IntStream.range(0, constructorArguments.size())
                      .mapToObj(i -> dataArgument(fieldNames.value()[i], constructorArguments.get(i).type()))
                      .collect(Collectors.toList()),

                  IntStream.range(constructorArguments.size(), totalNbArgs).mapToObj(i -> {
                    TypeRestriction typeRestriction = typeRestrictions.get(i - constructorArguments.size());
                    return typeRestriction(typeRestriction.restrictedTypeVariable(), typeRestriction.refinementType(),
                        dataArgument(fieldNames.value()[i], typeRestriction.typeEq().type()));
                  }).collect(Collectors.toList()), returnedType, deconstructor));
        }

    );
  }

  private Optional<AnnotationMirror> fieldNamesAnnotation(VariableElement visitorArg) {

    return visitorArg.getAnnotationMirrors()
        .stream()
        .filter(am -> types.isSameType(types.getDeclaredType(elements.getTypeElement(FieldNames.class.getName())),
            am.getAnnotationType()))
        .findFirst()
        .map(Function.<AnnotationMirror>identity());
  }

  private Optional<TypeRestriction> parseGadtConstraint(String argName, TypeMirror paramType,
      List<TypeVariable> adtTypeVariables) {

    return asDeclaredType.visit(paramType)
        .filter(dt -> asTypeElement.visit(dt.asElement())
            .filter(te -> te.getQualifiedName().contentEquals("org.derive4j.hkt.TypeEq"))
            .isPresent())
        .flatMap(typeEq -> adtTypeVariables.stream()
            .filter(tv -> types.isSameType(tv, typeEq.getTypeArguments().get(1)))
            .findFirst()
            .map(restrictedTypeVariable -> typeRestriction(restrictedTypeVariable, typeEq.getTypeArguments().get(0),
                dataArgument(argName, paramType))));
  }
}
