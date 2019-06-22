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
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.EitherModel;
import org.derive4j.processor.api.OptionModel;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataArgument;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;
import org.derive4j.processor.api.model.TypeRestriction;

import static org.derive4j.processor.Utils.joinStringsAsArguments;
import static org.derive4j.processor.Utils.uncapitalize;
import static org.derive4j.processor.api.model.DataConstructions.caseOf;

class OtherwiseMatchingStepDerivator {

  OtherwiseMatchingStepDerivator(DeriveUtils deriveUtils, PatternMatchingDerivator.MatchingKind matchingKind) {
    this.deriveUtils = deriveUtils;
    this.mapperDerivator = new MapperDerivator(deriveUtils);
    this.matchingKind = matchingKind;
  }

  private final DeriveUtils                           deriveUtils;
  private final MapperDerivator                       mapperDerivator;
  private final PatternMatchingDerivator.MatchingKind matchingKind;

  TypeSpec stepTypeSpec(AlgebraicDataType adt) {

    ParameterSpec adtParamSpec = PatternMatchingDerivator.asParameterSpec(adt);
    FieldSpec adtFieldSpec = PatternMatchingDerivator.asFieldSpec(adt);

    TypeSpec.Builder otherwiseMatchBuilder = TypeSpec.classBuilder(otherwiseBuilderClassName())
        .addTypeVariables(
            PatternMatchingDerivator.matcherVariables(adt).map(TypeVariableName::get).collect(Collectors.toList()))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

    MethodSpec.Builder otherwiseMatchConstructorBuilder = MethodSpec.constructorBuilder();

    if (matchingKind == PatternMatchingDerivator.MatchingKind.CaseOf) {
      otherwiseMatchBuilder.addField(adtFieldSpec);
      otherwiseMatchConstructorBuilder.addParameter(adtParamSpec).addStatement("this.$N = $N", adtFieldSpec,
          adtParamSpec);
    }

    otherwiseMatchConstructorBuilder.addParameters(adt.dataConstruction()
        .constructors()
        .stream()
        .map(dc -> ParameterSpec.builder(mapperDerivator.mapperTypeName(adt, dc), MapperDerivator.mapperFieldName(dc))
            .build())
        .collect(Collectors.toList()));

    otherwiseMatchBuilder.addFields(adt.dataConstruction()
        .constructors()
        .stream()
        .map(dc -> FieldSpec.builder(mapperDerivator.mapperTypeName(adt, dc), MapperDerivator.mapperFieldName(dc))
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build())
        .collect(Collectors.toList()));

    for (DataConstructor dc : adt.dataConstruction().constructors()) {
      otherwiseMatchConstructorBuilder.addStatement("this.$L = $L", MapperDerivator.mapperFieldName(dc),
          MapperDerivator.mapperFieldName(dc));
    }

    return otherwiseMatchBuilder.addMethod(otherwiseMatchConstructorBuilder.build())
        .addMethods(otherwiseMethods(adt))
        .addMethod(otherwiseNoneMethod(adt))
        .addMethods(deriveUtils.eitherModel(adt.deriveConfig().flavour())
            .map(eitherModel -> otherwiseLeftMethod(adt, eitherModel))
            .orElse(Collections.emptyList()))
        .build();

  }

