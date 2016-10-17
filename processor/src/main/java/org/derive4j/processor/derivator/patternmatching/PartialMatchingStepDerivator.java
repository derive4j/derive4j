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

import com.squareup.javapoet.MethodSpec;
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
import org.derive4j.processor.Utils;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.derivator.MapperDerivator;

import static org.derive4j.processor.Utils.joinStringsAsArguments;
import static org.derive4j.processor.derivator.MapperDerivator.mapperFieldName;
import static org.derive4j.processor.derivator.patternmatching.OtherwiseMatchingStepDerivator.otherwiseBuilderClassName;
import static org.derive4j.processor.derivator.patternmatching.PatternMatchingDerivator.constantMatchMethodBuilder;
import static org.derive4j.processor.derivator.patternmatching.PatternMatchingDerivator.matcherVariables;

public class PartialMatchingStepDerivator {

  private final DeriveUtils deriveUtils;
  private final MapperDerivator mapperDerivator;

  PartialMatchingStepDerivator(DeriveUtils deriveUtils) {
    this.deriveUtils = deriveUtils;
    mapperDerivator = new MapperDerivator(deriveUtils);
  }

  Stream<MethodSpec.Builder> partialMatchMethodBuilders(AlgebraicDataType adt, List<DataConstructor> previousConstructors,
      List<DataConstructor> nextConstructors) {

    return IntStream.rangeClosed(1, nextConstructors.size()).mapToObj(i -> {
      String returnCLassName = (i == nextConstructors.size())
          ? otherwiseBuilderClassName()
          : partialMatchBuilderClassName(nextConstructors.get(i));
      return partialMatchMethodBuilder(adt, previousConstructors, i, nextConstructors.get(i - 1), returnCLassName);
    }).flatMap(Function.identity());
  }

  TypeSpec partialMatchingStepTypeSpec(AlgebraicDataType adt, List<DataConstructor> previousConstructors,
      DataConstructor currentConstructor, List<DataConstructor> nextConstructors) {

    return TypeSpec.classBuilder(partialMatchBuilderClassName(currentConstructor))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(matcherVariables(adt).map(TypeVariableName::get).collect(Collectors.toList()))
        .superclass(
            ParameterizedTypeName.get(adt.deriveConfig().targetClass().className().nestedClass(otherwiseBuilderClassName()),
                matcherVariables(adt).map(TypeVariableName::get).toArray(TypeName[]::new)))
        .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameters(previousConstructors.stream()
                .map(dc -> ParameterSpec.builder(mapperDerivator.mapperTypeName(adt, dc), mapperFieldName(dc)).build())
                .collect(Collectors.toList()))
            .addStatement("super($L)", joinStringsAsArguments(
                Stream.concat(previousConstructors.stream().map(MapperDerivator::mapperFieldName),
                    IntStream.range(previousConstructors.size(), adt.dataConstruction().constructors().size())
                        .mapToObj(__ -> "null"))))
            .build())
        .addMethods(Stream.concat(partialMatchMethodBuilder(adt, previousConstructors, 0, currentConstructor,
            nextConstructors.isEmpty()
                ? otherwiseBuilderClassName()
                : partialMatchBuilderClassName(nextConstructors.get(0))),
            partialMatchMethodBuilders(adt, previousConstructors, nextConstructors))
            .map(MethodSpec.Builder::build)
            .collect(Collectors.toList()))
        .build();
  }

  private Stream<MethodSpec.Builder> partialMatchMethodBuilder(AlgebraicDataType adt, List<DataConstructor> previousConstructors,
      int nbSkipConstructors, DataConstructor currentConstructor, String partialMatchBuilderClassName) {

    ParameterizedTypeName returnType = ParameterizedTypeName.get(
        adt.deriveConfig().targetClass().className().nestedClass(partialMatchBuilderClassName),
        matcherVariables(adt).map(TypeName::get).toArray(TypeName[]::new));

    return Stream.of(MethodSpec.methodBuilder(currentConstructor.name())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .returns(returnType)
            .addParameter(mapperDerivator.mapperTypeName(adt, currentConstructor), mapperFieldName(currentConstructor))
            .addStatement("return new $L<>($L)", partialMatchBuilderClassName, Stream.concat(
                Stream.concat(previousConstructors.stream().map(dc -> "super." + mapperFieldName(dc)),
                    IntStream.range(0, nbSkipConstructors).mapToObj(__ -> "null")), Stream.of(mapperFieldName
                    (currentConstructor)))
                .reduce((s1, s2) -> s1 + ", " + s2)
                .orElse("")),

        constantMatchMethodBuilder(adt, currentConstructor).returns(returnType));
  }

  private static String partialMatchBuilderClassName(DataConstructor currentConstructor) {

    return "PartialMatchBuilder" + Utils.capitalize(currentConstructor.name());
  }
}
