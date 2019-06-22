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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.SamInterface;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.TypeConstructor;

import static org.derive4j.processor.Utils.optionalAsStream;
import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.api.DerivedCodeSpec.codeSpec;
import static org.derive4j.processor.api.DerivedCodeSpec.none;

final class LazyConstructorDerivator implements Derivator {

  private final DeriveUtils                deriveUtils;
  private final StrictConstructorDerivator strictDerivator;

  LazyConstructorDerivator(DeriveUtils deriveUtils) {
    this.deriveUtils = deriveUtils;
    strictDerivator = new StrictConstructorDerivator(deriveUtils);
  }

  @Override
  public DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt) {

    // skip constructors for enums
    if (adt.typeConstructor().declaredType().asElement().getKind() == ElementKind.ENUM) {
      return result(none());
    }

    TypeConstructor typeConstructor = adt.typeConstructor();
    SamInterface f0 = deriveUtils.function0Model(adt.deriveConfig().flavour());
    TypeElement lazyTypeElement = f0.samClass();
    TypeName lazyArgTypeName = TypeName
        .get(deriveUtils.types().getDeclaredType(lazyTypeElement, typeConstructor.declaredType()));
    String lazyArgName = Utils.uncapitalize(typeConstructor.typeElement().getSimpleName());
    TypeName typeName = TypeName.get(typeConstructor.declaredType());

    List<TypeVariableName> typeVariableNames = adt.typeConstructor()
        .typeVariables()
        .stream()
        .map(TypeVariableName::get)
        .collect(Collectors.toList());

    ClassName className = ClassName.bestGuess("Lazy");
    TypeName lazyTypeName = typeVariableNames.isEmpty()
        ? className
        : ParameterizedTypeName.get(className, typeVariableNames.toArray(new TypeName[0]));
    TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(className)
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .addTypeVariables(typeVariableNames)
        .addField(FieldSpec.builder(lazyArgTypeName, "expression", Modifier.PRIVATE, Modifier.VOLATILE).build())
        .addField(FieldSpec.builder(typeName, "evaluation", Modifier.PRIVATE).build())
        .addMethod(MethodSpec.constructorBuilder()
            .addParameter(ParameterSpec.builder(lazyArgTypeName, lazyArgName).build())
            .addStatement("this.expression = $N", lazyArgName)
            .build())
        .addMethod(MethodSpec.methodBuilder("_evaluate")
            .addModifiers(Modifier.PRIVATE, Modifier.SYNCHRONIZED)
            .returns(typeName)
            .addCode(CodeBlock.builder()
                .addStatement("$T lazy = this", lazyTypeName)
                .beginControlFlow("while (true)")
                .addStatement("$T expr = lazy.expression", lazyArgTypeName)
                .beginControlFlow("if (expr == null)")
                .addStatement("evaluation = lazy.evaluation", f0.sam())
                .addStatement("break")
                .endControlFlow()
                .beginControlFlow("else")
                .addStatement("$T eval = expr.$L", typeName, f0.sam())
                .beginControlFlow("if (eval instanceof $T)", className)
                .addStatement("lazy = ($T) eval", lazyTypeName)
                .endControlFlow()
                .beginControlFlow("else")
                .addStatement("evaluation = eval")
                .addStatement("break")
                .endControlFlow()
                .endControlFlow()
                .endControlFlow()
                .addStatement("expression = null")
                .addStatement("return evaluation")
                .build())
            .build())
        .addMethod(Utils.overrideMethodBuilder(adt.matchMethod().element())
            .addStatement("return (this.expression == null ? this.evaluation : _evaluate()).$L($L)",
                adt.matchMethod().element().getSimpleName(),
                Utils.asArgumentsStringOld(adt.matchMethod().element().getParameters()))
            .build());

    if (adt.typeConstructor().declaredType().asElement().getKind() == ElementKind.INTERFACE) {
      typeSpecBuilder.addSuperinterface(typeName);
    } else {
      typeSpecBuilder.superclass(typeName);
    }

    typeSpecBuilder.addMethods(optionalAsStream(strictDerivator.findAbstractEquals(typeConstructor.typeElement())
        .map(equals -> deriveUtils.overrideMethodBuilder(equals, adt.typeConstructor().declaredType())
            .addStatement("return (this.expression == null ? this.evaluation : _evaluate()).equals($L)",
                equals.getParameters().get(0).getSimpleName())
            .build())).collect(Collectors.toList()));

    typeSpecBuilder.addMethods(optionalAsStream(strictDerivator.findAbstractHashCode(typeConstructor.typeElement())
        .map(hashCode -> deriveUtils.overrideMethodBuilder(hashCode, adt.typeConstructor().declaredType())
            .addStatement("return (this.expression == null ? this.evaluation : _evaluate()).hashCode()")
            .build())).collect(Collectors.toList()));

    typeSpecBuilder.addMethods(optionalAsStream(strictDerivator.findAbstractToString(typeConstructor.typeElement())
        .map(toString -> deriveUtils.overrideMethodBuilder(toString, adt.typeConstructor().declaredType())
            .addStatement("return (this.expression == null ? this.evaluation : _evaluate()).toString()")
            .build())).collect(Collectors.toList()));

    return result(codeSpec(typeSpecBuilder.build(),
        MethodSpec.methodBuilder("lazy")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addTypeVariables(
                typeConstructor.typeVariables().stream().map(TypeVariableName::get).collect(Collectors.toList()))
            .addParameter(lazyArgTypeName, lazyArgName)
            .returns(typeName)
            .addStatement("return new $L$L($L)", className, typeVariableNames.isEmpty() ? "" : "<>", lazyArgName)
            .build()));

  }

}