  private List<MethodSpec> otherwiseLeftMethod(AlgebraicDataType adt, EitherModel eitherModel) {

    NameAllocator typeVarAllocator = new NameAllocator();
    adt.typeConstructor().typeVariables().forEach(tv -> typeVarAllocator.newName(tv.toString(), tv.toString()));
    typeVarAllocator.newName(adt.matchMethod().returnTypeVariable().toString(), "match type var");

    TypeElement eitherTypeElement = eitherModel.typeElement();

    TypeVariableName leftTypeVarName = TypeVariableName
        .get(typeVarAllocator.newName(eitherTypeElement.getTypeParameters().get(0).toString(), "leftTypeVar"));

    TypeName eitherTypeName = deriveUtils.resolveToTypeName(eitherTypeElement.asType(),
        etv -> deriveUtils.types().isSameType(etv, eitherTypeElement.getTypeParameters().get(0).asType())
            ? Optional.of(leftTypeVarName)
            : deriveUtils.types().isSameType(etv, eitherTypeElement.getTypeParameters().get(1).asType())
                ? Optional.of(TypeVariableName.get(adt.matchMethod().returnTypeVariable()))
                : Optional.empty());

    TypeName returnType = (matchingKind == PatternMatchingDerivator.MatchingKind.Cases)
        ? ParameterizedTypeName.get(ClassName.get(deriveUtils.function1Model(adt.deriveConfig().flavour()).samClass()),
            TypeName.get(adt.typeConstructor().declaredType()), eitherTypeName)
        : eitherTypeName;

    TypeElement f0 = deriveUtils.function0Model(adt.deriveConfig().flavour()).samClass();

    String otherwiseLeftMethodName = "otherwise"
        + Utils.capitalize(eitherModel.leftConstructor().getSimpleName().toString());
    String arg = uncapitalize(eitherModel.leftConstructor().getSimpleName().toString());

    return Arrays.asList(MethodSpec.methodBuilder(otherwiseLeftMethodName)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addTypeVariable(leftTypeVarName)
        .addParameter(ParameterizedTypeName.get(ClassName.get(f0), leftTypeVarName), arg)
        .returns(returnType)
        .addCode(caseOf(adt.dataConstruction()).multipleConstructors(MultipleConstructorsSupport.cases()
            .visitorDispatch((visitorParam, visitorType, constructors) -> visitorDispatchEitherImpl(f0, eitherModel,
                eitherTypeName, adt, visitorType, visitorParam, arg))
            .functionsDispatch(
                constructors -> functionsDispatchEitherImpl(f0, eitherModel, eitherTypeName, adt, constructors, arg)))
            .otherwise(() -> {
              throw new IllegalArgumentException();
            }))
        .build(),
        MethodSpec.methodBuilder(otherwiseLeftMethodName + '_')
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addTypeVariable(leftTypeVarName)
            .addParameter(leftTypeVarName, arg)
            .addStatement("return this.$L(() -> $L)", otherwiseLeftMethodName, arg)
            .returns(returnType)
            .build());
  }

