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
package org.derive4j.processor.derivator.patternmatching;

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
import org.derive4j.processor.Utils;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataArgument;
import org.derive4j.processor.api.model.DataConstructions;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.DeriveContext;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;
import org.derive4j.processor.api.model.TypeRestriction;
import org.derive4j.processor.derivator.FlavourImpl;
import org.derive4j.processor.derivator.MapperDerivator;

import static org.derive4j.processor.Utils.joinStringsAsArguments;
import static org.derive4j.processor.Utils.uncapitalize;
import static org.derive4j.processor.derivator.EitherTypes.getLeftConstructor;
import static org.derive4j.processor.derivator.EitherTypes.getRightConstructor;
import static org.derive4j.processor.derivator.EitherTypes.getTypeElement;
import static org.derive4j.processor.derivator.MapperDerivator.mapperFieldName;
import static org.derive4j.processor.derivator.MapperDerivator.mapperTypeName;

public class OtherwiseMatchingStepDerivator {

  static TypeSpec otherwiseMatchingStepTypeSpec(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {

    TypeSpec.Builder otherwiseMatchBuilder = TypeSpec.classBuilder(otherwiseBuilderClassName())
        .addTypeVariables(PatternMatchingDerivator.matcherVariables(adt).map(TypeVariableName::get).collect(Collectors.toList()))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addFields(adt.dataConstruction()
            .constructors()
            .stream()
            .map(dc -> FieldSpec.builder(mapperTypeName(adt, dc, deriveContext, deriveUtils), mapperFieldName(dc))
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build())
            .collect(Collectors.toList()));

    MethodSpec.Builder otherwiseMatchConstructorBuilder = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PRIVATE)
        .addParameters(adt.dataConstruction()
            .constructors()
            .stream()
            .map(dc -> ParameterSpec.builder(mapperTypeName(adt, dc, deriveContext, deriveUtils), mapperFieldName(dc)).build())
            .collect(Collectors.toList()));

    for (DataConstructor dc : adt.dataConstruction().constructors()) {
      otherwiseMatchConstructorBuilder.addStatement("this.$L = $L", mapperFieldName(dc), mapperFieldName(dc));
    }

    return otherwiseMatchBuilder.addMethod(otherwiseMatchConstructorBuilder.build())
        .addMethods(otherwiseMethods(adt, deriveContext, deriveUtils))
        .addMethod(otherwiseNoneMethod(adt, deriveContext, deriveUtils))
        .addMethods(FlavourImpl.findEitherType(deriveContext.flavour(), deriveUtils.elements())
            .map(eitherType -> otherwiseLeftMethod(adt, deriveContext, deriveUtils, eitherType))
            .orElse(Collections.emptyList()))
        .build();

  }

