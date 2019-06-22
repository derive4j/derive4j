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

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.DataConstructors;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;
import org.derive4j.processor.api.model.TypeRestriction;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static org.derive4j.processor.MapperDerivator.lambdaVisitorClassName;
import static org.derive4j.processor.Utils.uncapitalize;
import static org.derive4j.processor.Utils.zip;
import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.api.DerivedCodeSpec.methodSpec;
import static org.derive4j.processor.api.model.DataConstructions.caseOf;

final class CataDerivator implements Derivator {

  CataDerivator(DeriveUtils utils) {

    this.utils = utils;
    mapperDerivator = new MapperDerivator(utils);
  }

  private final DeriveUtils     utils;
  private final MapperDerivator mapperDerivator;

  @Override
  public DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt) {

    return visitorIsObjectAlgebra(adt)
        ? caseOf(adt.dataConstruction())
            .multipleConstructors(MultipleConstructorsSupport.cases()
                .visitorDispatch((visitorParam, visitorType, constructors) -> visitorDispatchImpl(adt, visitorType,
                    constructors))
                .functionsDispatch(dataConstructors -> functionDispatchImpl(adt, dataConstructors)))
            .oneConstructor(
                dataConstructor -> functionDispatchImpl(adt, Collections.singletonList(dataConstructor)))
            .noConstructor(() -> result(DerivedCodeSpec.none()))
        : result(DerivedCodeSpec.none());
  }

  boolean visitorIsObjectAlgebra(AlgebraicDataType adt) {
    List<VariableElement> selfReferenceParams = adt
        .dataConstruction()
        .constructors()
        .stream()
        .map(DataConstructors::getDeconstructor)
        .flatMap(dd -> Utils
            .<VariableElement, TypeMirror>zip(dd.method().getParameters(),
                dd.methodType().getParameterTypes())
            .stream()
            .filter(para -> utils.types().isSameType(para._2(), adt.typeConstructor().declaredType()))
            .map(P2::_1))
        .collect(toList());

    return !selfReferenceParams.isEmpty()
        && selfReferenceParams.stream().allMatch(p -> Utils.asTypeVariable.visit(p.asType()).isPresent());
  }

  private TypeName cataMapperTypeName(AlgebraicDataType adt, DataConstructor dc) {
    return mapperDerivator.mapperTypeName(adt, dc, adt.matchMethod().returnTypeVariable(),
        TypeName.get(adt.matchMethod().returnTypeVariable()));
  }

  private DeriveResult<DerivedCodeSpec> functionDispatchImpl(AlgebraicDataType adt,
      List<DataConstructor> constructors) {

    NameAllocator nameAllocator = nameAllocator(adt, constructors);
    nameAllocator.newName("delay", "delay");

    SamInterface f = utils.function1Model(adt.deriveConfig().flavour());
    DeclaredType fDT = utils.types().getDeclaredType(f.samClass(), adt.typeConstructor().declaredType(),
        adt.matchMethod().returnTypeVariable());
    TypeName returnType = TypeName.get(fDT);
    ExecutableElement abstractMethod = utils.allAbstractMethods(fDT).get(0);

    TypeSpec wrapper = TypeSpec.anonymousClassBuilder("")
        .addSuperinterface(returnType)
        .addMethod(
            MethodSpec.methodBuilder(abstractMethod.getSimpleName().toString())
                .addAnnotation(Override.class)
                .addModifiers(
                    abstractMethod.getModifiers().stream().filter(m -> m != Modifier.ABSTRACT).collect(toList()))
                .returns(TypeName.get(adt.matchMethod().returnTypeVariable()))
                .addParameter(TypeName.get(adt.typeConstructor().declaredType()), nameAllocator.get("adt var"))
                .addStatement("return $L.$L(() -> $L.$L($L))", nameAllocator.get("delay"), f.sam().getSimpleName(),
                    nameAllocator.get("adt var"),
                    adt.matchMethod().element().getSimpleName(),
                    Utils
                        .joinStringsAsArguments(
                            constructors.stream()
                                .map(
                                    constructor -> constructor.arguments()
                                        .stream()
                                        .map(DataArguments::getType)
                                        .noneMatch(
                                            tm -> utils.types().isSameType(tm, adt.typeConstructor().declaredType()))
                                                ? ('\n' + constructor.name())
                                                : CodeBlock.builder()
                                                    .add("\n($L) -> $L.$L($L)",
                                                        Utils
                                                            .joinStringsAsArguments(concat(
                                                                constructor.arguments()
                                                                    .stream()
                                                                    .map(DataArgument::fieldName)
                                                                    .map(fn -> nameAllocator.clone().newName(fn,
                                                                        fn + " field")),
                                                                constructor
                                                                    .typeRestrictions()
                                                                    .stream()
                                                                    .map(TypeRestriction::typeEq)
                                                                    .map(DataArgument::fieldName)
                                                                    .map(fn -> nameAllocator
                                                                        .clone()
                                                                        .newName(fn, fn + " field")))),
                                                        constructor.name(),
                                                        mapperDerivator.mapperApplyMethod(adt
                                                            .deriveConfig(), constructor),
                                                        Utils.joinStringsAsArguments(concat(
                                                            constructor.arguments().stream().map(
                                                                argument -> utils.types().isSameType(argument.type(),
                                                                    adt.typeConstructor().declaredType())
                                                                        ? ("this." + f.sam().getSimpleName() + '('
                                                                            + nameAllocator.clone().newName(
                                                                                argument.fieldName(),
                                                                                argument.fieldName() + " field")
                                                                            + ')')
                                                                        : argument.fieldName()),
                                                            constructor.typeRestrictions()
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
            concat(adt.typeConstructor().typeVariables().stream(), Stream.of(adt.matchMethod().returnTypeVariable()))
                .map(TypeVariableName::get)
                .collect(toList()))
        .returns(returnType)
        .addParameters(constructors.stream()
            .map(dc -> ParameterSpec.builder(cataMapperTypeName(adt, dc), MapperDerivator.mapperFieldName(dc)).build())
            .collect(toList()))
        .addParameter(ParameterSpec.builder(TypeName.get(delayType(adt)), nameAllocator.get("delay")).build())
        .addStatement("return $L", wrapper)
        .build();

    return result(methodSpec(cataMethod));
  }

  private DeclaredType strictCataType(AlgebraicDataType adt, DeclaredType acceptedVisitorType) {
    return utils.types().getDeclaredType(utils.asTypeElement(acceptedVisitorType).get(),
        acceptedVisitorType.getTypeArguments()
            .stream()
            .map(tm -> utils.types().isSameType(adt.typeConstructor().declaredType(), tm)
                ? adt.matchMethod().returnTypeVariable()
                : tm)
            .toArray(TypeMirror[]::new));
  }

  private DeclaredType delayType(AlgebraicDataType adt) {
    return utils.types().getDeclaredType(utils.function1Model(adt.deriveConfig().flavour()).samClass(),
        utils.types().getDeclaredType(utils.function0Model(adt.deriveConfig().flavour()).samClass(),
            adt.matchMethod().returnTypeVariable()),
        adt.matchMethod().returnTypeVariable());
  }

  private DerivedCodeSpec cataVisitor(AlgebraicDataType adt, DeclaredType visitorType,
      List<DataConstructor> constructors) {

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(uncapitalize(visitorType.asElement().getSimpleName().toString()), "strictCata");
    nameAllocator.newName("lazy" + visitorType.asElement().getSimpleName().toString(), "lazyCata");
    nameAllocator.newName("delay", "delay");
    nameAllocator.newName(Utils.uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName()),
        "adt var");

    TypeElement visitorTypeElement = utils.asTypeElement(visitorType).get();
    String cataVisitorClassName = "Cata" + visitorTypeElement.getSimpleName().toString();
    TypeName strictCata = TypeName.get(strictCataType(adt, visitorType));
    TypeName delay = TypeName.get(delayType(adt));
    SamInterface f1 = utils.function1Model(adt.deriveConfig().flavour());

    final TypeSpec.Builder cataVisitorBuilder = TypeSpec.classBuilder(cataVisitorClassName)
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(toList()))
        .addTypeVariable(TypeVariableName.get(adt.matchMethod().returnTypeVariable()))
        .addSuperinterface(TypeName.get(visitorType))
        .addField(FieldSpec.builder(strictCata, nameAllocator.get("strictCata"))
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build())
        .addField(FieldSpec.builder(delay, nameAllocator.get("delay"))
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build())
        .addMethods(constructors.stream()
            .map(DataConstructor::deconstructor)
            .map(dc -> utils.overrideMethodBuilder(dc.method(), visitorType)
                .addStatement("return this.$L.$L($L)", nameAllocator.get("strictCata"), dc.method().getSimpleName(),
                    zip(dc.method().getParameters(), dc.methodType().getParameterTypes())
                        .stream()
                        .map(p -> utils.types().isSameType(adt.typeConstructor().declaredType(), p._2())
                            ? CodeBlock.of("this.$L.$L(() -> $L.$L(this))", nameAllocator.get("delay"),
                                f1.sam().getSimpleName(),
                                p._1().getSimpleName(), adt.matchMethod().element().getSimpleName())
                            : CodeBlock.of(p._1().getSimpleName().toString()))
                        .collect(CodeBlock.joining(", ")))
                .build())
            .collect(toList()));

    final MethodSpec cataVisitorConstructor = MethodSpec.constructorBuilder()
        .addParameter(ParameterSpec.builder(strictCata, nameAllocator.get("strictCata")).build())
        .addParameter(ParameterSpec.builder(delay, nameAllocator.get("delay")).build())
        .addStatement("this.$1N = $1N", nameAllocator.get("strictCata"))
        .addStatement("this.$1N = $1N", nameAllocator.get("delay"))
        .build();

    TypeSpec cataVisitor = cataVisitorBuilder.addMethod(cataVisitorConstructor).build();

    final MethodSpec cataVisitorFactory = MethodSpec.methodBuilder("cata")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(
            adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(toList()))
        .addTypeVariable(TypeVariableName.get(adt.matchMethod().returnTypeVariable()))
        .addParameter(ParameterSpec.builder(strictCata, nameAllocator.get("strictCata")).build())
        .addParameter(ParameterSpec.builder(delay, nameAllocator.get("delay")).build())
        .returns(TypeName.get(utils.types().getDeclaredType(f1.samClass(), adt.typeConstructor().declaredType(),
            adt.matchMethod().returnTypeVariable())))
        .addStatement("$T $L = new $L<>($L, $L)", TypeName.get(visitorType), nameAllocator.get("lazyCata"),
            cataVisitorClassName, nameAllocator.get("strictCata"), nameAllocator.get("delay"))
        .addStatement("return $1L -> $2L.$3L(() -> $1L.$4L($5L))",
            nameAllocator.get("adt var"),
            nameAllocator.get("delay"),
            f1.sam().getSimpleName(), adt.matchMethod().element().getSimpleName(),
            nameAllocator.get("lazyCata"))
        .build();

    return DerivedCodeSpec.codeSpec(cataVisitor, cataVisitorFactory);

  }

  private DeriveResult<DerivedCodeSpec> visitorDispatchImpl(AlgebraicDataType adt, DeclaredType visitorType,
      List<DataConstructor> constructors) {

    NameAllocator nameAllocator = nameAllocator(adt, constructors);

    MethodSpec cataMethod = MethodSpec.methodBuilder("cata")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(toList()))
        .addTypeVariable(TypeVariableName.get(adt.matchMethod().returnTypeVariable()))
        .returns(
            TypeName.get(utils.types().getDeclaredType(utils.function1Model(adt.deriveConfig().flavour()).samClass(),
                adt.typeConstructor().declaredType(), adt.matchMethod().returnTypeVariable())))
        .addParameters(constructors.stream()
            .map(dc -> ParameterSpec.builder(cataMapperTypeName(adt, dc), MapperDerivator.mapperFieldName(dc)).build())
            .collect(toList()))
        .addParameter(
            ParameterSpec.builder(TypeName.get(delayType(adt)), nameAllocator.newName("delay", "delay")).build())
        .addStatement("return cata(new $L<>($L), $L)", lambdaVisitorClassName(visitorType),
            constructors.stream().map(MapperDerivator::mapperFieldName).collect(
                Collectors.joining(", ")),
            nameAllocator.get("delay"))
        .build();

    return result(methodSpec(cataMethod).append(cataVisitor(adt, visitorType, constructors)));
  }

  private static NameAllocator nameAllocator(AlgebraicDataType adt, List<DataConstructor> constructors) {

    NameAllocator nameAllocator = new NameAllocator();
    constructors.forEach(
        dc -> nameAllocator.newName(MapperDerivator.mapperFieldName(dc), MapperDerivator.mapperFieldName(dc) + " arg"));
    nameAllocator.newName("cata", "cata");
    nameAllocator.newName(Utils.uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName()),
        "adt var");
    return nameAllocator;
  }
}
