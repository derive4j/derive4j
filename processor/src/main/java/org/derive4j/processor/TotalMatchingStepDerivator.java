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
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.derive4j.processor.Utils.joinStringsAsArguments;
import static org.derive4j.processor.Utils.uncapitalize;
import static org.derive4j.processor.api.model.DataConstructions.caseOf;

class TotalMatchingStepDerivator {

  private final DeriveUtils                           deriveUtils;
  private final MapperDerivator                       mapperDerivator;
  private final PartialMatchingStepDerivator          partialMatching;
  private final PatternMatchingDerivator.MatchingKind matchingKind;

  TotalMatchingStepDerivator(DeriveUtils deriveUtils, PatternMatchingDerivator.MatchingKind matchingKind) {
    this.deriveUtils = deriveUtils;
    mapperDerivator = new MapperDerivator(deriveUtils);
    partialMatching = new PartialMatchingStepDerivator(deriveUtils, matchingKind);
    this.matchingKind = matchingKind;
  }

  TypeSpec stepTypeSpec(AlgebraicDataType adt, List<DataConstructor> previousConstructors,
      DataConstructor currentConstructor, List<DataConstructor> nextConstructors) {

    TypeVariableName returnTypeVarName = TypeVariableName.get(adt.matchMethod().returnTypeVariable());

    TypeSpec.Builder totalMatchBuilder = TypeSpec.classBuilder(totalMatchBuilderClassName(currentConstructor))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .addTypeVariables(
            adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()));

    MethodSpec.Builder currentConstructorTotalMatchMethod = MethodSpec.methodBuilder(currentConstructor.name())
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addParameter(mapperDerivator.mapperTypeName(adt, currentConstructor),
            MapperDerivator.mapperFieldName(currentConstructor));

    MethodSpec.Builder currentConstructorTotalMatchConstantMethod = PatternMatchingDerivator
        .constantMatchMethodBuilder(adt, currentConstructor);

    final Stream<MethodSpec> partialMatchMethods;

    ParameterSpec adtParamSpec = PatternMatchingDerivator.asParameterSpec(adt);
    MethodSpec.Builder constructor = MethodSpec.constructorBuilder().addParameters(
        (matchingKind == PatternMatchingDerivator.MatchingKind.CaseOf) ? singleton(adtParamSpec) : emptyList());

    FieldSpec adtFieldSpec = PatternMatchingDerivator.asFieldSpec(adt);

    if (previousConstructors.isEmpty()) {

      if (matchingKind == PatternMatchingDerivator.MatchingKind.CaseOf) {
        totalMatchBuilder.addField(adtFieldSpec);
        constructor.addStatement("this.$N = $N", adtFieldSpec, adtParamSpec);
      }

      totalMatchBuilder.addMethod(constructor.build());

      currentConstructorTotalMatchMethod.addTypeVariable(returnTypeVarName);
      currentConstructorTotalMatchConstantMethod.addTypeVariable(returnTypeVarName);

      partialMatchMethods = IntStream.rangeClosed(1, nextConstructors.size())
          .mapToObj(
              i -> partialMatching.partialMatchMethodBuilder(adt, previousConstructors, i, nextConstructors.get(i - 1),
                  PartialMatchingStepDerivator.superClass(adt, matchingKind,
                      nextConstructors.subList(i, nextConstructors.size()))))
          .flatMap(Function.identity())
          .map(mb -> mb.addTypeVariable(returnTypeVarName).build());

    } else {

      totalMatchBuilder.addTypeVariable(returnTypeVarName)
          .superclass(PartialMatchingStepDerivator.superClass(adt, matchingKind, nextConstructors))
          .addMethod(constructor
              .addParameters(previousConstructors.stream()
                  .map(dc -> ParameterSpec
                      .builder(mapperDerivator.mapperTypeName(adt, dc), MapperDerivator.mapperFieldName(dc))
                      .build())
                  .collect(Collectors.toList()))
              .addStatement("super($L)",
                  (matchingKind == PatternMatchingDerivator.MatchingKind.CaseOf ? adtParamSpec.name + ", " : "")
                      + joinStringsAsArguments(Stream.concat(
                          previousConstructors.stream().map(MapperDerivator::mapperFieldName), Stream.of("null"))))
              .build());

      partialMatchMethods = Stream.empty();
    }

    if (nextConstructors.isEmpty()) {

      TypeName returnType = (matchingKind == PatternMatchingDerivator.MatchingKind.Cases)
          ? TypeName.get(
              deriveUtils.types().getDeclaredType(deriveUtils.function1Model(adt.deriveConfig().flavour()).samClass(),
                  adt.typeConstructor().declaredType(), adt.matchMethod().returnTypeVariable()))
          : TypeName.get(adt.matchMethod().returnTypeVariable());

      currentConstructorTotalMatchMethod.returns(returnType).addCode(caseOf(adt.dataConstruction())
          .multipleConstructors(MultipleConstructorsSupport.cases()
              .visitorDispatch((visitorParam, visitorType, constructors) -> vistorDispatchImpl(adt, visitorType,
                  visitorParam, previousConstructors, currentConstructor))
              .functionsDispatch(constructors1 -> functionDispatchImpl(adt, previousConstructors, currentConstructor)))
          .oneConstructor(__ -> oneConstructorImpl(currentConstructor, adt))
          .noConstructor(() -> {
            throw new IllegalArgumentException();
          }));

      currentConstructorTotalMatchConstantMethod.returns(returnType);

    } else {

      DataConstructor firstNextConstructor = nextConstructors.get(0);

      ParameterizedTypeName returnType = ParameterizedTypeName.get(
          adt.deriveConfig().targetClass().className().nestedClass(matchingKind.wrapperClassName()).nestedClass(
              totalMatchBuilderClassName(firstNextConstructor)),
          PatternMatchingDerivator.matcherVariables(adt).map(TypeName::get).toArray(TypeName[]::new));

      ParameterizedTypeName otherwiseMatcherTypeName = OtherwiseMatchingStepDerivator.otherwiseMatcherTypeName(adt);

      String args = (matchingKind == PatternMatchingDerivator.MatchingKind.CaseOf
          ? (previousConstructors.isEmpty() ? "this." : "((" + otherwiseMatcherTypeName.toString() + ") this).")
              + adtFieldSpec.name + ", "
          : "")
          + Stream
              .concat(
                  previousConstructors.stream()
                      .map(dc -> "((" + otherwiseMatcherTypeName.toString() + ") this)."
                          + MapperDerivator.mapperFieldName(dc)),
                  Stream.of(MapperDerivator.mapperFieldName(currentConstructor)))
              .reduce((s1, s2) -> s1 + ", " + s2)
              .orElse("");

      currentConstructorTotalMatchMethod.returns(returnType).addStatement("return new $L<>($L)",
          totalMatchBuilderClassName(firstNextConstructor), args);

      currentConstructorTotalMatchConstantMethod.returns(returnType);

    }

