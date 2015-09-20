/**
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
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.*;
import org.derive4j.processor.Utils;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.Utils.joinStringsAsArguments;

public final class GettersDerivator {

  public static DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return result(
        adt.fields().stream()
            .map(da -> deriveGetter(da, adt, deriveContext, deriveUtils))
            .reduce(DerivedCodeSpec.none(), Utils::appendCodeSpecs)
    );
  }

  private static DerivedCodeSpec deriveGetter(DataArgument field, AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return isLens(field, adt.dataConstruction().constructors()) ? generateLensGetter(field, adt, deriveUtils, deriveContext) : generateOptionalGetter(field, adt, deriveContext, deriveUtils);
  }

  private static DerivedCodeSpec generateOptionalGetter(DataArgument field, AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {


    String arg = Utils.uncapitalize(adt.typeConstructor().typeElement().getSimpleName().toString());

    Flavours.OptionType optionType = Flavours.findOptionType(deriveContext.deriveFlavour(), deriveUtils.elements());

    DeclaredType returnType = deriveUtils.types().getDeclaredType(optionType.typeElement(), field.type());

    MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder("get" + Utils.capitalize(field.fieldName())).addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(adt.typeConstructor().typeVariables().stream()
            .map(TypeVariableName::get).collect(Collectors.toList()))
        .addParameter(TypeName.get(adt.typeConstructor().declaredType()), arg)
        .returns(TypeName.get(returnType));

    return adt.dataConstruction().match(new DataConstruction.Cases<DerivedCodeSpec>() {
      @Override
      public DerivedCodeSpec multipleConstructors(DataConstructors constructors) {

        CodeBlock lambdas = constructors.constructors().stream()
            .map(constructor -> {
              CodeBlock.Builder caseImplBuilder = CodeBlock.builder().add("($L) -> $T.", Utils.asLambdaParametersString(constructor.arguments(), constructor.typeRestrictions()), ClassName.get(optionType.typeElement()));
              if (constructor.arguments().stream().anyMatch(da -> da.fieldName().equals(field.fieldName()))) {
                caseImplBuilder.add("$L($L)", optionType.someConstructor(), field.fieldName());
              } else {
                caseImplBuilder.add("$L()", optionType.noneConstructor());
              }
              return caseImplBuilder.build();
            })
            .reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build())
            .orElse(CodeBlock.builder().build());

        return constructors.match(new DataConstructors.Cases<DerivedCodeSpec>() {
          @Override
          public DerivedCodeSpec visitorDispatch(VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors) {

            Function<TypeVariable, Optional<TypeMirror>> returnTypeArg = tv ->
                deriveUtils.types().isSameType(tv, adt.matchMethod().returnTypeVariable())
                    ? Optional.of(deriveUtils.types().getDeclaredType(Flavours.findOptionType(deriveContext.deriveFlavour(), deriveUtils.elements()).typeElement(), field.type()))
                    : Optional.<TypeMirror>empty();

            Function<TypeVariable, Optional<TypeMirror>> otherTypeArgs = tv -> Optional.of(deriveUtils.elements().getTypeElement(Object.class.getName()).asType());

            String getterFieldName = field.fieldName() + "Getter";

            FieldSpec.Builder getterField = FieldSpec.builder(TypeName.get(deriveUtils.resolve(deriveUtils.resolve(visitorType, returnTypeArg), otherTypeArgs)), getterFieldName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.$L($L)",
                    ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName()),
                    MapperDerivator.visitorLambdaFactoryName(adt),
                    lambdas);

            if (adt.typeConstructor().typeVariables().isEmpty()) {
              getterBuilder
                  .addStatement("return $L.$L($L)",
                      arg,
                      adt.matchMethod().element().getSimpleName(),
                      getterFieldName);
            } else {


              getterBuilder
                  .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "{$S, $S}", "unchecked", "rawtypes").build())
                  .addStatement("return ($T) $L.$L(($T) $L)",
                      TypeName.get(returnType),
                      arg,
                      adt.matchMethod().element().getSimpleName(),
                      TypeName.get(deriveUtils.types().erasure(visitorType)),
                      getterFieldName);

            }

            return DerivedCodeSpec.codeSpec(

                getterField.build(),

                getterBuilder.build()

            );
          }

          @Override
          public DerivedCodeSpec functionsDispatch(List<DataConstructor> constructors) {
            CodeBlock.Builder implBuilder = CodeBlock.builder().add("return $L.$L(", arg, adt.matchMethod().element().getSimpleName());

            return DerivedCodeSpec.methodSpec(getterBuilder.addCode(implBuilder.add(lambdas).add(");").build()).build());
          }
        });
      }

      @Override
      public DerivedCodeSpec oneConstructor(DataConstructor constructor) {
        return DerivedCodeSpec.none();
      }

      @Override
      public DerivedCodeSpec noConstructor() {
        return DerivedCodeSpec.none();
      }
    });
  }


  private static DerivedCodeSpec generateLensGetter(DataArgument field, AlgebraicDataType adt, DeriveUtils deriveUtils, DeriveContext deriveContext) {

    String arg = Utils.uncapitalize(adt.typeConstructor().typeElement().getSimpleName().toString());

    MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder("get" + Utils.capitalize(field.fieldName())).addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(adt.typeConstructor().typeVariables().stream()
            .map(TypeVariableName::get).collect(Collectors.toList()))
        .addParameter(TypeName.get(adt.typeConstructor().declaredType()), arg)
        .returns(TypeName.get(field.type()));

    return adt.dataConstruction().match(new DataConstruction.Cases<DerivedCodeSpec>() {
      @Override
      public DerivedCodeSpec multipleConstructors(DataConstructors constructors) {

        String lambdas = joinStringsAsArguments(constructors.constructors().stream()
            .map(dc -> "(" + Utils.asLambdaParametersString(dc.arguments(), dc.typeRestrictions()) + ") -> " + field.fieldName()));

        return constructors.match(new DataConstructors.Cases<DerivedCodeSpec>() {
          @Override
          public DerivedCodeSpec visitorDispatch(VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors) {

            Function<TypeVariable, Optional<TypeMirror>> returnTypeArg = tv ->
                deriveUtils.types().isSameType(tv, adt.matchMethod().returnTypeVariable())
                    ? Optional.of(field.type())
                    : Optional.<TypeMirror>empty();

            Function<TypeVariable, Optional<TypeMirror>> otherTypeArgs = tv -> Optional.of(deriveUtils.elements().getTypeElement(Object.class.getName()).asType());

            String getterFieldName = Utils.uncapitalize(field.fieldName() + "Getter");

            FieldSpec.Builder getterField = FieldSpec.builder(TypeName.get(deriveUtils.resolve(deriveUtils.resolve(visitorType, returnTypeArg), otherTypeArgs)), getterFieldName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.$L($L)",
                    ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName()),
                    MapperDerivator.visitorLambdaFactoryName(adt),
                    lambdas);

            if (adt.typeConstructor().typeVariables().isEmpty()) {
              getterBuilder
                  .addStatement("return $L.$L($L)",
                      arg,
                      adt.matchMethod().element().getSimpleName(),
                      getterFieldName);
            } else {

              getterBuilder
                  .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "{$S, $S}", "unchecked", "rawtypes").build())
                  .addStatement("return ($T) $L.$L(($T) $L)",
                      TypeName.get(field.type()),
                      arg,
                      adt.matchMethod().element().getSimpleName(),
                      TypeName.get(deriveUtils.types().erasure(visitorType)),
                      getterFieldName);

            }

            return DerivedCodeSpec.codeSpec(

                getterField.build(),

                getterBuilder.build()

            );
          }

          @Override
          public DerivedCodeSpec functionsDispatch(List<DataConstructor> constructors) {
            return DerivedCodeSpec.methodSpec(getterBuilder.addStatement("return $L.$L($L)",
                arg,
                adt.matchMethod().element().getSimpleName(),
                lambdas
            ).build());
          }
        });
      }

      @Override
      public DerivedCodeSpec oneConstructor(DataConstructor constructor) {
        return DerivedCodeSpec.methodSpec(getterBuilder.addStatement("return $L.$L(($L) -> $L)",
            arg,
            adt.matchMethod().element().getSimpleName(),
            Utils.asArgumentsString(constructor.arguments(), constructor.typeRestrictions()),
            field.fieldName()
        ).build());
      }

      @Override
      public DerivedCodeSpec noConstructor() {
        return DerivedCodeSpec.none();
      }
    });
  }

  private static boolean isLens(DataArgument field, List<DataConstructor> constructors) {
    return constructors.stream().allMatch(dc -> dc.arguments().stream().anyMatch(da -> da.fieldName().equals(field.fieldName())));
  }

}
