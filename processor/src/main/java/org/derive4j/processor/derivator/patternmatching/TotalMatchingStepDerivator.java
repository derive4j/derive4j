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

import com.squareup.javapoet.CodeBlock;
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
import org.derive4j.processor.Utils;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataConstructions;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;
import org.derive4j.processor.derivator.MapperDerivator;

import static org.derive4j.processor.Utils.joinStringsAsArguments;
import static org.derive4j.processor.Utils.uncapitalize;
import static org.derive4j.processor.derivator.MapperDerivator.mapperFieldName;
import static org.derive4j.processor.derivator.patternmatching.OtherwiseMatchingStepDerivator.otherwiseMatcherTypeName;
import static org.derive4j.processor.derivator.patternmatching.PartialMatchingStepDerivator.superClass;

public class TotalMatchingStepDerivator {

  private final DeriveUtils deriveUtils;
  private final MapperDerivator mapperDerivator;
  private final PartialMatchingStepDerivator partialMatching;

  TotalMatchingStepDerivator(DeriveUtils deriveUtils) {
    this.deriveUtils = deriveUtils;
    mapperDerivator = new MapperDerivator(deriveUtils);
    partialMatching = new PartialMatchingStepDerivator(deriveUtils);
  }

  TypeSpec stepTypeSpec(AlgebraicDataType adt, List<DataConstructor> previousConstructors, DataConstructor currentConstructor,
      List<DataConstructor> nextConstructors) {

    TypeVariableName returnTypeVarName = TypeVariableName.get(adt.matchMethod().returnTypeVariable());

    TypeSpec.Builder totalMatchBuilder = TypeSpec.classBuilder(totalMatchBuilderClassName(currentConstructor))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()));

    MethodSpec.Builder currentConstructorTotalMatchMethod = MethodSpec.methodBuilder(currentConstructor.name())
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addParameter(mapperDerivator.mapperTypeName(adt, currentConstructor), mapperFieldName(currentConstructor));

    MethodSpec.Builder currentConstructorTotalMatchConstantMethod = PatternMatchingDerivator.constantMatchMethodBuilder(adt,
        currentConstructor);

    final Stream<MethodSpec> partialMatchMethods;

    if (previousConstructors.isEmpty()) {

      currentConstructorTotalMatchMethod.addTypeVariable(returnTypeVarName);
      currentConstructorTotalMatchConstantMethod.addTypeVariable(returnTypeVarName);

      totalMatchBuilder.addMethod(MethodSpec.constructorBuilder().build());

      partialMatchMethods = IntStream.rangeClosed(1, nextConstructors.size())
          .mapToObj(i -> partialMatching.partialMatchMethodBuilder(adt, previousConstructors, i, nextConstructors.get(i - 1),
              superClass(adt, nextConstructors.subList(i, nextConstructors.size()))))
          .flatMap(Function.identity())
          .map(mb -> mb.addTypeVariable(returnTypeVarName).build());

    } else {

      totalMatchBuilder.addTypeVariable(returnTypeVarName)
          .superclass(superClass(adt, nextConstructors))
          .addMethod(MethodSpec.constructorBuilder()
              .addParameters(previousConstructors.stream()
                  .map(dc -> ParameterSpec.builder(mapperDerivator.mapperTypeName(adt, dc), mapperFieldName(dc)).build())
                  .collect(Collectors.toList()))
              .addStatement("super($L)", joinStringsAsArguments(
                  Stream.concat(previousConstructors.stream().map(MapperDerivator::mapperFieldName), Stream.of("null"))))
              .build());

      partialMatchMethods = Stream.empty();
    }

    if (nextConstructors.isEmpty()) {

      TypeName returnType = TypeName.get(deriveUtils.types()
          .getDeclaredType(deriveUtils.function1Model(adt.deriveConfig().flavour()).samClass(),
              adt.typeConstructor().declaredType(), adt.matchMethod().returnTypeVariable()));

      currentConstructorTotalMatchMethod.returns(returnType)
          .addCode(DataConstructions.cases()
              .multipleConstructors(MultipleConstructorsSupport.cases()
                  .visitorDispatch((visitorParam, visitorType, constructors) -> vistorDispatchImpl(adt, visitorType, visitorParam,
                      previousConstructors, currentConstructor))
                  .functionsDispatch(constructors1 -> functionDispatchImpl(adt, previousConstructors, currentConstructor)))
              .oneConstructor(constructor -> oneConstructorImpl(currentConstructor, adt))
              .noConstructor(() -> {
                throw new IllegalArgumentException();
              })
              .apply(adt.dataConstruction()));

      currentConstructorTotalMatchConstantMethod.returns(returnType);

    } else {

      DataConstructor firstNextConstructor = nextConstructors.get(0);

      ParameterizedTypeName returnType = ParameterizedTypeName.get(
          adt.deriveConfig().targetClass().className().nestedClass(totalMatchBuilderClassName(firstNextConstructor)),
          PatternMatchingDerivator.matcherVariables(adt).map(TypeName::get).toArray(TypeName[]::new));

      ParameterizedTypeName otherwiseMatcherTypeName = otherwiseMatcherTypeName(adt);

      currentConstructorTotalMatchMethod.returns(returnType)
          .addStatement("return new $L<>($L)", totalMatchBuilderClassName(firstNextConstructor), Stream.concat(
              previousConstructors.stream()
                  .map(dc -> "((" + otherwiseMatcherTypeName.toString() + ") this)." + mapperFieldName(dc)),
              Stream.of(mapperFieldName(currentConstructor))).reduce((s1, s2) -> s1 + ", " + s2).orElse(""));

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
    nameAllocator.newName(mapperFieldName(currentConstructor), mapperFieldName(currentConstructor));

    for (DataConstructor dc : previousConstructors) {
      nameAllocator.newName(mapperFieldName(dc), mapperFieldName(dc));
      codeBlock.addStatement("$1T $2L = super.$2L", mapperDerivator.mapperTypeName(adt, dc), mapperFieldName(dc));
    }

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    nameAllocator.newName(adtLambdaParam, "adt var");

    return codeBlock.addStatement("return $1L -> $1L.$2L($3L)", nameAllocator.get("adt var"),
        adt.matchMethod().element().getSimpleName(), joinStringsAsArguments(
            Stream.concat(previousConstructors.stream().map(MapperDerivator::mapperFieldName),
                Stream.of(mapperFieldName(currentConstructor))))).build();
  }

  private CodeBlock vistorDispatchImpl(AlgebraicDataType adt, DeclaredType visitorType, VariableElement visitorParam,
      List<DataConstructor> previousConstructors, DataConstructor currentConstructor) {

    String visitorVarName = visitorParam.getSimpleName().toString();
    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(mapperFieldName(currentConstructor), "case arg");
    nameAllocator.newName(adtLambdaParam, "adt var");
    nameAllocator.newName(visitorVarName, "visitor var");

    ParameterizedTypeName otherwiseMatcherTypeName = otherwiseMatcherTypeName(adt);

    return CodeBlock.builder()
        .addStatement("$T $L = $T.$L($L)", TypeName.get(visitorType), nameAllocator.get("visitor var"),
            adt.deriveConfig().targetClass().className(), MapperDerivator.visitorLambdaFactoryName(adt), joinStringsAsArguments(
                Stream.concat(previousConstructors.stream()
                        .map(dc -> "((" + otherwiseMatcherTypeName.toString() + ") this)." + mapperFieldName(dc)),
                    Stream.of(mapperFieldName(currentConstructor)))))
        .addStatement("return $1L -> $1L.$2L($3L)", nameAllocator.get("adt var"), adt.matchMethod().element().getSimpleName(),
            nameAllocator.get("visitor var"))
        .build();
  }

  static String totalMatchBuilderClassName(DataConstructor currentConstructor) {

    return "TotalMatcher_" + Utils.capitalize(currentConstructor.name());
  }

  private static CodeBlock oneConstructorImpl(DataConstructor currentConstructor, AlgebraicDataType adt) {

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(mapperFieldName(currentConstructor), mapperFieldName(currentConstructor));

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());
    nameAllocator.newName(adtLambdaParam, "adt var");

    return CodeBlock.builder()
        .addStatement("return $1L -> $1L.$2L($3L)", nameAllocator.get("adt var"), adt.matchMethod().element().getSimpleName(),
            mapperFieldName(currentConstructor))
        .build();
  }
}
