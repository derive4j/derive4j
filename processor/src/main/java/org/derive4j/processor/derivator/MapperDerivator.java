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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.derive4j.processor.Utils;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataArgument;
import org.derive4j.processor.api.model.DataConstructions;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.DeriveContext;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;
import org.derive4j.processor.api.model.TypeRestriction;

import static java.util.stream.Stream.concat;

public class MapperDerivator {

  public static DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return DeriveResult.result(
       DataConstructions.cases()
          .multipleConstructors(
             MultipleConstructorsSupport.cases()
                .visitorDispatch(
                   (visitorParam, visitorType, constructors) -> createVisitorFactoryAndMappers(adt, visitorType, constructors, deriveUtils,
                      deriveContext))
                .otherwise(() -> DerivedCodeSpec.none())
          )
          .otherwise(() -> DerivedCodeSpec.none())
          .apply(adt.dataConstruction())
    );
  }

  private static TypeSpec mapperTypeSpec(DeriveUtils deriveUtils, AlgebraicDataType adt, DataConstructor dc) {

    return TypeSpec.interfaceBuilder(mapperInterfaceName(dc))
       .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
       .addTypeVariables(mapperVariables(deriveUtils, adt, dc).collect(Collectors.toList()))
       .addMethod(MethodSpec.methodBuilder(dc.deconstructor().visitorMethod().getSimpleName().toString())
          .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
          .addParameters(concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
             .map(da -> ParameterSpec.builder(deriveUtils.types().isSameType(da.type(), adt.typeConstructor().declaredType())
                                              ? TypeVariableName.get(inductionTypeVarName(adt, dc))
                                              : TypeName.get(da.type()), da.fieldName())
                .build()).collect(Collectors.toList()))
          .returns(TypeName.get(adt.matchMethod().returnTypeVariable()))
          .build()
       )
       .build();
  }

  static DerivedCodeSpec createVisitorFactoryAndMappers(AlgebraicDataType adt, DeclaredType visitorType, List<DataConstructor> constructors,
     DeriveUtils deriveUtils, DeriveContext deriveContext) {

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

    final MethodSpec lambdaVisitorFactory = MethodSpec
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
       concat(
          constructors.stream().filter(dc -> dc.arguments().size() + dc.typeRestrictions().size() > 1)
             .map(dc -> mapperTypeSpec(deriveUtils, adt, dc)),
          Stream.of(lambdaVisitor))
          .collect(Collectors.toList()),
       lambdaVisitorFactory);

  }

  public static String mapperApplyMethod(DeriveUtils deriveUtils, DeriveContext deriveContext, DataConstructor dc) {
    int nbArgs = dc.arguments().size() + dc.typeRestrictions().size();
    return nbArgs == 0
           ? FlavourImpl.supplierApplyMethod(deriveUtils, deriveContext)
           : nbArgs == 1
             ? FlavourImpl.functionApplyMethod(deriveUtils, deriveContext)
             : dc.deconstructor().visitorMethod().getSimpleName().toString();
  }

  static String mapperInterfaceName(DataConstructor dc) {
    return Utils.capitalize(dc.name()) + "Mapper";
  }

  static Stream<TypeVariableName> mapperVariables(DeriveUtils deriveUtils, AlgebraicDataType adt, DataConstructor dc) {

    String recursionTypeVar = inductionTypeVarName(adt, dc);

    Stream<TypeVariableName> cataTypeVar = Utils.fold(findInductiveArgument(deriveUtils, adt, dc),
       Stream.<TypeVariableName>of(),
       __ -> Stream.of(TypeVariableName.get(recursionTypeVar)));

    return concat(concat(dc.typeVariables().stream().map(TypeVariableName::get),
       cataTypeVar),
       Stream.of(TypeVariableName.get(adt.matchMethod().returnTypeVariable())));
  }

  private static String inductionTypeVarName(AlgebraicDataType adt, DataConstructor dc) {
    NameAllocator nameAllocator = new NameAllocator();
    dc.typeVariables().stream().forEach(variable -> nameAllocator.newName(variable.toString(), variable.toString()));
    nameAllocator.newName(adt.matchMethod().returnTypeVariable().toString(), "returnTypeVar");
    return nameAllocator.newName("R", "recursionTypeVar");
  }

  public static TypeName mapperTypeName(AlgebraicDataType adt, DataConstructor dc, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return mapperTypeName(adt,  dc, deriveContext, deriveUtils, TypeVariableName.get(adt.matchMethod().returnTypeVariable()));
  }

  public static TypeName mapperTypeName(AlgebraicDataType adt, DataConstructor dc, DeriveContext deriveContext, DeriveUtils deriveUtils,
     TypeName returnType) {
    TypeName[] argsTypeNames = concat(dc.arguments().stream().map(DataArgument::type),
       dc.typeRestrictions().stream().map(TypeRestriction::idFunction)
          .map(DataArgument::type)).map(t -> Utils.asBoxedType.visit(t, deriveUtils.types())).map(TypeName::get).toArray(TypeName[]::new);

    return
       adt.dataConstruction().isVisitorDispatch()
       ? argsTypeNames.length == 0
         ? ParameterizedTypeName.get(ClassName.get(FlavourImpl.findF0(deriveContext.flavour(), deriveUtils.elements())), returnType)
         : argsTypeNames.length == 1
           ? ParameterizedTypeName
              .get(ClassName.get(FlavourImpl.findF(deriveContext.flavour(), deriveUtils.elements())), argsTypeNames[0], returnType)
           : ParameterizedTypeName.get(Utils.getClassName(deriveContext, mapperInterfaceName(dc)),
              concat(concat(dc.typeVariables().stream().map(TypeVariableName::get),
                 Utils.fold(findInductiveArgument(deriveUtils, adt, dc),
                    Stream.<TypeName>of(),
                    tm -> Stream.of(TypeName.get(tm)))
                 ),
                 Stream.of(returnType)).toArray(TypeName[]::new))
       : deriveUtils.resolveToTypeName(dc.deconstructor().visitorType(),
          tv -> deriveUtils.types().isSameType(tv, adt.matchMethod().returnTypeVariable()) ? Optional.of(returnType) : Optional.empty());
  }

  static Optional<TypeMirror> findInductiveArgument(DeriveUtils deriveUtils, AlgebraicDataType adt, DataConstructor dc) {
    return dc.arguments().stream()
       .map(DataArgument::type)
       .filter(typeMirror -> deriveUtils.types().isSameType(typeMirror, adt.typeConstructor().declaredType()))
       .findFirst();
  }

  public static String mapperFieldName(DataConstructor dc) {
    return dc.name();
  }

  public static String visitorLambdaFactoryName(AlgebraicDataType adt) {
    return adt.matchMethod().element().getParameters().get(0).getSimpleName().toString();
  }
}
