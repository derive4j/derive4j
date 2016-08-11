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
package org.derive4j.processor.derivator;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.derive4j.Visibility;
import org.derive4j.processor.Utils;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataArgument;
import org.derive4j.processor.api.model.DataConstructions;
import org.derive4j.processor.api.model.DeriveContext;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;
import org.derive4j.processor.api.model.TypeRestriction;

import static org.derive4j.Visibility.Smart;
import static org.derive4j.processor.Utils.joinStringsAsArguments;
import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.derivator.StrictConstructorDerivator.smartConstructor;

public final class ModiersDerivator {

  public static DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {

    return result(adt.fields()
        .stream()
        .map(da -> generateModfier(da, adt, deriveContext, deriveUtils))
        .reduce(DerivedCodeSpec.none(), DerivedCodeSpec::append));
  }

  private static DerivedCodeSpec generateModfier(DataArgument field, AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {

    String moderArg = field.fieldName() + "Mod";
    TypeElement f1 = FlavourImpl.findF(deriveContext.flavour(), deriveUtils.elements());
    String f1Apply = deriveUtils.allAbstractMethods(f1).get(0).getSimpleName().toString();

    List<TypeVariable> uniqueTypeVariables = getUniqueTypeVariables(field, adt.fields(), deriveUtils);

    Function<TypeVariable, Optional<TypeName>> polymorphism = tv -> uniqueTypeVariables.stream()
        .filter(utv -> deriveUtils.types().isSameType(tv, utv))
        .findFirst()
        .map(utv -> TypeVariableName.get(adt.matchMethod().returnTypeVariable().toString() + utv.toString()));

    TypeMirror boxedFieldType = field.type().accept(Utils.asBoxedType, deriveUtils.types());

    Visibility visibility = deriveContext.visibility();
    String smartSuffix = visibility == Smart
                         ? "0"
                         : "";

    String modMethodName = "mod" + Utils.capitalize(field.fieldName()) + smartSuffix;

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(modMethodName);

    String adtArg = nameAllocator.newName(Utils.uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName()));



    MethodSpec.Builder modBuilder = MethodSpec.methodBuilder(modMethodName)
        .addModifiers(Modifier.STATIC)
        .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
        .addTypeVariables(uniqueTypeVariables.stream()
            .map(utv -> TypeVariableName.get(adt.matchMethod().returnTypeVariable().toString() + utv.toString()))
            .collect(Collectors.toList()))
        .addParameter(ParameterSpec.builder(
            ParameterizedTypeName.get(ClassName.get(f1), TypeName.get(boxedFieldType), deriveUtils.resolveToTypeName(boxedFieldType, polymorphism)),
            moderArg).build())
        .returns(ParameterizedTypeName.get(ClassName.get(f1), TypeName.get(adt.typeConstructor().declaredType()),
            deriveUtils.resolveToTypeName(adt.typeConstructor().declaredType(), polymorphism)));

    if (visibility != Smart) {
      modBuilder.addModifiers(Modifier.PUBLIC);
    }

    if (adt.dataConstruction().constructors().stream().anyMatch(dc -> !dc.typeRestrictions().isEmpty())) {

      modBuilder.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build());
    }

