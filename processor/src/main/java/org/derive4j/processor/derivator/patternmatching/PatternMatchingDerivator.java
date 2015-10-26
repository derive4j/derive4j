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

import com.squareup.javapoet.*;
import org.derive4j.processor.Utils;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.DeriveContext;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeVariable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.derive4j.processor.Utils.*;
import static org.derive4j.processor.derivator.MapperDerivator.mapperFieldName;
import static org.derive4j.processor.derivator.MapperDerivator.mapperTypeName;
import static org.derive4j.processor.derivator.patternmatching.OtherwiseMatchingStepDerivator.otherwiseBuilderClassName;
import static org.derive4j.processor.derivator.patternmatching.TotalMatchingStepDerivator.totalMatchingStepTypeSpec;

public class PatternMatchingDerivator {

  public static DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    List<DataConstructor> constructors = adt.dataConstruction().constructors();

    return fold(constructors.stream().findFirst(),
        DeriveResult.result(DerivedCodeSpec.none()),
        firstConstructor -> {

          ClassName totalMatchBuilderClassName = getClassName(deriveContext, TotalMatchingStepDerivator.totalMatchBuilderClassName(firstConstructor));

          TypeName firstMatchBuilderTypeName = Utils.typeName(totalMatchBuilderClassName,
              adt.typeConstructor().typeVariables().stream().map(TypeName::get));

          TypeName firstMatchBuilderObjectifiedTypeName = Utils.typeName(totalMatchBuilderClassName,
              adt.typeConstructor().typeVariables().stream().map(__ -> ClassName.get(Object.class)));

          String initialCasesStepFieldName = Utils.uncapitalize(TotalMatchingStepDerivator.totalMatchBuilderClassName(firstConstructor));

          FieldSpec.Builder initialCasesStepField = FieldSpec.builder(firstMatchBuilderObjectifiedTypeName, initialCasesStepFieldName)
              .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
              .initializer("new $T()",
                  firstMatchBuilderObjectifiedTypeName);

          MethodSpec.Builder matchFactory = MethodSpec.methodBuilder("cases")
              .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
              .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
              .returns(firstMatchBuilderTypeName);


          if (adt.typeConstructor().typeVariables().isEmpty()) {

            matchFactory.addStatement("return $L", initialCasesStepFieldName);
          } else {
            initialCasesStepField
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "rawtypes").build());

            matchFactory.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build())
                .addStatement("return ($T) $L", firstMatchBuilderTypeName, initialCasesStepFieldName);

          }

          return DeriveResult.result(DerivedCodeSpec.codeSpec(
              Stream.concat(
                  // Total matching path:
                  IntStream.range(0, constructors.size())
                      .mapToObj(i -> totalMatchingStepTypeSpec(adt,
                          constructors.subList(0, i), constructors.get(i), constructors.subList(i + 1, constructors.size()),
                          deriveContext, deriveUtils)),

                  // Partial matching path:
                  constructors.size() > 1
                      ? IntStream.rangeClosed(1, constructors.size())
                      .mapToObj(i -> i < constructors.size()
                          ? partialMatchingStepTypeSpec(adt,
                          constructors.subList(0, i), constructors.get(i), constructors.subList(i + 1, constructors.size()),
                          deriveContext, deriveUtils)
                          : OtherwiseMatchingStepDerivator.otherwiseMatchingStepTypeSpec(adt, deriveContext, deriveUtils))
                      : Stream.<TypeSpec>empty()

              ).collect(Collectors.<TypeSpec>toList()),

              initialCasesStepField.build(),

              matchFactory.build()));

        });
  }

  private static TypeSpec partialMatchingStepTypeSpec(AlgebraicDataType adt, List<DataConstructor> previousConstructors, DataConstructor currentConstructor, List<DataConstructor> nextConstructors, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return TypeSpec.classBuilder(partialMatchBuilderClassName(currentConstructor))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(matcherVariables(adt).map(TypeVariableName::get).collect(Collectors.toList()))
        .superclass(ParameterizedTypeName.get(getClassName(deriveContext, otherwiseBuilderClassName()),
            matcherVariables(adt).map(TypeVariableName::get).toArray(TypeName[]::new)))
        .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameters(previousConstructors.stream()
                .map(dc -> ParameterSpec.builder(mapperTypeName(adt, dc, deriveContext, deriveUtils), mapperFieldName(dc)).build())
                .collect(Collectors.toList()))
            .addStatement("super($L)", joinStringsAsArguments(Stream.concat(
                previousConstructors.stream().map(dc -> mapperFieldName(dc)),
                IntStream.range(previousConstructors.size(), adt.dataConstruction().constructors().size()).mapToObj(__ -> "null")
            )))
            .build())
        .addMethod(partialMatchMethodBuilder(adt, previousConstructors, 0, currentConstructor, nextConstructors.isEmpty() ? otherwiseBuilderClassName() : partialMatchBuilderClassName(nextConstructors.get(0)), deriveContext, deriveUtils)
            .build())
        .addMethods(partialMatchMethodBuilders(adt, previousConstructors, nextConstructors, deriveContext, deriveUtils).map(MethodSpec.Builder::build).collect(Collectors.toList()))
        .build();
  }

  static Stream<MethodSpec.Builder> partialMatchMethodBuilders(AlgebraicDataType adt, List<DataConstructor> previousConstructors, List<DataConstructor> nextConstructors, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return IntStream.rangeClosed(1, nextConstructors.size()).mapToObj(i -> {
      String returnCLassName = i == nextConstructors.size() ? otherwiseBuilderClassName() : partialMatchBuilderClassName(nextConstructors.get(i));
      return partialMatchMethodBuilder(adt, previousConstructors, i, nextConstructors.get(i - 1), returnCLassName, deriveContext, deriveUtils);
    });
  }

  private static MethodSpec.Builder partialMatchMethodBuilder(AlgebraicDataType adt, List<DataConstructor> previousConstructors, int nbSkipConstructors, DataConstructor currentConstructor, String partialMatchBuilderClassName, DeriveContext deriveContext, DeriveUtils deriveUtils) {

    return MethodSpec.methodBuilder(currentConstructor.name())
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .returns(ParameterizedTypeName.get(getClassName(deriveContext, partialMatchBuilderClassName),
            matcherVariables(adt).map(TypeName::get).toArray(TypeName[]::new)))
        .addParameter(mapperTypeName(adt, currentConstructor, deriveContext, deriveUtils), mapperFieldName(currentConstructor))
        .addStatement("return new $L<>($L)", partialMatchBuilderClassName,
            Stream.concat(Stream.concat(previousConstructors.stream().map(dc -> "super." + mapperFieldName(dc)),
                IntStream.range(0, nbSkipConstructors).mapToObj(__ -> "null")),
                Stream.of(mapperFieldName(currentConstructor)))
                .reduce((s1, s2) -> s1 + ", " + s2).orElse(""));
  }


  private static String partialMatchBuilderClassName(DataConstructor currentConstructor) {
    return "PartialMatchBuilder" + Utils.capitalize(currentConstructor.name());
  }


  static Stream<TypeVariable> matcherVariables(AlgebraicDataType adt) {
    return Stream.concat(adt.typeConstructor().typeVariables().stream(), Stream.of(adt.matchMethod().returnTypeVariable()));
  }

}
