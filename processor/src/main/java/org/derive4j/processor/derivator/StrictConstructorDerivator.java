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
import org.derive4j.Visibility;
import org.derive4j.processor.Utils;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.TypeKindVisitor7;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.derive4j.processor.Utils.joinStrings;
import static org.derive4j.processor.Utils.optionalAsStream;
import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.api.DerivedCodeSpec.none;

public final class StrictConstructorDerivator {

  private static final List<Integer> PRIMES = Arrays.asList(23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173, 179, 181, 191, 193, 197, 199, 211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281, 283, 293, 307, 311, 313, 317, 331, 337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409, 419, 421, 431, 433, 439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499, 503, 509, 521, 523, 541, 547, 557, 563, 569, 571, 577, 587, 593, 599, 601, 607, 613, 617, 619, 631, 641, 643, 647, 653, 659, 661, 673, 677, 683, 691, 701, 709, 719, 727, 733, 739, 743, 751, 757, 761, 769, 773, 787, 797, 809, 811, 821, 823, 827, 829, 839, 853, 857, 859, 863, 877, 881, 883, 887, 907, 911, 919, 929, 937, 941, 947, 953, 967, 971, 977, 983, 991, 997);

  public static DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt, DeriveContext deriveContext, DeriveUtils deriveUtils) {

    DerivedCodeSpec codeSpec;
    // skip constructors for enums
    if (adt.typeConstructor().declaredType().asElement().getKind() == ElementKind.ENUM) {
      codeSpec = none();
    } else {
      codeSpec = DataConstructions.cases()
          .multipleConstructors(
              constructors -> constructors.constructors().stream()
                  .map(dc -> constructorSpec(adt, dc, deriveContext, deriveUtils))
                  .reduce(DerivedCodeSpec.none(), DerivedCodeSpec::append)
          )
          .oneConstructor(constructor -> constructorSpec(adt, constructor, deriveContext, deriveUtils))
          .noConstructor(() -> DerivedCodeSpec.none())
          .apply(adt.dataConstruction());
    }

    return result(codeSpec);

  }

  private static DerivedCodeSpec constructorSpec(AlgebraicDataType adt, DataConstructor constructor, DeriveContext deriveContext, DeriveUtils deriveUtils) {

    TypeName constructedType = TypeName.get(deriveUtils.resolve(
        adt.typeConstructor().declaredType(),
        deriveUtils.typeRestrictions(constructor.typeRestrictions())));

    List<TypeVariableName> typeVariableNames = adt.typeConstructor().typeVariables().stream()
        .filter(tv -> constructor.typeRestrictions().stream().map(TypeRestriction::restrictedTypeVariable)
            .noneMatch(rtv -> deriveUtils.types().isSameType(rtv, tv)))
        .map(TypeVariableName::get).collect(Collectors.toList());

    MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
        .addParameters(constructor.arguments().stream()
            .map(da -> ParameterSpec.builder(TypeName.get(da.type()), da.fieldName()).build())
            .collect(Collectors.toList()));

    for (DataArgument argument : constructor.arguments()) {
      constructorBuilder.addStatement("this.$N = $N", argument.fieldName(), argument.fieldName());
    }

    Function<TypeVariable, Optional<TypeMirror>> typesRestrictions = tv -> constructor.typeRestrictions().stream().filter(tr -> deriveUtils.types().isSameType(tr.restrictedTypeVariable(), tv)).map(TypeRestriction::refinementType).findFirst();

    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName(adt.typeConstructor().declaredType().asElement().getSimpleName().toString(), "Type Element");
    constructor.arguments().stream().filter(da -> da.type().getKind() == TypeKind.DECLARED)
        .forEach(da -> nameAllocator.newName(deriveUtils.types().asElement(da.type()).getSimpleName().toString(), da.fieldName()));

    String className = nameAllocator.newName(Utils.capitalize(constructor.name()), "Impl Element");
    TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(className)
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .addTypeVariables(typeVariableNames)
        .addFields(constructor.arguments().stream()
            .map(da -> FieldSpec.builder(TypeName.get(da.type()), da.fieldName())
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL).build())
            .collect(Collectors.toList()))
        .addMethod(constructorBuilder.build())
        .addMethod(
            deriveUtils.overrideMethodBuilder(adt.matchMethod().element(), typesRestrictions)
                .addStatement("return $L.$L($L)", constructor.deconstructor().visitorParam().getSimpleName(), constructor.deconstructor().visitorMethod().getSimpleName(),
                    Utils.asArgumentsString(constructor.arguments(), constructor.typeRestrictions())).build());
    if (adt.typeConstructor().declaredType().asElement().getKind() == ElementKind.INTERFACE) {
      typeSpecBuilder.addSuperinterface(constructedType);
    } else {
      typeSpecBuilder.superclass(constructedType);
    }

    typeSpecBuilder.addMethods(optionalAsStream(deriveEquals(adt, constructor, deriveContext, deriveUtils)).collect(Collectors.toList()));
    typeSpecBuilder.addMethods(optionalAsStream(deriveHashCode(adt, constructor, deriveContext, deriveUtils)).collect(Collectors.toList()));
    typeSpecBuilder.addMethods(optionalAsStream(deriveToString(adt, constructor, deriveContext, deriveUtils)).collect(Collectors.toList()));

    MethodSpec.Builder factory = MethodSpec.methodBuilder(constructor.name())
        .addModifiers(Modifier.STATIC)
        .addTypeVariables(typeVariableNames)
        .addParameters(constructor.arguments().stream()
            .map(da -> ParameterSpec.builder(TypeName.get(da.type()), da.fieldName()).build()).collect(Collectors.toList()))
        .returns(constructedType);

    if (deriveContext.visibility() != Visibility.Smart) {
      factory.addModifiers(Modifier.PUBLIC);
    }

    DerivedCodeSpec result;

    if (constructor.arguments().isEmpty()) {
      FieldSpec.Builder singleton = FieldSpec.builder(ClassName.get(adt.typeConstructor().typeElement()),
          constructor.name(),
          Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
          .initializer("new $L()", className);
      if (!adt.typeConstructor().typeVariables().isEmpty()) {
        singleton.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "rawtypes").build());
        factory.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build());
      }

      result = DerivedCodeSpec.codeSpec(typeSpecBuilder.build(),
          singleton.build(),
          factory
              .addStatement("return $L", constructor.name())
              .build());
    } else {
      result = DerivedCodeSpec.codeSpec(typeSpecBuilder.build(),
          factory
              .addStatement("return new $L$L($L)", className, typeVariableNames.isEmpty() ? "" : "<>", Utils.asArgumentsString(constructor.arguments()))
              .build()
      );
    }

    return result;

  }

  private static Optional<MethodSpec> deriveEquals(AlgebraicDataType adt, DataConstructor constructor, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return findAbstractEquals(adt.typeConstructor().typeElement(), deriveUtils.elements()).map(abstractEquals -> {
      VariableElement objectParam = abstractEquals.getParameters().get(0);

      CodeBlock lambdas = adt.dataConstruction().constructors().stream()
          .map(c ->
              CodeBlock.builder().add("($L) -> $L",
                  Utils.asLambdaParametersString(c.arguments(), c.typeRestrictions()),
                  c.name().equals(constructor.name())
                      ? constructor.arguments().stream().map(da -> equalityTest(da)).reduce((s1, s2) -> s1 + " && " + s2)
                      .orElse("true")
                      : "false").build())
          .reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build())
          .orElse(CodeBlock.builder().build());

      MethodSpec.Builder equalBuilder = deriveUtils.overrideMethodBuilder(abstractEquals, __ -> Optional.<TypeMirror>empty());
      if (!adt.typeConstructor().typeVariables().isEmpty()) {
        equalBuilder.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build());
      }

      return DataConstructions.cases()
          .multipleConstructors(
              MultipleConstructorsSupport.cases()
                  .visitorDispatch((visitorParam, visitorType, constructors) ->

                      equalBuilder.addStatement("return ($1L instanceof $2T) && (($3T) $1L).$4L($5T.$6L($7L))",
                          objectParam.getSimpleName().toString(),
                          TypeName.get(deriveUtils.types().erasure(adt.typeConstructor().declaredType())),
                          TypeName.get(deriveUtils.resolve(adt.typeConstructor().declaredType(), tv -> constructor.typeRestrictions().stream()
                              .filter(tr -> deriveUtils.types().isSameType(tr.restrictedTypeVariable(), tv))
                              .map(TypeRestriction::refinementType).findFirst())),
                          adt.matchMethod().element().getSimpleName(),
                          ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName()),
                          MapperDerivator.visitorLambdaFactoryName(adt),
                          lambdas)
                          .build()


                  )
                  .functionsDispatch(constructors ->
                      equalBuilder.addStatement("return ($1L instanceof $2T) && (($3T) $1L).$4L($5L)",
                          objectParam.getSimpleName().toString(),
                          TypeName.get(deriveUtils.types().erasure(adt.typeConstructor().declaredType())),
                          TypeName.get(deriveUtils.resolve(adt.typeConstructor().declaredType(), tv -> constructor.typeRestrictions().stream()
                              .filter(tr -> deriveUtils.types().isSameType(tr.restrictedTypeVariable(), tv))
                              .map(TypeRestriction::refinementType).findFirst())),
                          adt.matchMethod().element().getSimpleName(),
                          lambdas)
                          .build()
                  )
          )
          .oneConstructor(c -> equalBuilder.addStatement("return ($1L instanceof $2T) && (($3T) $1L).$4L($5L)",
              objectParam.getSimpleName().toString(),
              TypeName.get(deriveUtils.types().erasure(adt.typeConstructor().declaredType())),
              TypeName.get(deriveUtils.resolve(adt.typeConstructor().declaredType(), tv -> c.typeRestrictions().stream()
                  .filter(tr -> deriveUtils.types().isSameType(tr.restrictedTypeVariable(), tv))
                  .map(TypeRestriction::refinementType).findFirst())),
              adt.matchMethod().element().getSimpleName(),
              lambdas)
              .build())
          .noConstructor(() -> {
            throw new IllegalArgumentException();
          })
          .apply(adt.dataConstruction());
    });
  }

  private static Optional<MethodSpec> deriveHashCode(AlgebraicDataType adt, DataConstructor constructor, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    int nbConstructors = adt.dataConstruction().constructors().size();
    int constructorIndex = IntStream.range(0, nbConstructors).filter(i -> adt.dataConstruction().constructors().get(i).name().equals(constructor.name())).findFirst().getAsInt();

    return findAbstractHashCode(adt.typeConstructor().typeElement(), deriveUtils.elements()).map(abstractHashCode ->
        deriveUtils.overrideMethodBuilder(abstractHashCode, __ -> Optional.<TypeMirror>empty())
            .addStatement("return $L$L$L",
                IntStream.range(0, constructor.arguments().size() - 1).mapToObj(__ -> "(").collect(Collectors.joining()),
                PRIMES.get(constructorIndex),
                joinStrings(constructor.arguments().stream().map(da -> " + " + hascode(da)), ") * " + PRIMES.get(constructorIndex)))
            .build());

  }

  private static Optional<MethodSpec> deriveToString(AlgebraicDataType adt, DataConstructor constructor, DeriveContext deriveContext, DeriveUtils deriveUtils) {
    return findAbstractToString(adt.typeConstructor().typeElement(), deriveUtils.elements()).map(abstractToString -> {
      MethodSpec.Builder methodBuilder = deriveUtils.overrideMethodBuilder(abstractToString, __ -> Optional.<TypeMirror>empty());
      if (constructor.arguments().isEmpty()) {
        methodBuilder.addStatement("return \"$L()\"",
            constructor.name());
      } else {
        methodBuilder.addStatement("return $S + $L + $S",
            constructor.name() + "(",
            joinStrings(constructor.arguments().stream().map(da -> toString(da)), " + \", \" + "),
            ")");

      }
      return methodBuilder.build();
    });
  }

  private static String equalityTest(DataArgument da) {
    String thisField = "this." + da.fieldName();
    return da.type().accept(new TypeKindVisitor7<String, String>() {

      @Override
      protected String defaultAction(final TypeMirror e, final String p) {
        return "(" + thisField + " == " + p + ")";
      }

      @Override
      public String visitArray(final ArrayType t, final String p) {
        return "Arrays.equal(" + thisField + ", " + p + ")";
      }

      @Override
      public String visitDeclared(final DeclaredType t, final String p) {
        return t.asElement().getKind() == ElementKind.ENUM ? defaultAction(t, p) : thisField + ".equals(" + p + ")";
      }

      @Override
      public String visitPrimitiveAsDouble(final PrimitiveType t, final String p) {
        return "(Double.doubleToLongBits(" + thisField + ") == Double.doubleToLongBits(" + p + "))";
      }

      @Override
      public String visitPrimitiveAsFloat(final PrimitiveType t, final String p) {
        return "(Float.floatToIntBits(" + thisField + ") == Float.floatToIntBits(" + p + "))";
      }

    }, da.fieldName());
  }

  private static String hascode(DataArgument da) {
    return da.type().accept(new TypeKindVisitor7<String, String>() {

      @Override
      protected String defaultAction(final TypeMirror e, final String p) {
        return p + ".hashCode()";
      }

      @Override
      public String visitArray(final ArrayType t, final String p) {
        return "Arrays.hashCode(" + p + ")";
      }

      @Override
      public String visitPrimitiveAsBoolean(PrimitiveType t, String p) {
        return "Boolean.hashCode(" + p + ")";
      }

      @Override
      public String visitPrimitiveAsDouble(final PrimitiveType t, final String p) {
        return "Double.hashCode(" + p + ")";
      }

      @Override
      public String visitPrimitiveAsFloat(final PrimitiveType t, final String p) {
        return "Float.hashCode(" + p + ")";
      }

      @Override
      public String visitPrimitiveAsByte(PrimitiveType t, String p) {
        return "Byte.hashCode(" + p + ")";
      }

      @Override
      public String visitPrimitiveAsChar(PrimitiveType t, String p) {
        return "Character.hashCode(" + p + ")";
      }

      @Override
      public String visitPrimitiveAsInt(PrimitiveType t, String p) {
        return p;
      }

      @Override
      public String visitPrimitiveAsLong(PrimitiveType t, String p) {
        return "Long.hashCode(" + p + ")";
      }

      @Override
      public String visitPrimitiveAsShort(PrimitiveType t, String p) {
        return "Short.hashCode(" + p + ")";
      }
    }, "this." + da.fieldName());
  }


  private static String toString(DataArgument da) {
    return da.type().accept(new TypeKindVisitor7<String, String>() {

      @Override
      protected String defaultAction(final TypeMirror e, final String p) {
        return p;
      }

      @Override
      public String visitArray(final ArrayType t, final String p) {
        return "Arrays.toString(" + p + ")";
      }

    }, "this." + da.fieldName());
  }

  static Optional<ExecutableElement> findAbstractEquals(TypeElement typeElement, Elements elements) {

    TypeElement object = elements.getTypeElement(Object.class.getName());
    List<ExecutableElement> objectMethods = Utils.getMethods(object.getEnclosedElements()).collect(Collectors.toList());
    ExecutableElement equals = objectMethods.stream().filter(e -> e.getSimpleName().toString().equals("equals")).findFirst().get();

    return Utils.getAbstractMethods(typeElement.getEnclosedElements()).stream().filter(e -> elements.overrides(e, equals, object)).findFirst();
  }

  static Optional<ExecutableElement> findAbstractToString(TypeElement typeElement, Elements elements) {

    TypeElement object = elements.getTypeElement(Object.class.getName());
    List<ExecutableElement> objectMethods = Utils.getMethods(object.getEnclosedElements()).collect(Collectors.toList());
    ExecutableElement toString = objectMethods.stream().filter(e -> e.getSimpleName().toString().equals("toString")).findFirst().get();

    return Utils.getAbstractMethods(typeElement.getEnclosedElements()).stream().filter(e -> elements.overrides(e, toString, object)).findFirst();
  }

  static Optional<ExecutableElement> findAbstractHashCode(TypeElement typeElement, Elements elements) {

    TypeElement object = elements.getTypeElement(Object.class.getName());
    List<ExecutableElement> objectMethods = Utils.getMethods(object.getEnclosedElements()).collect(Collectors.toList());
    ExecutableElement hashCode = objectMethods.stream().filter(e -> e.getSimpleName().toString().equals("hashCode")).findFirst().get();

    return Utils.getAbstractMethods(typeElement.getEnclosedElements()).stream().filter(e -> elements.overrides(e, hashCode, object)).findFirst();
  }
}
