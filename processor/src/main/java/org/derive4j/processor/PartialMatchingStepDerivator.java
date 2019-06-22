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

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataConstructor;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static org.derive4j.processor.Utils.joinStringsAsArguments;

class PartialMatchingStepDerivator {

  private final MapperDerivator                       mapperDerivator;
  private final PatternMatchingDerivator.MatchingKind matchingKind;

  PartialMatchingStepDerivator(DeriveUtils deriveUtils, PatternMatchingDerivator.MatchingKind matchingKind) {
    mapperDerivator = new MapperDerivator(deriveUtils);
    this.matchingKind = matchingKind;
  }

  TypeSpec partialMatchingStepTypeSpec(AlgebraicDataType adt, List<DataConstructor> previousConstructors,
      DataConstructor currentConstructor, List<DataConstructor> nextConstructors) {

    ParameterizedTypeName nextStepTypeName = superClass(adt, matchingKind, nextConstructors);

    ParameterSpec adtParamSpec = PatternMatchingDerivator.asParameterSpec(adt);
    return TypeSpec.classBuilder(partialMatchBuilderClassName(currentConstructor))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(PatternMatchingDerivator.matcherVariables(adt).map(TypeVariableName::get).collect(toList()))
        .superclass(nextStepTypeName)
        .addMethod(MethodSpec.constructorBuilder()
            .addParameters(
                (matchingKind == PatternMatchingDerivator.MatchingKind.CaseOf) ? singleton(adtParamSpec) : emptyList())
            .addParameters(previousConstructors.stream()
                .map(dc -> ParameterSpec
                    .builder(mapperDerivator.mapperTypeName(adt, dc), MapperDerivator.mapperFieldName(dc))
                    .build())
                .collect(toList()))
            .addStatement("super($L)",
                (matchingKind == PatternMatchingDerivator.MatchingKind.CaseOf ? adtParamSpec.name + ", " : "")
                    + joinStringsAsArguments(Stream.concat(
                        previousConstructors.stream().map(MapperDerivator::mapperFieldName), Stream.of("null"))))
            .build())
        .addMethods(partialMatchMethodBuilder(adt, previousConstructors, 0, currentConstructor, nextStepTypeName)
            .map(MethodSpec.Builder::build)
            .collect(toList()))
        .build();
  }

  Stream<MethodSpec.Builder> partialMatchMethodBuilder(AlgebraicDataType adt,
      List<DataConstructor> previousConstructors, int nbSkipConstructors, DataConstructor currentConstructor,
      ParameterizedTypeName returnType) {

    ParameterizedTypeName otherwiseMatcherTypeName = OtherwiseMatchingStepDerivator.otherwiseMatcherTypeName(adt);

    String args = (matchingKind == PatternMatchingDerivator.MatchingKind.CaseOf
        ? (previousConstructors.isEmpty() ? "this." : "((" + otherwiseMatcherTypeName.toString() + ") this).")
            + PatternMatchingDerivator.asFieldSpec(adt).name + ", "
        : "")
        + Stream.concat(
            Stream.concat(
                previousConstructors.stream()
                    .map(dc -> "((" + otherwiseMatcherTypeName + ") this)." + MapperDerivator.mapperFieldName(dc)),
                IntStream.range(0, nbSkipConstructors).mapToObj(__ -> "null")),
            Stream.of(MapperDerivator.mapperFieldName(currentConstructor))).reduce((s1, s2) -> s1 + ", " + s2).orElse(
                "");

    return Stream.of(

        MethodSpec.methodBuilder(currentConstructor.name())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .returns(returnType)
            .addParameter(mapperDerivator.mapperTypeName(adt, currentConstructor),
                MapperDerivator.mapperFieldName(currentConstructor))
            .addStatement("return new $L<>($L)", returnType.rawType.simpleName(), args),

        PatternMatchingDerivator.constantMatchMethodBuilder(adt, currentConstructor).returns(returnType));
  }

  static ParameterizedTypeName superClass(AlgebraicDataType adt, PatternMatchingDerivator.MatchingKind matchingKind,
      List<DataConstructor> nextConstructors) {
    return ParameterizedTypeName.get(
        adt.deriveConfig().targetClass().className().nestedClass(matchingKind.wrapperClassName()).nestedClass(
            nextConstructors.isEmpty()
                ? OtherwiseMatchingStepDerivator.otherwiseBuilderClassName()
                : partialMatchBuilderClassName(nextConstructors.get(0))),
        PatternMatchingDerivator.matcherVariables(adt).map(TypeVariableName::get).toArray(TypeName[]::new));
  }

  private static String partialMatchBuilderClassName(DataConstructor currentConstructor) {

    return "PartialMatcher_" + Utils.capitalize(currentConstructor.name());
  }
}