    CodeBlock lambdas = adt.dataConstruction()
        .constructors()
        .stream()
        .map(constructor -> {
          String constructorName =  smartConstructor(constructor, deriveContext) ? constructor.name() + "0" : constructor.name();
          return CodeBlock.builder()
              .add("($L) -> " +
                      (constructor.typeRestrictions().isEmpty()
                       ? "$L"
                       : "($T) ") +
                      "$L($L)", joinStringsAsArguments(Stream.concat(
                  constructor.arguments().stream().map(DataArgument::fieldName).map(fn -> nameAllocator.clone().newName(fn, fn + " field")),
                  constructor.typeRestrictions()
                      .stream()
                      .map(TypeRestriction::idFunction)
                      .map(DataArgument::fieldName)
                      .map(fn -> nameAllocator.clone().newName(fn, fn + " field")))), constructor.typeRestrictions().isEmpty()
                                                                                          ? ""
                                                                                          : ClassName.get(adt.typeConstructor().typeElement()), constructorName,
                  joinStringsAsArguments(constructor.arguments()
                      .stream()
                      .map(DataArgument::fieldName)
                      .map(fn -> fn.equals(field.fieldName())
                                 ? (moderArg + '.' + f1Apply + "(" + nameAllocator.clone().newName(fn, fn + " field") + ")")
                                 : nameAllocator.clone().newName(fn, fn + " field"))))
              .build();
        })
        .reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build())
        .orElse(CodeBlock.builder().build());

    String setterArgName = "new" + Utils.capitalize(field.fieldName());
    MethodSpec.Builder setMethod = MethodSpec.methodBuilder("set" + Utils.capitalize(field.fieldName()) + smartSuffix)
        .addModifiers(Modifier.STATIC)
        .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
        .addTypeVariables(uniqueTypeVariables.stream()
            .map(utv -> TypeVariableName.get(adt.matchMethod().returnTypeVariable().toString() + utv.toString()))
            .collect(Collectors.toList()))
        .addParameter(ParameterSpec.builder(deriveUtils.resolveToTypeName(boxedFieldType, polymorphism), setterArgName).build())
        .returns(ParameterizedTypeName.get(ClassName.get(f1), TypeName.get(adt.typeConstructor().declaredType()),
            deriveUtils.resolveToTypeName(adt.typeConstructor().declaredType(), polymorphism)))
        .addStatement("return $L(__ -> $L)", modMethodName, setterArgName);

    if (visibility != Smart) {
      setMethod.addModifiers(Modifier.PUBLIC);
    }

    return DataConstructions.cases()
        .multipleConstructors(MultipleConstructorsSupport.cases()
            .visitorDispatch((visitorParam, visitorType, constructors) -> {

              String visitorVarName = Utils.uncapitalize(visitorType.asElement().getSimpleName());

              return DerivedCodeSpec.methodSpecs(Arrays.asList(setMethod.build(), modBuilder.addStatement("$T $L = $T.$L($L)",
                  deriveUtils.resolveToTypeName(visitorType, tv -> deriveUtils.types().isSameType(tv, adt.matchMethod().returnTypeVariable())
                                                                   ? Optional.of(
                      deriveUtils.resolveToTypeName(adt.typeConstructor().declaredType(), polymorphism))
                                                                   : Optional.empty()), visitorVarName,
                  ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName()), MapperDerivator.visitorLambdaFactoryName(adt),
                  lambdas)
                  .addStatement("return $1L -> $1L.$2L($3L)", adtArg, adt.matchMethod().element().getSimpleName(), visitorVarName)
                  .build()));
            })
            .functionsDispatch(constructors -> DerivedCodeSpec.methodSpecs(Arrays.asList(setMethod.build(),
                modBuilder.addStatement("return $1L -> $1L.$2L($3L)", adtArg, adt.matchMethod().element().getSimpleName(), lambdas).build()))))
        .oneConstructor(constructor -> DerivedCodeSpec.methodSpecs(Arrays.asList(setMethod.build(),
            modBuilder.addStatement("return $1L -> $1L.$2L($3L)", adtArg, adt.matchMethod().element().getSimpleName(), lambdas).build())))
        .noConstructor(DerivedCodeSpec::none)
        .apply(adt.dataConstruction());
  }

  private static List<TypeVariable> getUniqueTypeVariables(DataArgument field, List<DataArgument> allFields, DeriveUtils deriveUtils) {

    return deriveUtils.typeVariablesIn(field.type())
        .stream()
        .filter(tv -> allFields.stream()
            .filter(da -> !field.fieldName().equals(da.fieldName()))
            .flatMap(da -> deriveUtils.typeVariablesIn(da.type()).stream())
            .noneMatch(tv2 -> deriveUtils.types().isSameType(tv, tv2)))
        .collect(Collectors.toList());
  }

}
