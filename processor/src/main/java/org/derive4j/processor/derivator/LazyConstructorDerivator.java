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
package org.derive4j.processor.derivator;

import com.squareup.javapoet.*;
import org.derive4j.processor.Utils;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DeriveContext;
import org.derive4j.processor.api.model.TypeConstructor;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.stream.Collectors;

import static org.derive4j.processor.Utils.optionalAsStream;
import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.api.DerivedCodeSpec.codeSpec;
import static org.derive4j.processor.api.DerivedCodeSpec.none;
import static org.derive4j.processor.derivator.StrictConstructorDerivator.*;

public final class LazyConstructorDerivator {


  public static DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {

    // skip constructors for enums
    if (adt.typeConstructor().declaredType().asElement().getKind() == ElementKind.ENUM) {
      return result(none());
    }

    TypeConstructor typeConstructor = adt.typeConstructor();
    TypeElement lazyTypeElement = FlavourImpl.findF0(deriveContext.flavour(), deriveUtils.elements());
    TypeName lazyArgTypeName = TypeName.get(deriveUtils.types().getDeclaredType(lazyTypeElement, typeConstructor.declaredType()));
    String lazyArgName = Utils.uncapitalize(typeConstructor.typeElement().getSimpleName());
    TypeName typeName = TypeName.get(typeConstructor.declaredType());

    List<TypeVariableName> typeVariableNames = adt.typeConstructor().typeVariables().stream()
        .map(TypeVariableName::get).collect(Collectors.toList());

    String className = "Lazy";
    TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(className)
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .addTypeVariables(typeVariableNames)
        .addField(FieldSpec.builder(TypeName.BOOLEAN, "initialized", Modifier.PRIVATE, Modifier.VOLATILE).build())
        .addField(FieldSpec.builder(lazyArgTypeName, "expression", Modifier.PRIVATE, Modifier.FINAL).build())
        .addField(FieldSpec.builder(typeName, "evaluation", Modifier.PRIVATE).build())
        .addMethod(MethodSpec.constructorBuilder()
            .addParameter(ParameterSpec.builder(lazyArgTypeName, lazyArgName).build())
            .addStatement("this.expression = $N", lazyArgName).build())
        .addMethod(
            MethodSpec.methodBuilder("eval")
                .addModifiers(Modifier.PRIVATE)
                .returns(typeName)
                .addCode(CodeBlock.builder()
                    .beginControlFlow("if (!initialized)")
                    .beginControlFlow("synchronized (this)")
                    .beginControlFlow("if (!initialized)")
                    .addStatement("$T _evaluation = expression.$L()", typeName, Utils.getAbstractMethods(lazyTypeElement.getEnclosedElements()).get(0).getSimpleName())
                    .addStatement("evaluation = _evaluation")
                    .addStatement("initialized = true")
                    .addStatement("return _evaluation")
                    .endControlFlow().endControlFlow().endControlFlow()
                    .addStatement("return evaluation")
                    .build())
                .build())
        .addMethod(
            Utils.overrideMethodBuilder(adt.matchMethod().element())
                .addStatement("return eval().$L($L)", adt.matchMethod().element().getSimpleName(), Utils.asArgumentsStringOld(adt.matchMethod().element().getParameters()))
                .build());

    if (adt.typeConstructor().declaredType().asElement().getKind() == ElementKind.INTERFACE) {
      typeSpecBuilder.addSuperinterface(typeName);
    } else {
      typeSpecBuilder.superclass(typeName);
    }

    typeSpecBuilder.addMethods(optionalAsStream(
        findAbstractEquals(typeConstructor.typeElement(), deriveUtils.elements())
            .map(equals -> deriveUtils.overrideMethodBuilder(equals)
                .addStatement("return this.eval().equals($L)", equals.getParameters().get(0).getSimpleName())
                .build())
    ).collect(Collectors.toList()));

    typeSpecBuilder.addMethods(optionalAsStream(
        findAbstractHashCode(typeConstructor.typeElement(), deriveUtils.elements())
            .map(hashCode -> deriveUtils.overrideMethodBuilder(hashCode)
                .addStatement("return this.eval().hashCode()")
                .build())
    ).collect(Collectors.toList()));

    typeSpecBuilder.addMethods(optionalAsStream(
        findAbstractToString(typeConstructor.typeElement(), deriveUtils.elements())
            .map(toString -> deriveUtils.overrideMethodBuilder(toString)
                .addStatement("return this.eval().toString()")
                .build())
    ).collect(Collectors.toList()));

    return result(codeSpec(
        typeSpecBuilder.build(),
        MethodSpec.methodBuilder("lazy")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addTypeVariables(typeConstructor.typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
            .addParameter(lazyArgTypeName, lazyArgName)
            .returns(typeName)
            .addStatement("return new $L$L($L)", className, typeVariableNames.isEmpty() ? "" : "<>", lazyArgName).build()
    ));

  }

}