  private static List<MethodSpec> otherwiseMethods(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {

    TypeElement f0 = FlavourImpl.findF0(deriveContext.flavour(), deriveUtils.elements());

    TypeName returnType = TypeName.get(deriveUtils.types()
        .getDeclaredType(FlavourImpl.findF(deriveContext.flavour(), deriveUtils.elements()), adt.typeConstructor().declaredType(),
            adt.matchMethod().returnTypeVariable()));

    return Arrays.asList(MethodSpec.methodBuilder("otherwise")
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addParameter(
            ParameterSpec.builder(TypeName.get(deriveUtils.types().getDeclaredType(f0, adt.matchMethod().returnTypeVariable())), "otherwise").build())
        .returns(returnType)
        .addCode(DataConstructions.cases()
            .multipleConstructors(MultipleConstructorsSupport.cases()
                .visitorDispatch(
                    (visitorParam, visitorType, constructors) -> visitorDispatchImpl(deriveUtils, deriveContext, f0, adt, visitorType, visitorParam))
                .functionsDispatch(constructors -> functionsDispatchImpl(deriveUtils, deriveContext, f0, adt, constructors)))
            .otherwise(() -> {
              throw new IllegalArgumentException();
            })
            .apply(adt.dataConstruction()))
        .build(), MethodSpec.methodBuilder("otherwise")
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addParameter(TypeName.get(adt.matchMethod().returnTypeVariable()), uncapitalize(adt.matchMethod().returnTypeVariable().toString()))
        .addStatement("return this.$L(() -> $L)", "otherwise", uncapitalize(adt.matchMethod().returnTypeVariable().toString()))
        .returns(returnType)
        .build());
  }

  private static MethodSpec otherwiseNoneMethod(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {

    FlavourImpl.OptionType optionType = FlavourImpl.findOptionType(deriveContext.flavour(), deriveUtils.elements());

    TypeName returnType = TypeName.get(deriveUtils.types()
        .getDeclaredType(FlavourImpl.findF(deriveContext.flavour(), deriveUtils.elements()), adt.typeConstructor().declaredType(),
            deriveUtils.types().getDeclaredType(optionType.typeElement(), adt.matchMethod().returnTypeVariable())));

    return MethodSpec.methodBuilder("otherwise" + Utils.capitalize(optionType.noneConstructor()))
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .returns(returnType)
        .addCode(DataConstructions.cases()
            .multipleConstructors(MultipleConstructorsSupport.cases()
                .visitorDispatch(
                    (visitorParam, visitorType, constructors) -> visitorDispatchOptionImpl(deriveUtils, deriveContext, optionType, adt, visitorType,
                        visitorParam))
                .functionsDispatch(constructors -> functionsDispatchOptionImpl(deriveUtils, deriveContext, optionType, adt, constructors)))
            .otherwise(() -> {
              throw new IllegalArgumentException();
            })
            .apply(adt.dataConstruction()))
        .build();
  }

  private static CodeBlock functionsDispatchImpl(DeriveUtils deriveUtils, DeriveContext deriveContext, TypeElement f0, AlgebraicDataType adt,
      List<DataConstructor> constructors) {

    CodeBlock.Builder codeBlock = CodeBlock.builder();

    for (DataConstructor dc : constructors) {
      NameAllocator nameAllocator = new NameAllocator();
      nameAllocator.newName("otherwise", "otherwise arg");
      nameAllocator.newName(mapperFieldName(dc), "case var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

      codeBlock.addStatement("$1T $2L = (this.$3L != null) ? this.$3L : (" +
          joinStringsAsArguments(IntStream.range(5, 5 + dc.arguments().size() + dc.typeRestrictions().size()).mapToObj(i -> "$" + i + 'L')) +
          ") -> otherwise.$4L()", Stream.concat(
          Stream.of(mapperTypeName(adt, dc, deriveContext, deriveUtils), nameAllocator.get("case var"), mapperFieldName(dc),
              deriveUtils.allAbstractMethods(f0).get(0).getSimpleName().toString()),
          Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
              .map(DataArgument::fieldName)
              .map(nameAllocator::get)).toArray(Object[]::new));
    }

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    return codeBlock.addStatement("return $1L -> $1L.$2L($3L)", adtLambdaParam, adt.matchMethod().element().getSimpleName(),
        joinStringsAsArguments(constructors.stream().map(MapperDerivator::mapperFieldName))).build();
  }

  private static CodeBlock visitorDispatchImpl(DeriveUtils deriveUtils, DeriveContext deriveContext, TypeElement f0, AlgebraicDataType adt,
      DeclaredType visitorType, VariableElement visitorParam) {

    String visitorVarName = visitorParam.getSimpleName().toString();
    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    CodeBlock lambdaArgs = adt.dataConstruction().constructors().stream().map(dc -> {
      NameAllocator nameAllocator = new NameAllocator();
      nameAllocator.newName("otherwise", "otherwise arg");
      nameAllocator.newName(adtLambdaParam, "adt var");
      nameAllocator.newName(visitorVarName, "visitor var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

      return CodeBlock.builder()
          .add("this.$1L != null ? this.$1L : (" +
                  joinStringsAsArguments(IntStream.range(3, 3 + dc.arguments().size() + dc.typeRestrictions().size()).mapToObj(i -> "$" + i + 'L')) +
                  ") -> otherwise.$2L()",
              (Object[]) Stream.concat(Stream.of(mapperFieldName(dc), deriveUtils.allAbstractMethods(f0).get(0).getSimpleName().toString()),
                  Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
                      .map(DataArgument::fieldName)
                      .map(nameAllocator::get)).toArray(String[]::new))
          .build();
    }).reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build()).orElse(CodeBlock.builder().build());

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName("otherwise", "otherwise arg");
    nameAllocator.newName(adtLambdaParam, "adt var");
    nameAllocator.newName(visitorVarName, "visitor var");

    String typeVarArgs = Stream.concat(adt.typeConstructor().typeVariables().stream(), Stream.of(adt.matchMethod().returnTypeVariable()))
        .map(TypeVariable::toString)
        .collect(Collectors.joining(", "));

    return CodeBlock.builder()
        .addStatement("$T $L = $T.<$L>$L($L)", TypeName.get(visitorType), nameAllocator.get("visitor var"),
            ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName()), typeVarArgs, MapperDerivator.visitorLambdaFactoryName(adt),
            lambdaArgs)
        .addStatement("return $1L -> $1L.$2L($3L)", nameAllocator.get("adt var"), adt.matchMethod().element().getSimpleName(),
            nameAllocator.get("visitor var"))
        .build();
  }

  private static CodeBlock functionsDispatchOptionImpl(DeriveUtils deriveUtils, DeriveContext deriveContext, FlavourImpl.OptionType optionType,
      AlgebraicDataType adt, List<DataConstructor> constructors) {

    CodeBlock.Builder codeBlock = CodeBlock.builder();

    for (DataConstructor dc : constructors) {
      NameAllocator nameAllocator = new NameAllocator();
      nameAllocator.newName(mapperFieldName(dc), "case var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

      String lambdaArgs = joinStringsAsArguments(
          IntStream.range(8, 8 + dc.arguments().size() + dc.typeRestrictions().size()).mapToObj(i -> "$" + i + 'L'));

      codeBlock.addStatement("$1T $2L = (this.$3L != null) ? (" +
              lambdaArgs +
              ") -> $4T.$5L(this.$3L.$6L(" +
              lambdaArgs +
              "))\n" +
              ": (" +
              lambdaArgs +
              ") -> $4T.$7L()",

          Stream.concat(Stream.of(mapperTypeName(adt, dc, deriveContext, deriveUtils,
              TypeName.get(deriveUtils.types().getDeclaredType(optionType.typeElement(), adt.matchMethod().returnTypeVariable()))),
              nameAllocator.get("case var"), mapperFieldName(dc), ClassName.get(optionType.typeElement()), optionType.someConstructor(),
              deriveUtils.allAbstractMethods(dc.deconstructor().visitorType()).get(0).getSimpleName().toString(), optionType.noneConstructor()),
              Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
                  .map(DataArgument::fieldName)
                  .map(nameAllocator::get)).toArray(Object[]::new));
    }

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    return codeBlock.addStatement("return $1L -> $1L.$2L($3L)", adtLambdaParam, adt.matchMethod().element().getSimpleName(),
        joinStringsAsArguments(constructors.stream().map(MapperDerivator::mapperFieldName))).build();

  }

  private static CodeBlock visitorDispatchOptionImpl(DeriveUtils deriveUtils, DeriveContext deriveContext, FlavourImpl.OptionType optionType,
      AlgebraicDataType adt, DeclaredType visitorType, VariableElement visitorParam) {

    String visitorVarName = visitorParam.getSimpleName().toString();
    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    CodeBlock lambdaArgs = adt.dataConstruction().constructors().stream().map(dc -> {
      NameAllocator nameAllocator = new NameAllocator();
      nameAllocator.newName(adtLambdaParam, "adt var");
      nameAllocator.newName(visitorVarName, "visitor var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

      String lambdaArg = joinStringsAsArguments(
          IntStream.range(6, 6 + dc.arguments().size() + dc.typeRestrictions().size()).mapToObj(i -> "$" + i + 'L'));

      return CodeBlock.builder()
          .add("(this.$1L != null) ? (" + lambdaArg + ") -> $2T.$3L(this.$1L.$4L(" + lambdaArg + "))\n" + ": (" + lambdaArg + ") -> $2T.$5L()",
              Stream.<Object>concat(Stream.of(mapperFieldName(dc), ClassName.get(optionType.typeElement()), optionType.someConstructor(),
                  MapperDerivator.mapperApplyMethod(deriveUtils, deriveContext, dc), optionType.noneConstructor()),
                  Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
                      .map(DataArgument::fieldName)
                      .map(nameAllocator::get)).toArray(Object[]::new))
          .build();
    }).reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build()).orElse(CodeBlock.builder().build());

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(adtLambdaParam, "adt var");
    nameAllocator.newName(visitorVarName, "visitor var");

    return CodeBlock.builder()
        .addStatement("$T $L = $T.$L($L)", TypeName.get(deriveUtils.resolve(visitorType,
            tv -> deriveUtils.types().isSameType(tv, adt.matchMethod().returnTypeVariable())
                  ? Optional.of(deriveUtils.types().getDeclaredType(optionType.typeElement(), adt.matchMethod().returnTypeVariable()))
                  : Optional.empty())), nameAllocator.get("visitor var"),
            ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName()), MapperDerivator.visitorLambdaFactoryName(adt), lambdaArgs)
        .addStatement("return $1L -> $1L.$2L($3L)", nameAllocator.get("adt var"), adt.matchMethod().element().getSimpleName(),
            nameAllocator.get("visitor var"))
        .build();

  }

  private static List<MethodSpec> otherwiseLeftMethod(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils,
      FlavourImpl.EitherType eitherType) {

    NameAllocator typeVarAllocator = new NameAllocator();
    adt.typeConstructor().typeVariables().stream().forEach(tv -> typeVarAllocator.newName(tv.toString(), tv.toString()));
    typeVarAllocator.newName(adt.matchMethod().returnTypeVariable().toString(), "match type var");

    TypeElement eitherTypeElement = getTypeElement(eitherType);

    TypeVariableName leftTypeVarName = TypeVariableName.get(
        typeVarAllocator.newName(eitherTypeElement.getTypeParameters().get(0).toString(), "leftTypeVar"));

    TypeName eitherTypeName = deriveUtils.resolveToTypeName(eitherTypeElement.asType(),
        etv -> deriveUtils.types().isSameType(etv, eitherTypeElement.getTypeParameters().get(0).asType())
               ? Optional.of(leftTypeVarName)
               : (deriveUtils.types().isSameType(etv, eitherTypeElement.getTypeParameters().get(1).asType())
                  ? Optional.of(TypeVariableName.get(adt.matchMethod().returnTypeVariable()))
                  : Optional.empty()));

    TypeName returnType = ParameterizedTypeName.get(ClassName.get(FlavourImpl.findF(deriveContext.flavour(), deriveUtils.elements())),
        TypeName.get(adt.typeConstructor().declaredType()), eitherTypeName);

    TypeElement f0 = FlavourImpl.findF0(deriveContext.flavour(), deriveUtils.elements());

    String otherwiseLeftMethodName = "otherwise" + Utils.capitalize(getLeftConstructor(eitherType));
    String arg = uncapitalize(getLeftConstructor(eitherType));

    return Arrays.asList(MethodSpec.methodBuilder(otherwiseLeftMethodName)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addTypeVariable(leftTypeVarName)
        .addParameter(ParameterizedTypeName.get(ClassName.get(f0), leftTypeVarName), arg)
        .returns(returnType)
        .addCode(DataConstructions.cases()
            .multipleConstructors(MultipleConstructorsSupport.cases()
                .visitorDispatch(
                    (visitorParam, visitorType, constructors) -> visitorDispatchEitherImpl(deriveUtils, deriveContext, f0, eitherType, eitherTypeName,
                        adt, visitorType, visitorParam, arg))
                .functionsDispatch(
                    constructors -> functionsDispatchEitherImpl(deriveUtils, deriveContext, f0, eitherType, eitherTypeName, adt, constructors, arg)))
            .otherwise(() -> {
              throw new IllegalArgumentException();
            })
            .apply(adt.dataConstruction()))
        .build(), MethodSpec.methodBuilder(otherwiseLeftMethodName)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addTypeVariable(leftTypeVarName)
        .addParameter(leftTypeVarName, arg)
        .addStatement("return this.$L(() -> $L)", otherwiseLeftMethodName, arg)
        .returns(returnType)
        .build());
  }

  private static CodeBlock functionsDispatchEitherImpl(DeriveUtils deriveUtils, DeriveContext deriveContext, TypeElement f0,
      FlavourImpl.EitherType eitherType, TypeName eitherTypeName, AlgebraicDataType adt, List<DataConstructor> constructors, String argName) {

    CodeBlock.Builder codeBlock = CodeBlock.builder();
    TypeElement eitherTypeElement = getTypeElement(eitherType);

    for (DataConstructor dc : constructors) {
      NameAllocator nameAllocator = new NameAllocator();
      nameAllocator.newName("left", argName);
      nameAllocator.newName(mapperFieldName(dc), "case var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

      String lambdaArgs = joinStringsAsArguments(
          IntStream.range(9, 9 + dc.arguments().size() + dc.typeRestrictions().size()).mapToObj(i -> "$" + i + 'L'));

      codeBlock.addStatement("$1T $2L = (this.$3L != null) ? (" +
              lambdaArgs +
              ") -> $4T.$5L(this.$3L.$6L(" +
              lambdaArgs +
              "))\n" +
              ": (" +
              lambdaArgs +
              ") -> $4T.$7L(left.$8L())",

          Stream.concat(
              Stream.of(mapperTypeName(adt, dc, deriveContext, deriveUtils, eitherTypeName), nameAllocator.get("case var"), mapperFieldName(dc),
                  ClassName.get(eitherTypeElement), getRightConstructor(eitherType),
                  deriveUtils.allAbstractMethods(dc.deconstructor().visitorType()).get(0).getSimpleName().toString(), getLeftConstructor(eitherType),
                  deriveUtils.allAbstractMethods(f0).get(0).getSimpleName().toString()),
              Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
                  .map(DataArgument::fieldName)
                  .map(nameAllocator::get)).toArray(Object[]::new));
    }

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    return codeBlock.addStatement("return $1L -> $1L.$2L($3L)", adtLambdaParam, adt.matchMethod().element().getSimpleName(),
        joinStringsAsArguments(constructors.stream().map(MapperDerivator::mapperFieldName))).build();

  }

  private static CodeBlock visitorDispatchEitherImpl(DeriveUtils deriveUtils, DeriveContext deriveContext, TypeElement f0,
      FlavourImpl.EitherType eitherType, TypeName eitherTypeName, AlgebraicDataType adt, DeclaredType visitorType, VariableElement visitorParam,
      String argName) {

    TypeElement eitherTypeElement = getTypeElement(eitherType);
    String visitorVarName = visitorParam.getSimpleName().toString();
    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    CodeBlock lambdaArgs = adt.dataConstruction().constructors().stream().map(dc -> {
      NameAllocator nameAllocator = new NameAllocator();
      nameAllocator.newName(argName, "left arg");
      nameAllocator.newName(adtLambdaParam, "adt var");
      nameAllocator.newName(visitorVarName, "visitor var");
      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

      String lambdaArg = joinStringsAsArguments(
          IntStream.range(7, 7 + dc.arguments().size() + dc.typeRestrictions().size()).mapToObj(i -> "$" + i + 'L'));

      return CodeBlock.builder()
          .add("(this.$1L != null) ? (" +
              lambdaArg +
              ") -> $2T.$3L(this.$1L.$4L(" +
              lambdaArg +
              "))\n" +
              ": (" +
              lambdaArg +
              ") -> $2T.$5L(left.$6L())", Stream.concat(
              Stream.of(mapperFieldName(dc), ClassName.get(eitherTypeElement), getRightConstructor(eitherType),
                  MapperDerivator.mapperApplyMethod(deriveUtils, deriveContext, dc), getLeftConstructor(eitherType),
                  deriveUtils.allAbstractMethods(f0).get(0).getSimpleName().toString()),
              Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::idFunction))
                  .map(DataArgument::fieldName)
                  .map(nameAllocator::get)).toArray(Object[]::new))
          .build();
    }).reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build()).orElse(CodeBlock.builder().build());

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(adtLambdaParam, "adt var");
    nameAllocator.newName(visitorVarName, "visitor var");

    return CodeBlock.builder()
        .addStatement("$T $L = $T.$L($L)", deriveUtils.resolveToTypeName(visitorType,
            tv -> deriveUtils.types().isSameType(tv, adt.matchMethod().returnTypeVariable())
                  ? Optional.of(eitherTypeName)
                  : Optional.empty()), nameAllocator.get("visitor var"),
            ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName()), MapperDerivator.visitorLambdaFactoryName(adt), lambdaArgs)
        .addStatement("return $1L -> $1L.$2L($3L)", nameAllocator.get("adt var"), adt.matchMethod().element().getSimpleName(),
            nameAllocator.get("visitor var"))
        .build();

  }

  static String otherwiseBuilderClassName() {

    return "PartialMatchBuilder";
  }

}
