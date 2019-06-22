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
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataConstructions;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;

import static java.util.stream.Collectors.toList;

final class FactoryDerivator implements Derivator {

  private final DeriveUtils   utils;
  private final CataDerivator cataDerivator;

  FactoryDerivator(DeriveUtils utils) {
    this.utils = utils;
    cataDerivator = new CataDerivator(utils);
  }

  @Override
  public DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adtModel) {
    return DeriveResult.result(DataConstructions.caseOf(adtModel.dataConstruction())
        .multipleConstructors(
            MultipleConstructorsSupport.cases()
                .visitorDispatch(
                    (visitorParam, visitorType, constructors) -> cataDerivator.visitorIsObjectAlgebra(adtModel)
                        ? factory(adtModel, visitorType, constructors)
                        : DerivedCodeSpec.none())
                .otherwise_(DerivedCodeSpec.none()))
        .otherwise_(DerivedCodeSpec.none()));
  }

  private DerivedCodeSpec factory(AlgebraicDataType adt, DeclaredType visitorType,
      List<DataConstructor> constructors) {

    String methodName = "factory";

    DeclaredType factoryType = utils.resolve(visitorType,
        tv -> Optional.of(utils.types().isSameType(adt.matchMethod().returnTypeVariable(), tv)
            ? adt.typeConstructor().declaredType()
            : tv));

    TypeName factoryTypeName = TypeName.get(factoryType);

    CodeBlock initializer = CodeBlock.of("new $L<>($L)", MapperDerivator.lambdaVisitorClassName(visitorType),
        constructors.stream()
            .map(c -> CodeBlock.of("$T::$L", adt.deriveConfig().targetClass().className(), c.name()))
            .collect(CodeBlock.joining(", ")));

    MethodSpec.Builder factory = MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(
            adt.typeConstructor()
                .typeVariables()
                .stream()
                .map(TypeVariableName::get)
                .collect(toList()))
        .returns(factoryTypeName);

    final FieldSpec singleton;

    if (adt.typeConstructor().typeVariables().isEmpty()) {
      singleton = FieldSpec.builder(factoryTypeName, methodName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
          .initializer(initializer)
          .build();

      factory.addStatement("return $L", methodName);
    } else {
      singleton = FieldSpec.builder(TypeName.get(utils.types().erasure(factoryType)), methodName,
          Modifier.PRIVATE, Modifier.STATIC)
          .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "rawtypes").build())
          .build();

      factory.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
          .addMember("value", "{$S, $S}", "rawtypes", "unchecked")
          .build())
          .addStatement("$1T _$2L = $2L", factoryTypeName, methodName)
          .beginControlFlow("if (_$L == null)", methodName)
          .addStatement("$1L = _$1L = $2L", methodName, initializer)
          .endControlFlow()
          .addStatement("return _$L", methodName);
    }
    return DerivedCodeSpec.codeSpec(singleton, factory.build());
  }
}
