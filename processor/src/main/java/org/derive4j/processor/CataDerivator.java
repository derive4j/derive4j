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
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.SamInterface;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataArgument;
import org.derive4j.processor.api.model.DataArguments;
import org.derive4j.processor.api.model.DataConstructions;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;
import org.derive4j.processor.api.model.TypeRestriction;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static org.derive4j.processor.Utils.fold;
import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.api.DerivedCodeSpec.methodSpec;
import static org.derive4j.processor.api.model.DataConstructions.caseOf;

final class CataDerivator implements Derivator {

  CataDerivator(DeriveUtils utils) {

    this.utils = utils;
    mapperDerivator = new MapperDerivator(utils);
  }

  private final DeriveUtils utils;
  private final MapperDerivator mapperDerivator;

  @Override
  public DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt) {

    return adt.fields()
        .stream()
        .map(DataArguments::getType)
        .anyMatch(tm -> utils.types().isSameType(tm, adt.typeConstructor().declaredType()))
        ? caseOf(adt.dataConstruction())
          .multipleConstructors(MultipleConstructorsSupport.cases()
            .visitorDispatch((visitorParam, visitorType, constructors) -> visitorDispatchImpl(adt, visitorType, constructors))
            .functionsDispatch(dataConstructors -> functionDispatchImpl(adt, dataConstructors)))
          .otherwise(() -> result(DerivedCodeSpec.none()))
        : result(DerivedCodeSpec.none());
  }

  private TypeName cataMapperTypeName(AlgebraicDataType adt, DataConstructor dc) {

    TypeName[] argsTypeNames = concat(dc.arguments().stream().map(DataArgument::type),
        dc.typeRestrictions().stream().map(TypeRestriction::typeEq).map(DataArgument::type)).map(
        t -> Utils.asBoxedType.visit(t, utils.types()))
        .map(tm -> substituteTypeWithRecursionVar(adt, tm))
        .map(TypeName::get)
        .toArray(TypeName[]::new);

    return adt.dataConstruction().isVisitorDispatch()
        ? argsTypeNames.length == 0
               ? ParameterizedTypeName.get(ClassName.get(utils.function0Model(adt.deriveConfig().flavour()).samClass()),
        TypeName.get(adt.matchMethod().returnTypeVariable()))
               : argsTypeNames.length == 1
                      ? ParameterizedTypeName.get(ClassName.get(utils.function1Model(adt.deriveConfig().flavour()).samClass()),
                   argsTypeNames[0], TypeName.get(adt.matchMethod().returnTypeVariable()))
                      : ParameterizedTypeName.get(
                          adt.deriveConfig().targetClass().className().nestedClass(MapperDerivator.mapperInterfaceName(dc)), concat(
                              concat(dc.typeVariables().stream().map(TypeVariableName::get),
                                  fold(mapperDerivator.findInductiveArgument(adt, dc), Stream.of(), tm -> Stream.of(
                                      ParameterizedTypeName.get(
                                          ClassName.get(utils.function0Model(adt.deriveConfig().flavour()).samClass()),
                                          TypeName.get(adt.matchMethod().returnTypeVariable()))))),
                              Stream.of(TypeVariableName.get(adt.matchMethod().returnTypeVariable()))).toArray(TypeName[]::new))

        : TypeName.get(utils.types()
            .getDeclaredType(Utils.asTypeElement.visit(dc.deconstructor().visitorType().asElement()).get(), dc.deconstructor()
                .visitorType()
                .getTypeArguments()
                .stream()
                .map(tm -> substituteTypeWithRecursionVar(adt, tm))
                .toArray(TypeMirror[]::new)));
  }

  private TypeMirror substituteTypeWithRecursionVar(AlgebraicDataType adt, TypeMirror tm) {

    return utils.types().isSameType(tm, adt.typeConstructor().declaredType())
        ? utils.types()
        .getDeclaredType(utils.function0Model(adt.deriveConfig().flavour()).samClass(), adt.matchMethod().returnTypeVariable())
        : tm;
  }

  private DeriveResult<DerivedCodeSpec> functionDispatchImpl(AlgebraicDataType adt, List<DataConstructor> constructors) {

    NameAllocator nameAllocator = nameAllocator(adt, constructors);

    SamInterface f = utils.function1Model(adt.deriveConfig().flavour());
    DeclaredType fDT = utils.types()
        .getDeclaredType(f.samClass(), adt.typeConstructor().declaredType(), adt.matchMethod().returnTypeVariable());
    TypeName returnType = TypeName.get(fDT);
    ExecutableElement abstractMethod = utils.allAbstractMethods(fDT).get(0);

    TypeSpec wrapper = TypeSpec.anonymousClassBuilder("")
        .addSuperinterface(returnType)
        .addMethod(MethodSpec.methodBuilder(abstractMethod.getSimpleName().toString())
            .addAnnotation(Override.class)
            .addModifiers(abstractMethod.getModifiers().stream().filter(m -> m != Modifier.ABSTRACT).collect(toList()))
            .returns(TypeName.get(adt.matchMethod().returnTypeVariable()))
            .addParameter(TypeName.get(adt.typeConstructor().declaredType()), nameAllocator.get("adt var"))
            .addStatement("return $L.$L($L)", nameAllocator.get("adt var"), adt.matchMethod().element().getSimpleName(),
                Utils.joinStringsAsArguments(constructors.stream()
                    .map(constructor -> constructor.arguments()
                        .stream()
                        .map(DataArguments::getType)
                        .noneMatch(tm -> utils.types().isSameType(tm, adt.typeConstructor().declaredType()))
                        ? ('\n' + constructor.name())
                        : CodeBlock.builder()
                            .add("\n($L) -> $L.$L($L)", Utils.joinStringsAsArguments(concat(constructor.arguments()
                                    .stream()
                                    .map(DataArgument::fieldName)
                                    .map(fn -> nameAllocator.clone().newName(fn, fn + " field")), constructor.typeRestrictions()
                                    .stream()
                                    .map(TypeRestriction::typeEq)
                                    .map(DataArgument::fieldName)
                                    .map(fn -> nameAllocator.clone().newName(fn, fn + " field")))), constructor.name(),
                                mapperDerivator.mapperApplyMethod(adt.deriveConfig(), constructor), Utils.joinStringsAsArguments(
                                    concat(constructor.arguments()
                                        .stream()
                                        .map(argument -> utils.types()
                                            .isSameType(argument.type(), adt.typeConstructor().declaredType())
                                            ? ("() -> " +
                                                   f.sam().getSimpleName() +
                                                   '(' +
                                                   nameAllocator.clone()
                                                       .newName(argument.fieldName(), argument.fieldName() + " field") +
                                                   ')')
                                            : argument.fieldName()), constructor.typeRestrictions()
                                        .stream()
                                        .map(TypeRestriction::typeEq)
                                        .map(DataArgument::fieldName))))
                            .build()
                            .toString())))
            .build())
        .build();

    MethodSpec cataMethod = MethodSpec.methodBuilder("cata")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(
            concat(adt.typeConstructor().typeVariables().stream(), Stream.of(adt.matchMethod().returnTypeVariable())).map(
                TypeVariableName::get).collect(toList()))
        .returns(returnType)
        .addParameters(constructors.stream()
            .map(dc -> ParameterSpec.builder(cataMapperTypeName(adt, dc), MapperDerivator.mapperFieldName(dc)).build())
            .collect(toList()))
        .addStatement("return $L", wrapper)
        .build();

    return result(methodSpec(cataMethod));
  }

  private DeriveResult<DerivedCodeSpec> visitorDispatchImpl(AlgebraicDataType adt, DeclaredType visitorType,
      List<DataConstructor> constructors) {

    NameAllocator nameAllocator = nameAllocator(adt, constructors);

    TypeSpec wrapper = TypeSpec.anonymousClassBuilder("")
        .addField(FieldSpec.builder(TypeName.get(visitorType), nameAllocator.get("cata"))
            .initializer(CodeBlock.builder()
                .addStatement("$T.$L($L)", adt.deriveConfig().targetClass().className(),
                    MapperDerivator.visitorLambdaFactoryName(adt), Utils.joinStringsAsArguments(constructors.stream()
                        .map(constructor -> constructor.arguments()
                            .stream()
                            .map(DataArguments::getType)
                            .noneMatch(tm -> utils.types().isSameType(tm, adt.typeConstructor().declaredType()))
                            ? ('\n' + constructor.name())
                            : CodeBlock.builder()
                                .add("\n($L) -> $L.$L($L)", Utils.joinStringsAsArguments(concat(constructor.arguments()
                                        .stream()
                                        .map(DataArgument::fieldName)
                                        .map(fn -> nameAllocator.clone().newName(fn, fn + " field")), constructor
                                        .typeRestrictions()
                                        .stream()
                                        .map(TypeRestriction::typeEq)
                                        .map(DataArgument::fieldName)
                                        .map(fn -> nameAllocator.clone().newName(fn, fn + " field")))), constructor.name(),
                                    mapperDerivator.mapperApplyMethod(adt.deriveConfig(), constructor),
                                    Utils.joinStringsAsArguments(concat(constructor.arguments()
                                        .stream()
                                        .map(argument -> utils.types()
                                            .isSameType(argument.type(), adt.typeConstructor().declaredType())
                                            ? ("() -> " +
                                                   nameAllocator.clone()
                                                       .newName(argument.fieldName(), argument.fieldName() + ' ' + "field") +
                                                   '.' +
                                                   adt.matchMethod().element().getSimpleName() +
                                                   "(this." +
                                                   nameAllocator.get("cata") +
                                                   ')')
                                            : argument.fieldName()), constructor.typeRestrictions()
                                        .stream()
                                        .map(TypeRestriction::typeEq)
                                        .map(DataArgument::fieldName))))
                                .build()
                                .toString())

                    ))
                .build())
            .build())
        .build();

    MethodSpec cataMethod = MethodSpec.methodBuilder("cata")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(
            concat(adt.typeConstructor().typeVariables().stream(), Stream.of(adt.matchMethod().returnTypeVariable())).map(
                TypeVariableName::get).collect(toList()))
        .returns(TypeName.get(utils.types()
            .getDeclaredType(utils.function1Model(adt.deriveConfig().flavour()).samClass(), adt.typeConstructor().declaredType(),
                adt.matchMethod().returnTypeVariable())))
        .addParameters(constructors.stream()
            .map(dc -> ParameterSpec.builder(cataMapperTypeName(adt, dc), MapperDerivator.mapperFieldName(dc)).build())
            .collect(toList()))
        .addStatement("$T $L = $L.$L", TypeName.get(visitorType), nameAllocator.get("cata"), wrapper, nameAllocator.get("cata"))
        .addStatement("return $L -> $L.$L($L)", nameAllocator.get("adt var"), nameAllocator.get("adt var"),
            adt.matchMethod().element().getSimpleName(), nameAllocator.get("cata"))
        .build();

    return result(methodSpec(cataMethod));
  }

  private static NameAllocator nameAllocator(AlgebraicDataType adt, List<DataConstructor> constructors) {

    NameAllocator nameAllocator = new NameAllocator();
    constructors
        .forEach(dc -> nameAllocator.newName(MapperDerivator.mapperFieldName(dc), MapperDerivator.mapperFieldName(dc) + " arg"));
    nameAllocator.newName("cata", "cata");
    nameAllocator.newName(Utils.uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName()), "adt var");
    return nameAllocator;
  }
}
