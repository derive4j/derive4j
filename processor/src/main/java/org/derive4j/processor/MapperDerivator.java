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
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataArgument;
import org.derive4j.processor.api.model.DataConstructions;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.DeriveConfig;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;
import org.derive4j.processor.api.model.TypeRestriction;

import static java.util.stream.Stream.concat;
import static org.derive4j.processor.Utils.asTypeElement;
import static org.derive4j.processor.Utils.zip;

class MapperDerivator implements Derivator {

  MapperDerivator(DeriveUtils deriveUtils) {
    this.deriveUtils = deriveUtils;
  }

  public static String mapperFieldName(DataConstructor dc) {
    return dc.name();
  }

  public static String visitorLambdaFactoryName(AlgebraicDataType adt) {

    return adt.matchMethod().element().getParameters().get(0).getSimpleName().toString();
  }

  private final DeriveUtils deriveUtils;

  @Override
  public DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt) {

    return DeriveResult.result(DataConstructions.caseOf(adt.dataConstruction())
        .multipleConstructors(MultipleConstructorsSupport.cases()
            .visitorDispatch((visitorParam, visitorType, constructors) -> createVisitorFactoryAndMappers(adt,
                visitorType, constructors))
            .otherwise(DerivedCodeSpec::none))
        .otherwise(DerivedCodeSpec::none));
  }

  public String mapperApplyMethod(DeriveConfig deriveConfig, DataConstructor dc) {

    int nbArgs = dc.arguments().size() + dc.typeRestrictions().size();
    return (nbArgs == 0)
        ? deriveUtils.function0Model(deriveConfig.flavour()).sam().getSimpleName().toString()
        : nbArgs == 1
            ? deriveUtils.function1Model(deriveConfig.flavour()).sam().getSimpleName().toString()
            : dc.deconstructor().method().getSimpleName().toString();
  }

  public TypeName mapperTypeName(AlgebraicDataType adt, DataConstructor dc) {

    return mapperTypeName(adt, dc, TypeVariableName.get(adt.matchMethod().returnTypeVariable()));
  }

  public TypeName visitorMapperTypeName(AlgebraicDataType adt, DataConstructor dc) {

    TypeName[] argsTypeNames = dc.deconstructor()
        .visitorMethodType()
        .getParameterTypes()
        .stream()
        .map(p -> Utils.asBoxedType.visit(p, deriveUtils.types()))
        .map(dt -> deriveUtils.resolve(dt, v -> Optional.of(v.asElement().asType())))
        .map(TypeName::get)
        .toArray(TypeName[]::new);

    TypeName returnType = TypeName.get(dc.deconstructor().visitorMethodType().getReturnType());

    return argsTypeNames.length == 0
        ? ParameterizedTypeName
            .get(ClassName.get(deriveUtils.function0Model(adt.deriveConfig().flavour()).samClass()), returnType)
        : argsTypeNames.length == 1
            ? ParameterizedTypeName.get(
                ClassName.get(deriveUtils.function1Model(adt.deriveConfig().flavour()).samClass()),
                argsTypeNames[0], returnType)
            : ParameterizedTypeName
                .get(adt.deriveConfig().targetClass().className().nestedClass(mapperInterfaceName(dc)),
                    mapperVariables(dc).toArray(TypeName[]::new));
  }

  public TypeName mapperTypeName(AlgebraicDataType adt, DataConstructor dc, TypeName returnType) {
    return mapperTypeName(adt, dc, adt.typeConstructor().declaredType(), returnType);
  }

  public TypeName mapperTypeName(AlgebraicDataType adt, DataConstructor dc, TypeMirror selfReferenceType,
      TypeName returnType) {

    TypeName[] argsTypeNames = concat(dc.arguments().stream().map(DataArgument::type),
        dc.typeRestrictions().stream().map(TypeRestriction::typeEq).map(DataArgument::type))
            .map(t -> deriveUtils.types().isSameType(t, adt.typeConstructor().declaredType())
                ? selfReferenceType
                : Utils.asBoxedType.visit(t, deriveUtils.types()))
            .map(TypeName::get)
            .toArray(TypeName[]::new);

    Map<TypeVariable, TypeMirror> visitorTypeVarSubstitutions = zip(dc.deconstructor().methodType().getParameterTypes(),
        dc.deconstructor().visitorMethodType().getParameterTypes())
            .stream()
            .flatMap(p -> deriveUtils.unify(p._2(), p._1()).get().entrySet().stream())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (v1, v2) -> v1));

    return adt.dataConstruction().isVisitorDispatch()
        ? argsTypeNames.length == 0
            ? ParameterizedTypeName
                .get(ClassName.get(deriveUtils.function0Model(adt.deriveConfig().flavour()).samClass()), returnType)
            : argsTypeNames.length == 1
                ? ParameterizedTypeName.get(
                    ClassName.get(deriveUtils.function1Model(adt.deriveConfig().flavour()).samClass()),
                    argsTypeNames[0], returnType)
                : ParameterizedTypeName
                    .get(adt.deriveConfig().targetClass().className().nestedClass(mapperInterfaceName(dc)),
                        concat(
                            dc.deconstructor()
                                .argumentTypeVariables()
                                .stream()
                                .map(visitorTypeVarSubstitutions::get)
                                .map(t -> deriveUtils.types().isSameType(t, adt.typeConstructor().declaredType())
                                    ? selfReferenceType
                                    : t)
                                .map(TypeName::get),
                            Stream.of(returnType)).toArray(TypeName[]::new))
        : ParameterizedTypeName.get(
            ClassName.get(asTypeElement.visit(dc.deconstructor().visitorType().asElement()).get()),
            dc.deconstructor()
                .visitorType()
                .getTypeArguments()
                .stream()
                .map(t -> deriveUtils.types().isSameType(t, adt.typeConstructor().declaredType())
                    ? TypeName.get(selfReferenceType)
                    : deriveUtils.types().isSameType(adt.matchMethod().returnTypeVariable(), t)
                        ? returnType
                        : TypeName.get(t))
                .toArray(TypeName[]::new));
  }

  private Stream<TypeVariableName> mapperVariables(DataConstructor dc) {
    return concat(dc.deconstructor().argumentTypeVariables().stream(),
        Stream.of(dc.deconstructor().returnTypeVariable())).map(TypeVariableName::get);
  }

  private TypeSpec mapperTypeSpec(DataConstructor dc) {

    return TypeSpec.interfaceBuilder(mapperInterfaceName(dc))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(mapperVariables(dc).collect(Collectors.toList()))
        .addMethod(MethodSpec.methodBuilder(dc.deconstructor().method().getSimpleName().toString())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameters(zip(dc.deconstructor()
                .method()
                .getParameters(), dc.deconstructor().visitorMethodType().getParameterTypes())
                    .stream()
                    .map(param -> ParameterSpec
                        .builder(TypeName.get(param._2()), param._1().getSimpleName().toString())
                        .build())
                    .collect(Collectors.toList()))
            .returns(TypeName.get(dc.deconstructor().returnTypeVariable()))
            .build())
        .build();
  }

  private DerivedCodeSpec createVisitorFactoryAndMappers(AlgebraicDataType adt, DeclaredType acceptedVisitorType,
      List<DataConstructor> constructors) {

    DeclaredType visitorType = deriveUtils.asDeclaredType(acceptedVisitorType.asElement().asType()).get();
    TypeElement visitorTypeElement = deriveUtils.asTypeElement(visitorType).get();
    String lambdaVisitorClassName = lambdaVisitorClassName(acceptedVisitorType);

    final TypeSpec.Builder lambdaVisitorBuilder = TypeSpec.classBuilder(lambdaVisitorClassName)
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .addTypeVariables(
            visitorTypeElement.getTypeParameters().stream().map(TypeVariableName::get).collect(Collectors.toList()))
        .addSuperinterface(TypeName.get(visitorType))
        .addFields(constructors.stream()
            .map(dc -> FieldSpec.builder(visitorMapperTypeName(adt, dc), mapperFieldName(dc))
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build())
            .collect(Collectors.toList()))
        .addMethods(constructors.stream()
            .map(dc -> deriveUtils.overrideMethodBuilder(dc.deconstructor().method(), visitorType)
                .addStatement("return this.$L.$L($L)", mapperFieldName(dc), mapperApplyMethod(adt.deriveConfig(), dc),
                    Utils.asLambdaParametersString(dc.arguments(), dc.typeRestrictions()))
                .build())
            .collect(Collectors.toList()));

    final MethodSpec.Builder lambdaVisitorConstructor = MethodSpec.constructorBuilder()
        .addParameters(constructors.stream()
            .map(dc -> ParameterSpec.builder(visitorMapperTypeName(adt, dc), mapperFieldName(dc)).build())
            .collect(Collectors.toList()));

    for (DataConstructor dc : constructors) {
      lambdaVisitorConstructor.addStatement("this.$N = $N", mapperFieldName(dc), mapperFieldName(dc));
    }

    TypeSpec lambdaVisitor = lambdaVisitorBuilder.addMethod(lambdaVisitorConstructor.build()).build();

    final MethodSpec lambdaVisitorFactory = MethodSpec.methodBuilder(visitorLambdaFactoryName(adt))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(
            adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
        .addTypeVariable(TypeVariableName.get(adt.matchMethod().returnTypeVariable()))
        .addParameters(constructors.stream()
            .map(dc -> ParameterSpec.builder(mapperTypeName(adt, dc), mapperFieldName(dc)).build())
            .collect(Collectors.toList()))
        .returns(TypeName.get(acceptedVisitorType))
        .addStatement("return new $L<>($L)", lambdaVisitorClassName,
            constructors.stream().map(MapperDerivator::mapperFieldName).reduce((s1, s2) -> s1 + ", " + s2).orElse(""))
        .build();

    return DerivedCodeSpec.codeSpec(
        concat(constructors.stream().filter(dc -> (dc.arguments().size() + dc.typeRestrictions().size()) > 1).map(
            this::mapperTypeSpec), Stream.of(lambdaVisitor)).collect(Collectors.toList()),
        lambdaVisitorFactory);

  }

  static String mapperInterfaceName(DataConstructor dc) {

    return Utils.capitalize(dc.name()) + "Mapper";
  }

  static String lambdaVisitorClassName(DeclaredType visitorType) {
    return "Lambda" + visitorType.asElement().getSimpleName();
  }
}
