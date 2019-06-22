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

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeVariable;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataConstructor;

import static org.derive4j.processor.Utils.fold;
import static org.derive4j.processor.Utils.uncapitalize;

class PatternMatchingDerivator implements Derivator {

  enum MatchingKind {
    Cases {
      @Override
      String wrapperClassName() {
        return "CasesMatchers";
      }

      @Override
      String factoryMethodName() {
        return "cases";
      }
    },
    CaseOf {
      @Override
      String wrapperClassName() {
        return "CaseOfMatchers";
      }

      @Override
      String factoryMethodName() {
        return "caseOf";
      }
    };

    abstract String wrapperClassName();

    abstract String factoryMethodName();
  }

  PatternMatchingDerivator(DeriveUtils deriveUtils, MatchingKind matchingKind) {
    this.matchingKind = matchingKind;
    totalMatching = new TotalMatchingStepDerivator(deriveUtils, matchingKind);
    partialMatching = new PartialMatchingStepDerivator(deriveUtils, matchingKind);
    otherwiseMatching = new OtherwiseMatchingStepDerivator(deriveUtils, matchingKind);
  }

  private final MatchingKind                   matchingKind;
  private final TotalMatchingStepDerivator     totalMatching;
  private final OtherwiseMatchingStepDerivator otherwiseMatching;
  private final PartialMatchingStepDerivator   partialMatching;

  @Override
  public DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt) {

    List<DataConstructor> constructors = adt.dataConstruction().constructors();

    return ((matchingKind == MatchingKind.CaseOf) && (constructors.size() <= 1))
        ? DeriveResult.result(DerivedCodeSpec.none())
        : fold(constructors.stream().findFirst(), DeriveResult.result(DerivedCodeSpec.none()), firstConstructor -> {

          ClassName wrapperClass = adt.deriveConfig().targetClass().className().nestedClass(
              matchingKind.wrapperClassName());
          ClassName totalMatchBuilderClassName = wrapperClass
              .nestedClass(TotalMatchingStepDerivator.totalMatchBuilderClassName(firstConstructor));

          TypeName firstMatchBuilderTypeName = Utils.typeName(totalMatchBuilderClassName,
              adt.typeConstructor().typeVariables().stream().map(TypeName::get));

          TypeName firstMatchBuilderWildcardTypeName = Utils.typeName(totalMatchBuilderClassName,
              adt.typeConstructor().typeVariables().stream().map(__ -> WildcardTypeName.subtypeOf(Object.class)));

          String initialCasesStepFieldName = uncapitalize(totalMatchBuilderClassName.simpleName());

          MethodSpec.Builder matchFactory = MethodSpec.methodBuilder(matchingKind.factoryMethodName())
              .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
              .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(
                  Collectors.toList()))
              .returns(firstMatchBuilderTypeName);

          TypeSpec.Builder wrapperClassSpec = TypeSpec.classBuilder(wrapperClass)
              .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
              .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

          if (matchingKind == MatchingKind.Cases) {
            FieldSpec.Builder initialCasesStepField = FieldSpec
                .builder(firstMatchBuilderWildcardTypeName, initialCasesStepFieldName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

            if (adt.typeConstructor().typeVariables().isEmpty()) {
              initialCasesStepField.initializer("new $L()", totalMatchBuilderClassName.simpleName());

              matchFactory.addStatement("return $T.$L", wrapperClass, initialCasesStepFieldName);
            } else {
              initialCasesStepField.initializer("new $L<>()", totalMatchBuilderClassName.simpleName());

              matchFactory
                  .addAnnotation(
                      AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build())
                  .addStatement("return ($T) $T.$L", firstMatchBuilderTypeName, wrapperClass,
                      initialCasesStepFieldName);

            }
            wrapperClassSpec.addField(initialCasesStepField.build());
          } else {
            ParameterSpec adtParameterSpec = ParameterSpec
                .builder(TypeName.get(adt.typeConstructor().declaredType()),
                    uncapitalize(adt.typeConstructor().typeElement().getSimpleName().toString()))
                .build();
            matchFactory.addParameter(adtParameterSpec).addStatement("return new $T($N)", firstMatchBuilderTypeName,
                adtParameterSpec);
          }

          return DeriveResult.result(DerivedCodeSpec.codeSpec(wrapperClassSpec.addTypes(Stream.concat(
              // Total matching path:
              IntStream.range(0, constructors.size())
                  .mapToObj(i -> totalMatching.stepTypeSpec(adt, constructors.subList(0, i), constructors.get(i),
                      constructors.subList(i + 1, constructors.size()))),

              // Partial matching path:
              (constructors.size() > 1)
                  ? IntStream.rangeClosed(2, constructors.size())
                      .mapToObj(i -> (i < constructors.size())
                          ? partialMatching.partialMatchingStepTypeSpec(adt, constructors.subList(0, i),
                              constructors.get(i), constructors.subList(i + 1, constructors.size()))
                          : otherwiseMatching.stepTypeSpec(adt))
                  : Stream.empty()

        ).collect(Collectors.toList())).build(), matchFactory.build()));

        });
  }

  static MethodSpec.Builder constantMatchMethodBuilder(AlgebraicDataType adt, DataConstructor currentConstructor) {

    NameAllocator nameAllocator = new NameAllocator();
    String argName = uncapitalize(adt.matchMethod().returnTypeVariable().toString());
    return MethodSpec.methodBuilder(currentConstructor.name() + '_')
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addParameter(TypeName.get(adt.matchMethod().returnTypeVariable()), nameAllocator.newName(argName))
        .addStatement("return this.$L(($L) -> $L)", currentConstructor.name(), Utils.asLambdaParametersString(
            currentConstructor.arguments(), currentConstructor.typeRestrictions(), nameAllocator), argName);
  }

  static Stream<TypeVariable> matcherVariables(AlgebraicDataType adt) {

    return Stream.concat(adt.typeConstructor().typeVariables().stream(),
        Stream.of(adt.matchMethod().returnTypeVariable()));
  }

  static ParameterSpec asParameterSpec(AlgebraicDataType adt) {
    return ParameterSpec
        .builder(TypeName.get(adt.typeConstructor().declaredType()),
            '_' + uncapitalize(adt.typeConstructor().typeElement().getSimpleName().toString()))
        .build();
  }

  static FieldSpec asFieldSpec(AlgebraicDataType adt) {
    return FieldSpec
        .builder(TypeName.get(adt.typeConstructor().declaredType()),
            '_' + uncapitalize(adt.typeConstructor().typeElement().getSimpleName().toString()))
        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
        .build();
  }

}