  private CodeBlock functionsDispatchEitherImpl(TypeElement f0, EitherModel eitherModel, TypeName eitherTypeName,
      AlgebraicDataType adt, List<DataConstructor> constructors, String argName) {

    CodeBlock.Builder codeBlock = CodeBlock.builder();
    TypeElement eitherTypeElement = eitherModel.typeElement();
    NameAllocator rootNameAllocator = new NameAllocator();
    rootNameAllocator.newName("left", argName);
    for (DataConstructor dc : constructors) {
      NameAllocator nameAllocator = rootNameAllocator.clone();
      nameAllocator.newName(MapperDerivator.mapperFieldName(dc), "case var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName() + "Field"));

      String lambdaArgs = joinStringsAsArguments(
          IntStream.range(9, 9 + dc.arguments().size() + dc.typeRestrictions().size()).mapToObj(i -> "$" + i + 'L'));

      codeBlock.addStatement(
          "$1T $2L = (this.$3L != null) ? (" + lambdaArgs + ") -> $4T.$5L(this.$3L.$6L(" + lambdaArgs + "))\n" + ": ("
              + lambdaArgs + ") -> $4T.$7L(left.$8L())",

          Stream.concat(
              Stream.of(mapperDerivator.mapperTypeName(adt, dc, eitherTypeName), nameAllocator.get("case var"),
                  MapperDerivator.mapperFieldName(dc), ClassName.get(eitherTypeElement),
                  eitherModel.rightConstructor().getSimpleName(),
                  deriveUtils.allAbstractMethods(dc.deconstructor().visitorType()).get(0).getSimpleName().toString(),
                  eitherModel.leftConstructor().getSimpleName(),
                  deriveUtils.allAbstractMethods(f0).get(0).getSimpleName().toString()),
              Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
                  .map(da -> nameAllocator.get(da.fieldName() + "Field")))
              .toArray(Object[]::new));
    }

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    String template;
    Object templateArg;
    if (matchingKind == PatternMatchingDerivator.MatchingKind.Cases) {
      template = "$1L -> $1L";
      templateArg = adtLambdaParam;
    } else {
      template = "this.$1N";
      templateArg = PatternMatchingDerivator.asFieldSpec(adt);
    }
    return codeBlock
        .addStatement("return " + template + ".$2L($3L)", templateArg, adt.matchMethod().element().getSimpleName(),
            joinStringsAsArguments(
                constructors.stream().map(dc -> rootNameAllocator.newName(MapperDerivator.mapperFieldName(dc)))))
        .build();

  }

  private CodeBlock visitorDispatchEitherImpl(TypeElement f0, EitherModel eitherModel, TypeName eitherTypeName,
      AlgebraicDataType adt, DeclaredType visitorType, VariableElement visitorParam, String argName) {

    TypeElement eitherTypeElement = eitherModel.typeElement();
    String visitorVarName = visitorParam.getSimpleName().toString();
    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    CodeBlock lambdaArgs = adt.dataConstruction().constructors().stream().map(dc -> {
      NameAllocator nameAllocator = new NameAllocator();
      nameAllocator.newName(argName, "left arg");
      nameAllocator.newName(adtLambdaParam, "adt var");
      nameAllocator.newName(visitorVarName, "visitor var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

      String lambdaArg = joinStringsAsArguments(
          IntStream.range(7, 7 + dc.arguments().size() + dc.typeRestrictions().size()).mapToObj(i -> "$" + i + 'L'));

      return CodeBlock.builder()
          .add(
              "(this.$1L != null) ? (" + lambdaArg + ") -> $2T.$3L(this.$1L.$4L(" + lambdaArg + "))\n" + ": ("
                  + lambdaArg + ") -> $2T.$5L(left.$6L())",
              Stream.concat(
                  Stream.of(MapperDerivator.mapperFieldName(dc), ClassName.get(eitherTypeElement),
                      eitherModel.rightConstructor().getSimpleName(),
                      mapperDerivator.mapperApplyMethod(adt.deriveConfig(), dc),
                      eitherModel.leftConstructor().getSimpleName(),
                      deriveUtils.allAbstractMethods(f0).get(0).getSimpleName().toString()),
                  Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
                      .map(DataArgument::fieldName)
                      .map(nameAllocator::get))
                  .toArray(Object[]::new))
          .build();
    }).reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build()).orElse(
        CodeBlock.builder().build());

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(adtLambdaParam, "adt var");
    nameAllocator.newName(visitorVarName, "visitor var");

    CodeBlock.Builder implBuilder = CodeBlock.builder().addStatement("$T $L = $T.$L($L)",
        deriveUtils.resolveToTypeName(visitorType,
            tv -> deriveUtils.types().isSameType(tv, adt.matchMethod().returnTypeVariable())
                ? Optional.of(eitherTypeName)
                : Optional.empty()),
        nameAllocator.get("visitor var"), adt.deriveConfig().targetClass().className(),
        MapperDerivator.visitorLambdaFactoryName(adt), lambdaArgs);

    if (matchingKind == PatternMatchingDerivator.MatchingKind.Cases) {
      implBuilder.addStatement("return $1L -> $1L.$2L($3L)", nameAllocator.get("adt var"),
          adt.matchMethod().element().getSimpleName(), nameAllocator.get("visitor var"));
    } else {
      implBuilder.addStatement("return this.$1N.$2L($3L)", PatternMatchingDerivator.asFieldSpec(adt),
          adt.matchMethod().element().getSimpleName(), nameAllocator.get("visitor var"));
    }
    return implBuilder.build();
  }

  private CodeBlock functionsDispatchOptionImpl(OptionModel optionModel, AlgebraicDataType adt,
      List<DataConstructor> constructors) {

    CodeBlock.Builder codeBlock = CodeBlock.builder();

    for (DataConstructor dc : constructors) {
      NameAllocator nameAllocator = new NameAllocator();
      nameAllocator.newName(MapperDerivator.mapperFieldName(dc), "case var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

      String lambdaArgs = joinStringsAsArguments(
          IntStream.range(8, 8 + dc.arguments().size() + dc.typeRestrictions().size()).mapToObj(i -> "$" + i + 'L'));

      codeBlock.addStatement(
          "$1T $2L = (this.$3L != null) ? (" + lambdaArgs + ") -> $4T.$5L(this.$3L.$6L(" + lambdaArgs + "))\n" + ": ("
              + lambdaArgs + ") -> $4T.$7L()",

          Stream.concat(
              Stream.of(
                  mapperDerivator.mapperTypeName(adt, dc,
                      TypeName.get(deriveUtils.types().getDeclaredType(optionModel.typeElement(),
                          adt.matchMethod().returnTypeVariable()))),
                  nameAllocator.get("case var"), MapperDerivator.mapperFieldName(dc),
                  ClassName.get(optionModel.typeElement()), optionModel.someConstructor().getSimpleName(),
                  deriveUtils.allAbstractMethods(dc.deconstructor().visitorType()).get(0).getSimpleName().toString(),
                  optionModel.noneConstructor().getSimpleName()),
              Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
                  .map(DataArgument::fieldName)
                  .map(nameAllocator::get))
              .toArray(Object[]::new));
    }

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    String template;
    Object templateArg;
    if (matchingKind == PatternMatchingDerivator.MatchingKind.Cases) {
      template = "$1L -> $1L";
      templateArg = adtLambdaParam;
    } else {
      template = "this.$1N";
      templateArg = PatternMatchingDerivator.asFieldSpec(adt);
    }
    return codeBlock
        .addStatement("return " + template + ".$2L($3L)", templateArg, adt.matchMethod().element().getSimpleName(),
            joinStringsAsArguments(constructors.stream().map(MapperDerivator::mapperFieldName)))
        .build();

  }

  private CodeBlock visitorDispatchOptionImpl(OptionModel optionModel, AlgebraicDataType adt, DeclaredType visitorType,
      VariableElement visitorParam) {

    String visitorVarName = visitorParam.getSimpleName().toString();
    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    CodeBlock lambdaArgs = adt.dataConstruction().constructors().stream().map(dc -> {
      NameAllocator nameAllocator = new NameAllocator();
      nameAllocator.newName(adtLambdaParam, "adt var");
      nameAllocator.newName(visitorVarName, "visitor var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

      String lambdaArg = joinStringsAsArguments(
          IntStream.range(6, 6 + dc.arguments().size() + dc.typeRestrictions().size()).mapToObj(i -> "$" + i + 'L'));

      return CodeBlock.builder()
          .add(
              "(this.$1L != null) ? (" + lambdaArg + ") -> $2T.$3L(this.$1L.$4L(" + lambdaArg + "))\n" + ": ("
                  + lambdaArg + ") -> $2T.$5L()",
              Stream.<Object>concat(
                  Stream.of(MapperDerivator.mapperFieldName(dc), ClassName.get(optionModel.typeElement()),
                      optionModel.someConstructor().getSimpleName(),
                      mapperDerivator.mapperApplyMethod(adt.deriveConfig(), dc),
                      optionModel.noneConstructor().getSimpleName()),
                  Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
                      .map(DataArgument::fieldName)
                      .map(nameAllocator::get))
                  .toArray(Object[]::new))
          .build();
    }).reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build()).orElse(
        CodeBlock.builder().build());

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(adtLambdaParam, "adt var");
    nameAllocator.newName(visitorVarName, "visitor var");

    CodeBlock.Builder implBuilder = CodeBlock.builder().addStatement("$T $L = $T.$L($L)",
        TypeName.get(deriveUtils.resolve(visitorType,
            tv -> deriveUtils.types().isSameType(tv, adt.matchMethod().returnTypeVariable())
                ? Optional.of(deriveUtils.types().getDeclaredType(optionModel.typeElement(),
                    adt.matchMethod().returnTypeVariable()))
                : Optional.empty())),
        nameAllocator.get("visitor var"), adt.deriveConfig().targetClass().className(),
        MapperDerivator.visitorLambdaFactoryName(adt), lambdaArgs);

    if (matchingKind == PatternMatchingDerivator.MatchingKind.Cases) {
      implBuilder.addStatement("return $1L -> $1L.$2L($3L)", nameAllocator.get("adt var"),
          adt.matchMethod().element().getSimpleName(), nameAllocator.get("visitor var"));
    } else {
      implBuilder.addStatement("return this.$1N.$2L($3L)", PatternMatchingDerivator.asFieldSpec(adt),
          adt.matchMethod().element().getSimpleName(), nameAllocator.get("visitor var"));
    }
    return implBuilder.build();

  }

  private List<MethodSpec> otherwiseMethods(AlgebraicDataType adt) {

    TypeElement f0 = deriveUtils.function0Model(adt.deriveConfig().flavour()).samClass();

    TypeName returnType = (matchingKind == PatternMatchingDerivator.MatchingKind.Cases)
        ? TypeName.get(
            deriveUtils.types().getDeclaredType(deriveUtils.function1Model(adt.deriveConfig().flavour()).samClass(),
                adt.typeConstructor().declaredType(), adt.matchMethod().returnTypeVariable()))
        : TypeName.get(adt.matchMethod().returnTypeVariable());

    return Arrays
        .asList(MethodSpec.methodBuilder("otherwise")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(
                ParameterSpec
                    .builder(
                        TypeName.get(deriveUtils.types().getDeclaredType(f0, adt.matchMethod().returnTypeVariable())),
                        "otherwise")
                    .build())
            .returns(returnType)
            .addCode(
                caseOf(adt.dataConstruction())
                    .multipleConstructors(MultipleConstructorsSupport.cases()
                        .visitorDispatch((visitorParam, visitorType, constructors) -> visitorDispatchImpl(f0, adt,
                            visitorType, visitorParam))
                        .functionsDispatch(constructors -> functionsDispatchImpl(f0, adt, constructors)))
                    .otherwise(() -> {
                      throw new IllegalArgumentException();
                    }))
            .build(),
            MethodSpec.methodBuilder("otherwise_")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(TypeName.get(adt.matchMethod().returnTypeVariable()),
                    uncapitalize(adt.matchMethod().returnTypeVariable().toString()))
                .addStatement("return this.$L(() -> $L)", "otherwise",
                    uncapitalize(adt.matchMethod().returnTypeVariable().toString()))
                .returns(returnType)
                .build());
  }

  private MethodSpec otherwiseNoneMethod(AlgebraicDataType adt) {

    OptionModel optionModel = deriveUtils.optionModel(adt.deriveConfig().flavour());

    DeclaredType optionType = deriveUtils.types().getDeclaredType(optionModel.typeElement(),
        adt.matchMethod().returnTypeVariable());

    TypeName returnType = (matchingKind == PatternMatchingDerivator.MatchingKind.Cases)
        ? TypeName.get(
            deriveUtils.types().getDeclaredType(deriveUtils.function1Model(adt.deriveConfig().flavour()).samClass(),
                adt.typeConstructor().declaredType(), optionType))
        : TypeName.get(optionType);

    return MethodSpec
        .methodBuilder("otherwise" + Utils.capitalize(optionModel.noneConstructor().getSimpleName().toString()))
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .returns(returnType)
        .addCode(caseOf(adt.dataConstruction())
            .multipleConstructors(MultipleConstructorsSupport.cases()
                .visitorDispatch((visitorParam, visitorType, constructors) -> visitorDispatchOptionImpl(optionModel,
                    adt, visitorType, visitorParam))
                .functionsDispatch(constructors -> functionsDispatchOptionImpl(optionModel, adt, constructors)))
            .otherwise(() -> {
              throw new IllegalArgumentException();
            }))
        .build();
  }

  private CodeBlock functionsDispatchImpl(TypeElement f0, AlgebraicDataType adt, List<DataConstructor> constructors) {

    CodeBlock.Builder codeBlock = CodeBlock.builder();

    for (DataConstructor dc : constructors) {
      NameAllocator nameAllocator = new NameAllocator();
      nameAllocator.newName("otherwise", "otherwise arg");
      nameAllocator.newName(MapperDerivator.mapperFieldName(dc), "case var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

      codeBlock.addStatement(
          "$1T $2L = (this.$3L != null) ? this.$3L : (" + joinStringsAsArguments(
              IntStream.range(5, 5 + dc.arguments().size() + dc.typeRestrictions().size()).mapToObj(i -> "$" + i + 'L'))
              + ") -> otherwise.$4L()",
          Stream.concat(
              Stream.of(mapperDerivator.mapperTypeName(adt, dc), nameAllocator.get("case var"),
                  MapperDerivator.mapperFieldName(dc),
                  deriveUtils.allAbstractMethods(f0).get(0).getSimpleName().toString()),
              Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
                  .map(DataArgument::fieldName)
                  .map(nameAllocator::get))
              .toArray(Object[]::new));
    }

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    String template;
    Object templateArg;
    if (matchingKind == PatternMatchingDerivator.MatchingKind.Cases) {
      template = "$1L -> $1L";
      templateArg = adtLambdaParam;
    } else {
      template = "this.$1N";
      templateArg = PatternMatchingDerivator.asFieldSpec(adt);
    }
    return codeBlock
        .addStatement("return " + template + ".$2L($3L)", templateArg, adt.matchMethod().element().getSimpleName(),
            joinStringsAsArguments(constructors.stream().map(MapperDerivator::mapperFieldName)))
        .build();
  }

  private CodeBlock visitorDispatchImpl(TypeElement f0, AlgebraicDataType adt, DeclaredType visitorType,
      VariableElement visitorParam) {

    String visitorVarName = visitorParam.getSimpleName().toString();
    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    CodeBlock lambdaArgs = adt.dataConstruction().constructors().stream().map(dc -> {
      NameAllocator nameAllocator = new NameAllocator();
      nameAllocator.newName("otherwise", "otherwise arg");
      nameAllocator.newName(adtLambdaParam, "adt var");
      nameAllocator.newName(visitorVarName, "visitor var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

      return CodeBlock.builder()
          .add(
              "this.$1L != null ? this.$1L : ("
                  + joinStringsAsArguments(IntStream.range(3, 3 + dc.arguments().size() + dc.typeRestrictions().size())
                      .mapToObj(i -> "$" + i + 'L'))
                  + ") -> otherwise.$2L()",
              (Object[]) Stream.concat(
                  Stream.of(MapperDerivator.mapperFieldName(dc),
                      deriveUtils.allAbstractMethods(f0).get(0).getSimpleName().toString()),
                  Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::typeEq))
                      .map(DataArgument::fieldName)
                      .map(nameAllocator::get))
                  .toArray(String[]::new))
          .build();
    }).reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build()).orElse(
        CodeBlock.builder().build());

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName("otherwise", "otherwise arg");
    nameAllocator.newName(adtLambdaParam, "adt var");
    nameAllocator.newName(visitorVarName, "visitor var");

    String typeVarArgs = Stream
        .concat(adt.typeConstructor().typeVariables().stream(), Stream.of(adt.matchMethod().returnTypeVariable()))
        .map(TypeVariable::toString)
        .collect(Collectors.joining(", "));

    CodeBlock.Builder implBuilder = CodeBlock.builder().addStatement("$T $L = $T.<$L>$L($L)", TypeName.get(visitorType),
        nameAllocator.get("visitor var"), adt.deriveConfig().targetClass().className(), typeVarArgs,
        MapperDerivator.visitorLambdaFactoryName(adt), lambdaArgs);
    if (matchingKind == PatternMatchingDerivator.MatchingKind.Cases) {
      implBuilder.addStatement("return $1L -> $1L.$2L($3L)", nameAllocator.get("adt var"),
          adt.matchMethod().element().getSimpleName(), nameAllocator.get("visitor var"));
    } else {
      implBuilder.addStatement("return this.$1N.$2L($3L)", PatternMatchingDerivator.asFieldSpec(adt),
          adt.matchMethod().element().getSimpleName(), nameAllocator.get("visitor var"));
    }
    return implBuilder.build();
  }

  static ParameterizedTypeName otherwiseMatcherTypeName(AlgebraicDataType adt) {
    return ParameterizedTypeName.get(ClassName.bestGuess(otherwiseBuilderClassName()),
        PatternMatchingDerivator.matcherVariables(adt).map(TypeVariableName::get).toArray(TypeName[]::new));
  }

  static String otherwiseBuilderClassName() {
    return "PartialMatcher";
  }

}