    return totalMatchBuilder.addMethod(currentConstructorTotalMatchMethod.build())
        .addMethod(currentConstructorTotalMatchConstantMethod.build())
        .addMethods(partialMatchMethods.collect(Collectors.toList()))
        .build();

  }

  private CodeBlock functionDispatchImpl(AlgebraicDataType adt, List<DataConstructor> previousConstructors,
      DataConstructor currentConstructor) {

    CodeBlock.Builder codeBlock = CodeBlock.builder();

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(MapperDerivator.mapperFieldName(currentConstructor),
        MapperDerivator.mapperFieldName(currentConstructor));

    for (DataConstructor dc : previousConstructors) {
      nameAllocator.newName(MapperDerivator.mapperFieldName(dc), MapperDerivator.mapperFieldName(dc));
      codeBlock.addStatement("$1T $2L = super.$2L", mapperDerivator.mapperTypeName(adt, dc),
          MapperDerivator.mapperFieldName(dc));
    }

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    nameAllocator.newName(adtLambdaParam, "adt var");

    String template;
    Object templateArg;
    if (matchingKind == PatternMatchingDerivator.MatchingKind.Cases) {
      template = "$1L -> $1L";
      templateArg = nameAllocator.get("adt var");
    } else {
      template = "((" + OtherwiseMatchingStepDerivator.otherwiseMatcherTypeName(adt).toString() + ") this).$1N";
      templateArg = PatternMatchingDerivator.asFieldSpec(adt);
    }
    return codeBlock
        .addStatement("return " + template + ".$2L($3L)", templateArg, adt.matchMethod().element().getSimpleName(),
            joinStringsAsArguments(Stream.concat(previousConstructors.stream().map(MapperDerivator::mapperFieldName),
                Stream.of(MapperDerivator.mapperFieldName(currentConstructor)))))
        .build();
  }

  private CodeBlock vistorDispatchImpl(AlgebraicDataType adt, DeclaredType visitorType, VariableElement visitorParam,
      List<DataConstructor> previousConstructors, DataConstructor currentConstructor) {

    String visitorVarName = visitorParam.getSimpleName().toString();
    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(MapperDerivator.mapperFieldName(currentConstructor), "case arg");
    nameAllocator.newName(adtLambdaParam, "adt var");
    nameAllocator.newName(visitorVarName, "visitor var");

    ParameterizedTypeName otherwiseMatcherTypeName = OtherwiseMatchingStepDerivator.otherwiseMatcherTypeName(adt);

    CodeBlock.Builder implBuilder = CodeBlock.builder()
        .addStatement("$T $L = $T.$L($L)", TypeName.get(visitorType), nameAllocator.get("visitor var"),
            adt.deriveConfig().targetClass().className(), MapperDerivator.visitorLambdaFactoryName(adt),
            joinStringsAsArguments(Stream.concat(
                previousConstructors.stream()
                    .map(dc -> "((" + otherwiseMatcherTypeName.toString() + ") this)."
                        + MapperDerivator.mapperFieldName(dc)),
                Stream.of(MapperDerivator.mapperFieldName(currentConstructor)))));

    if (matchingKind == PatternMatchingDerivator.MatchingKind.Cases) {
      implBuilder.addStatement("return $1L -> $1L.$2L($3L)", nameAllocator.get("adt var"),
          adt.matchMethod().element().getSimpleName(), nameAllocator.get("visitor var"));
    } else {
      implBuilder.addStatement(
          "return ((" + OtherwiseMatchingStepDerivator.otherwiseMatcherTypeName(adt).toString()
              + ") this).$1N.$2L($3L)",
          PatternMatchingDerivator

              .asFieldSpec(adt),
          adt.matchMethod().element().getSimpleName(), nameAllocator.get("visitor var"));
    }
    return implBuilder.build();
  }

  static String totalMatchBuilderClassName(DataConstructor currentConstructor) {

    return "TotalMatcher_" + Utils.capitalize(currentConstructor.name());
  }

  private static CodeBlock oneConstructorImpl(DataConstructor currentConstructor, AlgebraicDataType adt) {

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(MapperDerivator.mapperFieldName(currentConstructor),
        MapperDerivator.mapperFieldName(currentConstructor));

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());
    nameAllocator.newName(adtLambdaParam, "adt var");

    return CodeBlock.builder()
        .addStatement("return $1L -> $1L.$2L($3L)", nameAllocator.get("adt var"),
            adt.matchMethod().element().getSimpleName(), MapperDerivator.mapperFieldName(currentConstructor))
        .build();
  }
}
