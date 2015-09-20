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
package org.derive4j.processor;

import com.squareup.javapoet.*;
import org.derive4j.processor.api.DeriveMessage;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.*;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Utils {

  public static final TypeVisitor<Optional<DeclaredType>, Unit> asDeclaredType = new SimpleTypeVisitor8<Optional<DeclaredType>, Unit>(
      Optional.empty()) {
    @Override
    public Optional<DeclaredType> visitDeclared(final DeclaredType t, final Unit p) {
      return Optional.of(t);
    }
  };

  public static final TypeVisitor<Optional<TypeVariable>, Unit> asTypeVariable = new SimpleTypeVisitor8<Optional<TypeVariable>, Unit>(
      Optional.empty()) {
    @Override
    public Optional<TypeVariable> visitTypeVariable(final TypeVariable t, final Unit p) {
      return Optional.of(t);
    }
  };

  public static final ElementVisitor<Optional<TypeElement>, Unit> asTypeElement = new SimpleElementVisitor8<Optional<TypeElement>, Unit>(Optional.empty()) {

    @Override
    public Optional<TypeElement> visitType(final TypeElement e, final Unit p) {
      return Optional.of(e);
    }

  };

  public static final SimpleElementVisitor6<PackageElement, Void> getPackage = new SimpleElementVisitor6<PackageElement, Void>() {

    @Override
    public PackageElement visitPackage(final PackageElement e, final Void p) {
      return e;
    }


    @Override
    protected PackageElement defaultAction(final Element e, final Void p) {
      return e.getEnclosingElement().accept(getPackage, null);
    }

  };

  public static final SimpleElementVisitor6<Optional<ExecutableElement>, Void> asExecutableElement = new SimpleElementVisitor6<Optional<ExecutableElement>, Void>() {

    @Override
    protected Optional<ExecutableElement> defaultAction(final Element e, final Void p) {
      return Optional.empty();
    }

    @Override
    public Optional<ExecutableElement> visitExecutable(final ExecutableElement e, final Void p) {
      return Optional.of(e);
    }

  };

  public static String capitalize(final String s) {
    if ((s.length() >= 2)
        && Character.isHighSurrogate(s.charAt(0))
        && Character.isLowSurrogate(s.charAt(1))) {
      return s.substring(0, 2).toUpperCase(Locale.US) + s.substring(2);
    } else {
      return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }
  }

  public static <A, R> R fold(Optional<A> oa, R none, Function<A, R> some) {
    return oa.map(some).orElse(none);
  }

  public static <A> Optional<A> findOnlyOne(List<A> as) {
    return as.size() == 1 ? Optional.of(as.get(0)) : Optional.empty();
  }

  public static <A> String showList(List<A> as, Function<A, String> showA) {
    return "List(" + as.stream().map(showA).reduce((a1, a2) -> a1 + ", " + a2).orElse("") + ")";
  }

  public static <A> Stream<A> optionalAsStream(Optional<A> o) {
    return fold(o, Stream.<A>empty(), a -> Stream.of(a));
  }

  public static <A, B> Optional<List<B>> traverseOptional(List<A> as, Function<A, Optional<B>> f) {
    List<B> bs = new ArrayList<>();
    for (A a : as) {
      Optional<B> b = f.apply(a);
      if (b.isPresent()) {
        bs.add(b.get());
      } else {
        return Optional.empty();
      }
    }
    return Optional.of(bs);
  }

  public static ClassName getClassName(DeriveContext deriveContext, String className) {
    return ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName(), className);
  }


  public static String uncapitalize(final CharSequence s) {
    if ((s.length() >= 2)
        && Character.isHighSurrogate(s.charAt(0))
        && Character.isLowSurrogate(s.charAt(1))) {
      return s.toString().substring(0, 2).toLowerCase(Locale.US) + s.toString().substring(2);
    } else {
      return s.toString().substring(0, 1).toLowerCase(Locale.US) + s.toString().substring(1);
    }
  }

  public static String asArgumentsStringOld(final List<? extends VariableElement> parameters) {
    return parameters.stream().map(p -> p.getSimpleName().toString()).reduce((s1, s2) -> s1 + ", " + s2).orElse("");
  }


  public static String asArgumentsString(List<DataArgument> arguments, List<TypeRestriction> restrictions) {

    return Stream.concat(
        arguments.stream().map(a -> "this." + a.fieldName()),
        restrictions.stream().map(tr -> uncapitalize(tr.restrictedTypeParameter().toString()) + " -> " + uncapitalize(tr.restrictedTypeParameter().toString())))
        .reduce((s1, s2) -> s1 + ", " + s2)
        .orElse("");
  }

  public static String asLambdaParametersString(List<DataArgument> arguments, List<TypeRestriction> restrictions) {

    return joinStringsAsArguments(Stream.concat(
        arguments.stream().map(DataArgument::fieldName),
        restrictions.stream().map(TypeRestriction::dataArgument).map(DataArgument::fieldName)));
  }

  public static String asArgumentsString(List<DataArgument> arguments) {

    return joinStringsAsArguments(arguments.stream().map(DataArgument::fieldName));
  }

  public static String joinStringsAsArguments(Stream<String> arguments) {

    return joinStrings(arguments, ", ");
  }

  public static String joinStrings(Stream<String> strings, String joiner) {

    return strings
        .reduce((s1, s2) -> s1 + joiner + s2)
        .orElse("");
  }

  public static ParameterizedTypeName typeName(TypeConstructor typeConstructor, List<TypeRestriction> restrictions, Types types) {
    return ParameterizedTypeName.get(
        ClassName.get(typeConstructor.typeElement()),
        typeConstructor.typeVariables().stream()
            .map(tv -> restrictions.stream()
                .filter(tr -> types.isSameType(tr.restrictedTypeParameter(), tv))
                .findFirst().map(TypeRestriction::type).orElse(tv))
            .map(TypeName::get).toArray(TypeName[]::new));
  }

  public static List<ExecutableElement> getAbstractMethods(final List<? extends Element> amongElements) {
    return getMethods(amongElements).filter(e -> e.getModifiers().contains(Modifier.ABSTRACT))
        .collect(Collectors.toList());
  }

  public static Stream<ExecutableElement> getMethods(final List<? extends Element> amongElements) {
    return amongElements.stream()
        .map(Utils.asExecutableElement::visit).flatMap(Utils::optionalAsStream);
  }


  public static MethodSpec.Builder overrideMethodBuilder(final ExecutableElement abstractMethod) {

    return MethodSpec.methodBuilder(abstractMethod.getSimpleName().toString())
        .addAnnotation(Override.class)
        .addModifiers(abstractMethod.getModifiers().stream().filter(m -> m != Modifier.ABSTRACT).collect(Collectors.toList()))
        .addTypeVariables(abstractMethod.getTypeParameters().stream()
            .map(TypeParameterElement::asType)
            .map(asTypeVariable::visit)
            .flatMap(tvOpt -> tvOpt.map(Collections::singleton).orElse(Collections.<TypeVariable>emptySet()).stream())
            .map(TypeVariableName::get)
            .collect(Collectors.toList()))
        .returns(TypeName.get(abstractMethod.getReturnType()))
        .addParameters(abstractMethod.getParameters().stream()
            .map(ve -> ParameterSpec.builder(
                TypeName.get(ve.asType()),
                ve.getSimpleName().toString()).build()
            ).collect(Collectors.toList()));
  }


  public static String show(AlgebraicDataType adt) {
    return "adt(" + adt.match((typeConstructor, matchMethod, dataConstruction, fields) ->
        show(typeConstructor) + ","
            + show(matchMethod) + ","
            + show(dataConstruction) + ","
            + showList(fields, Utils::show)
    )
        + ")";
  }

  private static String show(MatchMethod matchMethod) {
    return "matchMethod(" + matchMethod.match((method, returnTypeVariable) -> method + "," + returnTypeVariable) + ")";
  }

  private static String show(TypeConstructor constructor) {
    return "typeConstructor(" + constructor.match((typeElement, declaredType, typeVariables) -> typeElement + "," + declaredType + "," + showList(typeVariables, TypeVariable::toString)) + ")";
  }

  public static String show(DataConstruction construction) {
    return construction.match(new DataConstruction.Cases<String>() {

      @Override
      public String multipleConstructors(DataConstructors constructors) {
        return show(constructors);
      }

      @Override
      public String oneConstructor(DataConstructor constructor) {
        return show(constructor);
      }

      @Override
      public String noConstructor() {
        return "noConstructor";
      }
    });
  }

  public static String show(DataConstructor dataConstructor) {
    return "constructor(" + dataConstructor.match((name, arguments, typeVariables, typeRestrictions, deconstructor) ->
        name + ", "
            + showList(arguments, Utils::show) + ", "
            + showList(typeVariables, TypeVariable::toString) + ", "
            + showList(typeRestrictions, Utils::show))
        + ")";
  }

  public static String show(DataConstructors constructors) {
    return constructors.match(new DataConstructors.Cases<String>() {
      @Override
      public String visitorDispatch(VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors) {
        return "visitorDispatch(" + visitorParam.toString() + ", " + visitorType.toString() + ", " + showList(constructors, Utils::show) + ")";
      }

      @Override
      public String functionsDispatch(List<DataConstructor> constructors) {
        return "functionsDispatch(" + showList(constructors, Utils::show) + ")";
      }

    });
  }

  public static String show(DataArgument arg) {
    return "argument(" + arg.match((fieldName, type) -> fieldName + "," + type.toString()) + ")";
  }

  public static String show(TypeRestriction typeRestriction) {
    return "typeRestriction(" + typeRestriction.match((typeParameter, type, idFunction) -> typeParameter.toString() + "," + type.toString() + show(idFunction)) + ")";
  }


  public static <A, B> DeriveResult<List<B>> traverseResults(List<A> as, Function<A, DeriveResult<B>> f) {
    DeriveMessage errorMsg;
    List<B> results = new ArrayList<>();
    for (A a : as) {
      errorMsg = f.apply(a).match(err -> err, result -> {
            results.add(result);
            return null;
          }
      );
      if (errorMsg != null) {
        return DeriveResult.error(errorMsg);
      }
    }
    return DeriveResult.result(results);
  }

  public static DerivedCodeSpec appendCodeSpecs(DerivedCodeSpec codeSpec1, DerivedCodeSpec codeSpec2) {
    return DerivedCodeSpec.codeSpec(
        concat(codeSpec1.classes(), codeSpec2.classes()),
        concat(codeSpec1.fields(), codeSpec2.fields()),
        concat(codeSpec1.methods(), codeSpec2.methods()),
        concat(codeSpec1.infos(), codeSpec2.infos()),
        concat(codeSpec1.warnings(), codeSpec2.warnings())
    );
  }


  public static final <A> List<A> concat(List<A> as1, List<A> as2) {
    return Stream.concat(as1.stream(), as2.stream()).collect(Collectors.toList());
  }


  public static <A, B> String show(P2<A, B> p2, Function<A, String> showA, Function<B, String> showB) {
    return "p2(" + p2.match((a, b) -> showA.apply(a) + ", " + showB.apply(b)) + ")";
  }


}
