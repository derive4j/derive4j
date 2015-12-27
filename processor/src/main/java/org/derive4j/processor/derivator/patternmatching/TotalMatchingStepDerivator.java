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
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
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
import org.derive4j.processor.api.model.DeriveContext;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;
import org.derive4j.processor.derivator.FlavourImpl;
import org.derive4j.processor.derivator.MapperDerivator;

import static org.derive4j.processor.Utils.getClassName;
import static org.derive4j.processor.Utils.joinStringsAsArguments;
import static org.derive4j.processor.Utils.uncapitalize;
import static org.derive4j.processor.derivator.MapperDerivator.mapperFieldName;
import static org.derive4j.processor.derivator.MapperDerivator.mapperTypeName;

public class TotalMatchingStepDerivator {

  static TypeSpec totalMatchingStepTypeSpec(AlgebraicDataType adt,
     List<DataConstructor> previousConstructors,
     DataConstructor currentConstructor,
     List<DataConstructor> nextConstructors,
     DeriveContext deriveContext, DeriveUtils deriveUtils) {

    TypeVariableName returnTypeVarName = TypeVariableName.get(adt.matchMethod().returnTypeVariable());

    TypeSpec.Builder totalMatchBuilder = TypeSpec.classBuilder(totalMatchBuilderClassName(currentConstructor))
       .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
       .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()));

    MethodSpec.Builder currentConstructorTotalMatchMethod = MethodSpec.methodBuilder(currentConstructor.name())
       .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
       .addParameter(mapperTypeName(adt, currentConstructor, deriveContext, deriveUtils), mapperFieldName(currentConstructor));

    MethodSpec.Builder currentConstructorTotalMatchConstantMethod = PatternMatchingDerivator.constantMatchMethodBuilder(adt, currentConstructor);

    final Stream<MethodSpec> partialMatchMethods;

    if (previousConstructors.isEmpty()) {

      currentConstructorTotalMatchMethod.addTypeVariable(returnTypeVarName);
      currentConstructorTotalMatchConstantMethod.addTypeVariable(returnTypeVarName);

      totalMatchBuilder.addMethod(MethodSpec.constructorBuilder()
         .addModifiers(Modifier.PRIVATE).build());

      partialMatchMethods = PatternMatchingDerivator
         .partialMatchMethodBuilders(adt, previousConstructors, nextConstructors, deriveContext, deriveUtils)
         .map(mb -> mb.addTypeVariable(returnTypeVarName).build());

    } else {

      totalMatchBuilder.addTypeVariable(returnTypeVarName)
         .superclass(ParameterizedTypeName.get(getClassName(deriveContext, OtherwiseMatchingStepDerivator.otherwiseBuilderClassName()),
            PatternMatchingDerivator.matcherVariables(adt).map(TypeName::get).toArray(TypeName[]::new)))
         .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameters(previousConstructors.stream()
               .map(dc -> ParameterSpec.builder(mapperTypeName(adt, dc, deriveContext, deriveUtils), mapperFieldName(dc)).build())
               .collect(Collectors.toList()))
            .addStatement("super($L)", joinStringsAsArguments(Stream.concat(
               previousConstructors.stream().map(dc -> mapperFieldName(dc)),
               IntStream.range(previousConstructors.size(), adt.dataConstruction().constructors().size()).mapToObj(__ -> "null")
            )))
            .build());

      partialMatchMethods = PatternMatchingDerivator
         .partialMatchMethodBuilders(adt, previousConstructors, nextConstructors, deriveContext, deriveUtils).map(MethodSpec.Builder::build);
    }

    if (nextConstructors.isEmpty()) {

      TypeName returnType = TypeName.get(deriveUtils.types().getDeclaredType(FlavourImpl.findF(deriveContext.flavour(), deriveUtils.elements()),
         adt.typeConstructor().declaredType(), adt.matchMethod().returnTypeVariable()));

      currentConstructorTotalMatchMethod
         .returns(returnType)
         .addCode(
            DataConstructions.cases()
               .multipleConstructors(MultipleConstructorsSupport.cases()
                  .visitorDispatch((visitorParam, visitorType, constructors) -> vistorDispatchImpl(deriveContext, adt, visitorType, visitorParam,
                     previousConstructors, currentConstructor))
                  .functionsDispatch(constructors1 -> functionDispatchImpl(deriveUtils, deriveContext, adt, previousConstructors, currentConstructor))
               )
               .oneConstructor(constructor -> oneConstructorImpl(currentConstructor, adt))
               .noConstructor(() -> {
                 throw new IllegalArgumentException();
               })
               .apply(adt.dataConstruction()));

      currentConstructorTotalMatchConstantMethod.returns(returnType);

    } else {

      DataConstructor firstNextConstructor = nextConstructors.get(0);

      ParameterizedTypeName returnType = ParameterizedTypeName.get(getClassName(deriveContext, totalMatchBuilderClassName(firstNextConstructor)),
         PatternMatchingDerivator.matcherVariables(adt).map(TypeName::get).toArray(TypeName[]::new));

      currentConstructorTotalMatchMethod
         .returns(returnType)
         .addStatement("return new $L<>($L)", totalMatchBuilderClassName(firstNextConstructor),
            Stream.concat(previousConstructors.stream().map(dc -> "super." + mapperFieldName(dc)), Stream.of(mapperFieldName(currentConstructor)))
               .reduce((s1, s2) -> s1 + ", " + s2).orElse(""));

      currentConstructorTotalMatchConstantMethod.returns(returnType);

    }

    return totalMatchBuilder
       .addMethod(currentConstructorTotalMatchMethod.build())
       .addMethod(currentConstructorTotalMatchConstantMethod.build())
       .addMethods(partialMatchMethods.collect(Collectors.toList()))
       .build();

  }

  private static CodeBlock oneConstructorImpl(DataConstructor currentConstructor, AlgebraicDataType adt) {
    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(mapperFieldName(currentConstructor), mapperFieldName(currentConstructor));

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());
    nameAllocator.newName(adtLambdaParam, "adt var");

    return CodeBlock.builder().addStatement(
       "return $1L -> $1L.$2L($3L)",
       nameAllocator.get("adt var"),
       adt.matchMethod().element().getSimpleName(),
       mapperFieldName(currentConstructor)
    ).build();
  }

  private static CodeBlock functionDispatchImpl(DeriveUtils deriveUtils, DeriveContext deriveContext, AlgebraicDataType adt,
     List<DataConstructor> previousConstructors, DataConstructor currentConstructor) {
    CodeBlock.Builder codeBlock = CodeBlock.builder();

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(mapperFieldName(currentConstructor), mapperFieldName(currentConstructor));

    for (DataConstructor dc : previousConstructors) {
      nameAllocator.newName(mapperFieldName(dc), mapperFieldName(dc));
      codeBlock.addStatement("$1T $2L = super.$2L",
         mapperTypeName(adt, dc, deriveContext, deriveUtils),
         mapperFieldName(dc)
      );
    }

    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    nameAllocator.newName(adtLambdaParam, "adt var");

    return codeBlock
       .addStatement("return $1L -> $1L.$2L($3L)",
          nameAllocator.get("adt var"),
          adt.matchMethod().element().getSimpleName(),
          joinStringsAsArguments(Stream.concat(previousConstructors.stream().map(dc -> mapperFieldName(dc)),
             Stream.of(mapperFieldName(currentConstructor)))))
       .build();
  }

  private static CodeBlock vistorDispatchImpl(DeriveContext deriveContext, AlgebraicDataType adt, DeclaredType visitorType,
     VariableElement visitorParam, List<DataConstructor> previousConstructors, DataConstructor currentConstructor) {
    String visitorVarName = visitorParam.getSimpleName().toString();
    String adtLambdaParam = uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(mapperFieldName(currentConstructor), "case arg");
    nameAllocator.newName(adtLambdaParam, "adt var");
    nameAllocator.newName(visitorVarName, "visitor var");

    return CodeBlock.builder()
       .addStatement("$T $L = $T.$L($L)",
          TypeName.get(visitorType),
          nameAllocator.get("visitor var"),
          ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName()),
          MapperDerivator.visitorLambdaFactoryName(adt),
          joinStringsAsArguments(Stream.concat(previousConstructors.stream().map(dc -> "super." + mapperFieldName(dc)),
             Stream.of(mapperFieldName(currentConstructor)))))
       .addStatement("return $1L -> $1L.$2L($3L)",
          nameAllocator.get("adt var"),
          adt.matchMethod().element().getSimpleName(),
          nameAllocator.get("visitor var")
       )
       .build();
  }

  static String totalMatchBuilderClassName(DataConstructor currentConstructor) {
    return "TotalMatchBuilder" + Utils.capitalize(currentConstructor.name());
  }
}
