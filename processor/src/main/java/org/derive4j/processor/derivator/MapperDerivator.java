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

import com.squareup.javapoet.*;
import org.derive4j.processor.Utils;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.*;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MapperDerivator {

  public static DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return DeriveResult.result(
        DataConstructions.cases()
            .multipleConstructors(
                MultipleConstructorsSupport.cases()
                    .visitorDispatch((visitorParam, visitorType, constructors) -> createVisitorFactoryAndMappers(adt, visitorType, constructors, deriveUtils, deriveContext))
                    .otherwise(() -> DerivedCodeSpec.none())
            )
            .otherwise(() -> DerivedCodeSpec.none())
            .apply(adt.dataConstruction())
    );
  }


  private static TypeSpec mapperTypeSpec(AlgebraicDataType adt, DataConstructor dc) {

    return TypeSpec.interfaceBuilder(mapperInterfaceName(dc))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(mapperVariables(adt, dc).map(TypeVariableName::get).collect(Collectors.toList()))
        .addMethod(MethodSpec.methodBuilder(dc.deconstructor().visitorMethod().getSimpleName().toString())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameters(Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
                .map(da -> ParameterSpec.builder(TypeName.get(da.type()), da.fieldName()).build()).collect(Collectors.toList()))
            .returns(TypeName.get(adt.matchMethod().returnTypeVariable()))
            .build()
        )
        .build();
  }

  static DerivedCodeSpec createVisitorFactoryAndMappers(AlgebraicDataType adt, DeclaredType visitorType, List<DataConstructor> constructors, DeriveUtils deriveUtils, DeriveContext deriveContext) {

    String lambdaVisitorClassName = "Lambda" + visitorType.asElement().getSimpleName().toString();
    final TypeSpec.Builder lambdaVisitorBuilder = TypeSpec.classBuilder(lambdaVisitorClassName)
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
        .addTypeVariable(TypeVariableName.get(adt.matchMethod().returnTypeVariable()))
        .addSuperinterface(TypeName.get(visitorType))
        .addFields(constructors.stream()
            .map(dc -> FieldSpec.builder(mapperTypeName(adt, dc, deriveContext, deriveUtils), mapperFieldName(dc))
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build())
            .collect(Collectors.toList()))
        .addMethods(constructors.stream()
            .map(dc -> deriveUtils.overrideMethodBuilder(dc.deconstructor().visitorMethod(), deriveUtils.typeArgs(visitorType))
                .addStatement("return this.$L.$L($L)",
                    mapperFieldName(dc),
                    mapperApplyMethod(deriveUtils, deriveContext, dc),
                    Utils.asLambdaParametersString(dc.arguments(), dc.typeRestrictions())
                ).build())
            .collect(Collectors.toList()));


    final MethodSpec.Builder lambdaVisitorConstructor = MethodSpec.constructorBuilder()
        .addParameters(constructors.stream().map(dc -> ParameterSpec.builder(mapperTypeName(adt, dc, deriveContext, deriveUtils),
            mapperFieldName(dc)).build()).collect(Collectors.toList()));

    for (DataConstructor dc : constructors) {
      lambdaVisitorConstructor.addStatement("this.$N = $N", mapperFieldName(dc), mapperFieldName(dc));
    }

    TypeSpec lambdaVisitor = lambdaVisitorBuilder.addMethod(lambdaVisitorConstructor.build()).build();

    final MethodSpec lambdaVistorFactory = MethodSpec
        .methodBuilder(visitorLambdaFactoryName(adt))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
        .addTypeVariable(TypeVariableName.get(adt.matchMethod().returnTypeVariable()))
        .addParameters(constructors.stream().map(dc -> ParameterSpec.builder(mapperTypeName(adt, dc, deriveContext, deriveUtils),
            mapperFieldName(dc)).build()).collect(Collectors.toList()))
        .returns(TypeName.get(visitorType))
        .addStatement("return new $L<>($L)", lambdaVisitorClassName,
            constructors.stream().map(dc -> mapperFieldName(dc)).reduce((s1, s2) -> s1 + ", " + s2).orElse(""))
        .build();

    return DerivedCodeSpec.codeSpec(
        Stream.concat(
            constructors.stream().filter(dc -> dc.arguments().size() + dc.typeRestrictions().size() > 1).map(dc -> mapperTypeSpec(adt, dc)),
            Stream.of(lambdaVisitor))
            .collect(Collectors.toList()),
        lambdaVistorFactory);

  }

  private static String mapperApplyMethod(DeriveUtils deriveUtils, DeriveContext deriveContext, DataConstructor dc) {
    int nbArgs = dc.arguments().size() + dc.typeRestrictions().size();
    return nbArgs == 0
        ? Utils.getAbstractMethods(FlavourImpl.findF0(deriveContext.flavour(), deriveUtils.elements()).getEnclosedElements()).get(0).getSimpleName().toString()
        : nbArgs == 1
        ? Utils.getAbstractMethods(FlavourImpl.findF(deriveContext.flavour(), deriveUtils.elements()).getEnclosedElements()).get(0).getSimpleName().toString()
        : dc.deconstructor().visitorMethod().getSimpleName().toString();
  }


  private static String mapperInterfaceName(DataConstructor dc) {
    return Utils.capitalize(dc.name()) + "Mapper";
  }


  static Stream<TypeVariable> mapperVariables(AlgebraicDataType adt, DataConstructor dc) {
    return Stream.concat(dc.typeVariables().stream(), Stream.of(adt.matchMethod().returnTypeVariable()));
  }

  public static TypeName mapperTypeName(AlgebraicDataType adt, DataConstructor dc, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    TypeName[] argsTypeNames = Stream.concat(dc.arguments().stream().map(DataArgument::type),
        dc.typeRestrictions().stream().map(TypeRestriction::idFunction)
            .map(DataArgument::type)).map(t -> Utils.asBoxedType.visit(t, deriveUtils.types())).map(TypeName::get).toArray(TypeName[]::new);

    return
        adt.dataConstruction().isVisitorDispatch()
            ?
            argsTypeNames.length == 0 ?
                ParameterizedTypeName.get(ClassName.get(FlavourImpl.findF0(deriveContext.flavour(), deriveUtils.elements())), TypeName.get(adt.matchMethod().returnTypeVariable()))
                : argsTypeNames.length == 1
                ? ParameterizedTypeName.get(ClassName.get(FlavourImpl.findF(deriveContext.flavour(), deriveUtils.elements())),
                argsTypeNames[0],
                TypeName.get(adt.matchMethod().returnTypeVariable()))
                : ParameterizedTypeName.get(Utils.getClassName(deriveContext, mapperInterfaceName(dc)),
                mapperVariables(adt, dc).map(TypeName::get).toArray(TypeName[]::new))
            : TypeName.get(dc.deconstructor().visitorType());
  }

  public static String mapperFieldName(DataConstructor dc) {
    return dc.name();
  }

  public static String visitorLambdaFactoryName(AlgebraicDataType adt) {
    return adt.matchMethod().element().getParameters().get(0).getSimpleName().toString();
  }
}
