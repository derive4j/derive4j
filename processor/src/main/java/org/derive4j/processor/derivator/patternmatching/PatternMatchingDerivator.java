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

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeVariable;
import org.derive4j.processor.Utils;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.derivator.MapperDerivator;

import static org.derive4j.processor.Utils.fold;
import static org.derive4j.processor.Utils.uncapitalize;

public class PatternMatchingDerivator implements Derivator {

  public PatternMatchingDerivator(DeriveUtils deriveUtils) {
    mapperDerivator = new MapperDerivator(deriveUtils);
    totalMatching = new TotalMatchingStepDerivator(deriveUtils);
    partialMatching = new PartialMatchingStepDerivator(deriveUtils);
    otherwiseMatching = new OtherwiseMatchingStepDerivator(deriveUtils);
  }

  private final TotalMatchingStepDerivator totalMatching;
  private final OtherwiseMatchingStepDerivator otherwiseMatching;
  private final MapperDerivator mapperDerivator;
  private final PartialMatchingStepDerivator partialMatching;

  @Override
  public DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt) {

    List<DataConstructor> constructors = adt.dataConstruction().constructors();

    return fold(constructors.stream().findFirst(), DeriveResult.result(DerivedCodeSpec.none()), firstConstructor -> {

      ClassName totalMatchBuilderClassName = adt.deriveConfig()
          .targetClass()
          .className()
          .nestedClass(TotalMatchingStepDerivator.totalMatchBuilderClassName(firstConstructor));

      TypeName firstMatchBuilderTypeName = Utils.typeName(totalMatchBuilderClassName,
          adt.typeConstructor().typeVariables().stream().map(TypeName::get));

      TypeName firstMatchBuilderWildcardTypeName = Utils.typeName(totalMatchBuilderClassName,
          adt.typeConstructor().typeVariables().stream().map(__ -> WildcardTypeName.subtypeOf(Object.class)));

      String initialCasesStepFieldName = uncapitalize(totalMatchBuilderClassName.simpleName());

      FieldSpec.Builder initialCasesStepField = FieldSpec.builder(firstMatchBuilderWildcardTypeName, initialCasesStepFieldName)
          .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

      MethodSpec.Builder matchFactory = MethodSpec.methodBuilder("cases")
          .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
          .addTypeVariables(
              adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
          .returns(firstMatchBuilderTypeName);

      if (adt.typeConstructor().typeVariables().isEmpty()) {
        initialCasesStepField.initializer("new $L()", totalMatchBuilderClassName.simpleName());

        matchFactory.addStatement("return $L", initialCasesStepFieldName);
      } else {
        initialCasesStepField.initializer("new $L<>()", totalMatchBuilderClassName.simpleName());

        matchFactory.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build())
            .addStatement("return ($T) $L", firstMatchBuilderTypeName, initialCasesStepFieldName);

      }

      return DeriveResult.result(DerivedCodeSpec.codeSpec(Stream.concat(
          // Total matching path:
          IntStream.range(0, constructors.size())
              .mapToObj(i -> totalMatching.stepTypeSpec(adt, constructors.subList(0, i), constructors.get(i),
                  constructors.subList(i + 1, constructors.size()))),

          // Partial matching path:
          (constructors.size() > 1)
              ? IntStream.rangeClosed(1, constructors.size())
              .mapToObj(i -> (i < constructors.size())
                  ? partialMatching.partialMatchingStepTypeSpec(adt, constructors.subList(0, i), constructors.get(i),
                  constructors.subList(i + 1, constructors.size()))
                  : otherwiseMatching.stepTypeSpec(adt))
              : Stream.empty()

          ).collect(Collectors.toList()),

          initialCasesStepField.build(),

          matchFactory.build()));

    });
  }

  static MethodSpec.Builder constantMatchMethodBuilder(AlgebraicDataType adt, DataConstructor currentConstructor) {

    NameAllocator nameAllocator = new NameAllocator();
    String argName = uncapitalize(adt.matchMethod().returnTypeVariable().toString());
    return MethodSpec.methodBuilder(currentConstructor.name() + '_')
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addParameter(TypeName.get(adt.matchMethod().returnTypeVariable()), nameAllocator.newName(argName))
        .addStatement("return this.$L(($L) -> $L)", currentConstructor.name(),
            Utils.asLambdaParametersString(currentConstructor.arguments(), currentConstructor.typeRestrictions(), nameAllocator),
            argName);
  }

  static Stream<TypeVariable> matcherVariables(AlgebraicDataType adt) {

    return Stream.concat(adt.typeConstructor().typeVariables().stream(), Stream.of(adt.matchMethod().returnTypeVariable()));
  }

}
