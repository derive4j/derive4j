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
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.*;
import org.derive4j.processor.Utils;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MapperDerivator {

  public static DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return DeriveResult.result(
        adt.dataConstruction().match(new DataConstruction.Cases<DerivedCodeSpec>() {
          @Override
          public DerivedCodeSpec multipleConstructors(DataConstructors constructors) {
            return constructors.match(new DataConstructors.Cases<DerivedCodeSpec>() {

              @Override
              public DerivedCodeSpec visitorDispatch(VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors) {
                return createVisitorFactoryAndMappers(adt, visitorParam, visitorType, constructors, deriveUtils, deriveContext);
              }

              @Override
              public DerivedCodeSpec functionsDispatch(List<DataConstructor> constructors) {
                return DerivedCodeSpec.none();
              }
            });
          }

          @Override
          public DerivedCodeSpec oneConstructor(DataConstructor constructor) {
            return DerivedCodeSpec.none();
          }

          @Override
          public DerivedCodeSpec noConstructor() {
            return DerivedCodeSpec.none();
          }
        })
    );
  }


  private static TypeSpec mapperTypeSpec(AlgebraicDataType adt, DataConstructor dc) {

    return TypeSpec.interfaceBuilder(mapperInterfaceName(dc))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(mapperVariables(adt, dc).map(TypeVariableName::get).collect(Collectors.toList()))
        .addMethod(MethodSpec.methodBuilder(dc.deconstructor().visitorMethod().getSimpleName().toString())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameters(Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::dataArgument))
                .map(da -> ParameterSpec.builder(TypeName.get(da.type()), da.fieldName()).build()).collect(Collectors.toList()))
            .returns(TypeName.get(adt.matchMethod().returnTypeVariable()))
            .build()
        )
        .build();
  }

  static DerivedCodeSpec createVisitorFactoryAndMappers(AlgebraicDataType adt, VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors, DeriveUtils deriveUtils, DeriveContext deriveContext) {

    String lambdaVisitorClassName = "Lambda" + visitorType.asElement().getSimpleName().toString();
    final TypeSpec.Builder lambdaVisitorBuilder = TypeSpec.classBuilder(lambdaVisitorClassName)
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
        .addTypeVariable(TypeVariableName.get(adt.matchMethod().returnTypeVariable()))
        .addSuperinterface(TypeName.get(visitorType))
        .addFields(constructors.stream()
            .map(dc -> FieldSpec.builder(mapperTypeName(adt, dc, deriveContext), mapperFieldName(dc))
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build())
            .collect(Collectors.toList()))
        .addMethods(constructors.stream()
            .map(dc -> deriveUtils.overrideMethodBuilder(dc.deconstructor().visitorMethod(), deriveUtils.typeArgs(visitorType))
                .addStatement("return this.$L.$L($L)",
                    mapperFieldName(dc),
                    dc.deconstructor().visitorMethod().getSimpleName().toString(),
                    Utils.asLambdaParametersString(dc.arguments(), dc.typeRestrictions())
                ).build())
            .collect(Collectors.toList()));


    final MethodSpec.Builder lambdaVisitorConstructor = MethodSpec.constructorBuilder()
        .addParameters(constructors.stream().map(dc -> ParameterSpec.builder(mapperTypeName(adt, dc, deriveContext),
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
        .addParameters(constructors.stream().map(dc -> ParameterSpec.builder(mapperTypeName(adt, dc, deriveContext),
            mapperFieldName(dc)).build()).collect(Collectors.toList()))
        .returns(TypeName.get(visitorType))
        .addStatement("return new $L<>($L)", lambdaVisitorClassName,
            constructors.stream().map(dc -> mapperFieldName(dc)).reduce((s1, s2) -> s1 + ", " + s2).orElse(""))
        .build();

    return DerivedCodeSpec.codeSpec(
        Stream.concat(
            constructors.stream().map(dc -> mapperTypeSpec(adt, dc)),
            Stream.of(lambdaVisitor))
            .collect(Collectors.toList()),
        lambdaVistorFactory);

  }


  private static String mapperInterfaceName(DataConstructor dc) {
    return Utils.capitalize(dc.name()) + "Mapper";
  }


  static Stream<TypeVariable> mapperVariables(AlgebraicDataType adt, DataConstructor dc) {
    return Stream.concat(dc.typeVariables().stream(), Stream.of(adt.matchMethod().returnTypeVariable()));
  }

  public static TypeName mapperTypeName(AlgebraicDataType adt, DataConstructor dc, DeriveContext deriveContext) {
    return adt.dataConstruction().isVisitorDispatch()
        ? ParameterizedTypeName.get(Utils.getClassName(deriveContext, mapperInterfaceName(dc)),
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
