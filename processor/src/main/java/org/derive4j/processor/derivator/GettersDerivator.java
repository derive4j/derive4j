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

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.derive4j.processor.Utils;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataArgument;
import org.derive4j.processor.api.model.DataConstructions;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.DeriveContext;
import org.derive4j.processor.api.model.MultipleConstructorsSupport;
import org.derive4j.processor.api.model.TypeRestriction;

import static org.derive4j.processor.Utils.joinStringsAsArguments;
import static org.derive4j.processor.api.DeriveResult.result;

public final class GettersDerivator {

  public static DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return result(
       adt.fields().stream()
          .map(da -> deriveGetter(da, adt, deriveContext, deriveUtils))
          .reduce(DerivedCodeSpec.none(), DerivedCodeSpec::append)
    );
  }

  private static DerivedCodeSpec deriveGetter(DataArgument field, AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return isLens(field, adt.dataConstruction().constructors())
           ? generateLensGetter(field, adt, deriveUtils, deriveContext)
           : generateOptionalGetter(field, adt, deriveContext, deriveUtils);
  }

  private static DerivedCodeSpec generateOptionalGetter(DataArgument field, AlgebraicDataType adt, DeriveContext deriveContext,
     DeriveUtils deriveUtils) {

    String arg = asParameterName(adt);

    FlavourImpl.OptionType optionType = FlavourImpl.findOptionType(deriveContext.flavour(), deriveUtils.elements());

    DeclaredType returnType = deriveUtils.types()
       .getDeclaredType(optionType.typeElement(), field.type().accept(Utils.asBoxedType, deriveUtils.types()));

    return DataConstructions
       .cases()
       .multipleConstructors(
          MultipleConstructorsSupport.cases()
             .visitorDispatch(
                (visitorParam, visitorType, constructors) -> visitorDispatchOptionalGetterImpl(deriveUtils, deriveContext, optionType, adt,
                   visitorType, constructors, arg, field, returnType))
             .functionsDispatch(constructors -> functionsDispatchOptionalGetterImpl(optionType, adt, arg, constructors, field, returnType))
       )
       .otherwise(() -> DerivedCodeSpec.none())
       .apply(adt.dataConstruction());
  }

  private static DerivedCodeSpec functionsDispatchOptionalGetterImpl(FlavourImpl.OptionType optionType, AlgebraicDataType adt, String arg,
     List<DataConstructor> constructors, DataArgument field, DeclaredType returnType) {
    return DerivedCodeSpec.methodSpec(
       getterBuilder(adt, arg, field, returnType)
          .addCode(CodeBlock.builder()
             .add("return $L.$L(", arg, adt.matchMethod().element().getSimpleName())
             .add(optionalGetterLambdas(arg, optionType, constructors, field))
             .add(");")
             .build())
          .build()
    );
  }

  private static DerivedCodeSpec visitorDispatchOptionalGetterImpl(DeriveUtils deriveUtils, DeriveContext deriveContext,
     FlavourImpl.OptionType optionType, AlgebraicDataType adt, DeclaredType visitorType, List<DataConstructor> constructors, String arg,
     DataArgument field, DeclaredType returnType) {

    Function<TypeVariable, Optional<TypeMirror>> returnTypeArg = tv ->
       deriveUtils.types().isSameType(tv, adt.matchMethod().returnTypeVariable())
       ? Optional.of(returnType)
       : Optional.<TypeMirror>empty();

    Function<TypeVariable, Optional<TypeMirror>> otherTypeArgs = tv -> Optional
       .of(deriveUtils.elements().getTypeElement(Object.class.getName()).asType());

    FieldSpec getterField = FieldSpec.builder(TypeName.get(deriveUtils.resolve(deriveUtils.resolve(visitorType, returnTypeArg), otherTypeArgs)),
       Utils.uncapitalize(field.fieldName() + "Getter"))
       .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
       .initializer("$T.$L($L)",
          ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName()),
          MapperDerivator.visitorLambdaFactoryName(adt),
          optionalGetterLambdas(arg, optionType, constructors, field)).build();

    MethodSpec getter;

    if (adt.typeConstructor().typeVariables().isEmpty()) {
      getter = getterBuilder(adt, arg, field, returnType)
         .addStatement("return $L.$L($L)",
            arg,
            adt.matchMethod().element().getSimpleName(),
            getterField.name)
         .build();
    } else {
      getter = getterBuilder(adt, arg, field, returnType)
         .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "{$S, $S}", "unchecked", "rawtypes").build())
         .addStatement("return ($T) $L.$L(($T) $L)",
            TypeName.get(returnType),
            arg,
            adt.matchMethod().element().getSimpleName(),
            TypeName.get(deriveUtils.types().erasure(visitorType)),
            getterField.name)
         .build();

    }

    return DerivedCodeSpec.codeSpec(getterField, getter);
  }

  private static MethodSpec.Builder getterBuilder(AlgebraicDataType adt, String arg, DataArgument field, TypeMirror type) {
    return MethodSpec.methodBuilder("get" + Utils.capitalize(field.fieldName())).addModifiers(Modifier.PUBLIC, Modifier.STATIC)
       .addTypeVariables(adt.typeConstructor().typeVariables().stream()
          .map(TypeVariableName::get).collect(Collectors.toList()))
       .addParameter(TypeName.get(adt.typeConstructor().declaredType()), arg)
       .returns(TypeName.get(type));
  }

  private static CodeBlock optionalGetterLambdas(String arg, FlavourImpl.OptionType optionType, List<DataConstructor> constructors,
     DataArgument field) {
    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(arg);

    return constructors.stream()
       .map(constructor -> {
         CodeBlock.Builder caseImplBuilder = CodeBlock.builder().add("($L) -> $T.", Utils.joinStringsAsArguments(Stream.concat(
            constructor.arguments().stream().map(DataArgument::fieldName)
               .map(fn -> nameAllocator.clone().newName(fn, fn + " field")),
            constructor.typeRestrictions().stream().map(TypeRestriction::idFunction).map(DataArgument::fieldName)
               .map(fn -> nameAllocator.clone().newName(fn, fn + " field")))),
            ClassName.get(optionType.typeElement()));
         if (constructor.arguments().stream().anyMatch(da -> da.fieldName().equals(field.fieldName()))) {
           caseImplBuilder
              .add("$L($L)", optionType.someConstructor(), nameAllocator.clone().newName(field.fieldName(), field.fieldName() + " field"));
         } else {
           caseImplBuilder.add("$L()", optionType.noneConstructor());
         }
         return caseImplBuilder.build();
       })
       .reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build())
       .orElse(CodeBlock.builder().build());
  }

  private static DerivedCodeSpec generateLensGetter(DataArgument field, AlgebraicDataType adt, DeriveUtils deriveUtils, DeriveContext deriveContext) {

    String arg = asParameterName(adt);

    return DataConstructions.cases()
       .multipleConstructors(
          MultipleConstructorsSupport.cases()
             .visitorDispatch((visitorParam, visitorType, constructors) ->
                visitorDispatchLensGetterImpl(deriveUtils, deriveContext, adt, arg, visitorType, field))
             .functionsDispatch(constructors -> functionsDispatchLensGetterImpl(adt, arg, field))
       )
       .oneConstructor(constructor -> functionsDispatchLensGetterImpl(adt, arg, field))
       .noConstructor(() -> DerivedCodeSpec.none())
       .apply(adt.dataConstruction());
  }

  private static DerivedCodeSpec functionsDispatchLensGetterImpl(AlgebraicDataType adt, String arg, DataArgument field) {
    return DerivedCodeSpec.methodSpec(getterBuilder(adt, arg, field, field.type()).addStatement("return $L.$L($L)",
       arg,
       adt.matchMethod().element().getSimpleName(),
       lensGetterLambda(arg, adt, field)
    ).build());
  }

  private static DerivedCodeSpec visitorDispatchLensGetterImpl(DeriveUtils deriveUtils, DeriveContext deriveContext, AlgebraicDataType adt,
     String arg, DeclaredType visitorType, DataArgument field) {
    Function<TypeVariable, Optional<TypeMirror>> returnTypeArg = tv ->
       deriveUtils.types().isSameType(tv, adt.matchMethod().returnTypeVariable())
       ? Optional.of(field.type())
       : Optional.<TypeMirror>empty();

    Function<TypeVariable, Optional<TypeMirror>> otherTypeArgs = tv -> Optional
       .of(deriveUtils.elements().getTypeElement(Object.class.getName()).asType());

    FieldSpec getterField = FieldSpec.builder(TypeName.get(deriveUtils.resolve(deriveUtils.resolve(visitorType, returnTypeArg), otherTypeArgs)),
       Utils.uncapitalize(field.fieldName() + "Getter"))
       .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
       .initializer("$T.$L($L)",
          ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName()),
          MapperDerivator.visitorLambdaFactoryName(adt),
          lensGetterLambda(arg, adt, field)).build();

    final MethodSpec getter;

    if (adt.typeConstructor().typeVariables().isEmpty()) {
      getter = getterBuilder(adt, arg, field, field.type())
         .addStatement("return $L.$L($L)",
            arg,
            adt.matchMethod().element().getSimpleName(),
            getterField.name)
         .build();
    } else {

      getter = getterBuilder(adt, arg, field, field.type())
         .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "{$S, $S}", "unchecked", "rawtypes").build())
         .addStatement("return ($T) $L.$L(($T) $L)",
            TypeName.get(field.type()),
            arg,
            adt.matchMethod().element().getSimpleName(),
            TypeName.get(deriveUtils.types().erasure(visitorType)),
            getterField.name)
         .build();

    }

    return DerivedCodeSpec.codeSpec(getterField, getter);
  }

  private static String lensGetterLambda(String arg, AlgebraicDataType adt, DataArgument field) {
    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(arg);

    return joinStringsAsArguments(adt.dataConstruction().constructors().stream()
       .map(dc -> "(" + Utils.joinStringsAsArguments(Stream.concat(
          dc.arguments().stream().map(DataArgument::fieldName)
             .map(fn -> nameAllocator.clone().newName(fn, fn + " field")),
          dc.typeRestrictions().stream().map(TypeRestriction::idFunction).map(DataArgument::fieldName)
             .map(fn -> nameAllocator.clone().newName(fn, fn + " field")))) + ") -> "
          + nameAllocator.clone().newName(field.fieldName(), field.fieldName() + " field")));
  }

  private static String asParameterName(AlgebraicDataType adt) {
    return Utils.uncapitalize(adt.typeConstructor().typeElement().getSimpleName().toString());
  }

  private static boolean isLens(DataArgument field, List<DataConstructor> constructors) {
    return constructors.stream().allMatch(dc -> dc.arguments().stream().anyMatch(da -> da.fieldName().equals(field.fieldName())));
  }

}
