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
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;
import org.derive4j.processor.api.DeriveMessage;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.model.DataArgument;
import org.derive4j.processor.api.model.DataArguments;
import org.derive4j.processor.api.model.TypeRestriction;
import org.derive4j.processor.api.model.TypeRestrictions;

import static java.util.stream.Collectors.toList;
import static org.derive4j.processor.P2.p2;
import static org.derive4j.processor.api.model.DataArguments.getFieldName;

final class Utils {
  static final TypeVisitor<Optional<DeclaredType>, Unit>                asDeclaredType      = new SimpleTypeVisitor8<Optional<DeclaredType>, Unit>(
      Optional.empty()) {
                                                                                              @Override
                                                                                              public Optional<DeclaredType> visitDeclared(
                                                                                                  final DeclaredType t,
                                                                                                  final Unit p) {

                                                                                                return Optional.of(t);
                                                                                              }
                                                                                            };
  static final TypeVisitor<Optional<TypeVariable>, Unit>                asTypeVariable      = new SimpleTypeVisitor8<Optional<TypeVariable>, Unit>(
      Optional.empty()) {
                                                                                              @Override
                                                                                              public Optional<TypeVariable> visitTypeVariable(
                                                                                                  final TypeVariable t,
                                                                                                  final Unit p) {

                                                                                                return Optional.of(t);
                                                                                              }
                                                                                            };
  static final ElementVisitor<Optional<TypeElement>, Unit>              asTypeElement       = new SimpleElementVisitor8<Optional<TypeElement>, Unit>(
      Optional.empty()) {

                                                                                              @Override
                                                                                              public Optional<TypeElement> visitType(
                                                                                                  final TypeElement e,
                                                                                                  final Unit p) {

                                                                                                return Optional.of(e);
                                                                                              }

                                                                                            };
  static final SimpleElementVisitor8<PackageElement, Void>              getPackage          = new SimpleElementVisitor8<PackageElement, Void>() {

                                                                                              @Override
                                                                                              public PackageElement visitPackage(
                                                                                                  final PackageElement e,
                                                                                                  final Void p) {

                                                                                                return e;
                                                                                              }

                                                                                              @Override
                                                                                              protected PackageElement defaultAction(
                                                                                                  final Element e,
                                                                                                  final Void p) {

                                                                                                return e
                                                                                                    .getEnclosingElement()
                                                                                                    .accept(getPackage,
                                                                                                        null);
                                                                                              }

                                                                                            };
  static final SimpleElementVisitor8<Optional<ExecutableElement>, Void> asExecutableElement = new SimpleElementVisitor8<Optional<ExecutableElement>, Void>() {

                                                                                              @Override
                                                                                              public Optional<ExecutableElement> visitExecutable(
                                                                                                  final ExecutableElement e,
                                                                                                  final Void p) {

                                                                                                return Optional.of(e);
                                                                                              }

                                                                                              @Override
                                                                                              protected Optional<ExecutableElement> defaultAction(
                                                                                                  final Element e,
                                                                                                  final Void p) {

                                                                                                return Optional.empty();
                                                                                              }

                                                                                            };

  static final SimpleElementVisitor8<Optional<VariableElement>, Void> asVariableElement = new SimpleElementVisitor8<Optional<VariableElement>, Void>() {

    @Override
    public Optional<VariableElement> visitVariable(final VariableElement e, final Void p) {
      return Optional.of(e);
    }

    @Override
    protected Optional<VariableElement> defaultAction(final Element e, final Void p) {
      return Optional.empty();
    }
  };

  static final TypeVisitor<TypeMirror, Types> asBoxedType = new SimpleTypeVisitor8<TypeMirror, Types>() {

    @Override
    public TypeMirror visitPrimitive(PrimitiveType t, Types types) {

      return types.boxedClass(t).asType();
    }

    @Override
    protected TypeMirror defaultAction(TypeMirror e, Types types) {

      return e;
    }
  };

  private Utils() {
  }

  static String capitalize(final CharSequence s) {

    return ((s.length() >= 2) && Character.isHighSurrogate(s.charAt(0)) && Character.isLowSurrogate(s.charAt(1)))
        ? (s.toString().substring(0, 2).toUpperCase(Locale.US) + s.toString().substring(2))
        : (s.toString().substring(0, 1).toUpperCase(Locale.US) + s.toString().substring(1));
  }

  static <A, R> R fold(Optional<A> oa, R none, Function<A, R> some) {

    return oa.map(some).orElse(none);
  }

  static <A> Optional<A> findOnlyOne(List<A> as) {

    return (as.size() == 1) ? Optional.of(as.get(0)) : Optional.empty();
  }

  static <A> Stream<A> optionalAsStream(Optional<A> o) {

    return fold(o, Stream.<A>empty(), Stream::of);
  }

  static <K, V> Optional<V> get(K key, Map<? extends K, ? extends V> map) {
    return Optional.ofNullable(map.get(key));
  }

  static <A, B> Optional<List<B>> traverseOptional(List<A> as, Function<A, Optional<B>> f) {

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

  static String uncapitalize(final CharSequence s) {

    return ((s.length() >= 2) && Character.isHighSurrogate(s.charAt(0)) && Character.isLowSurrogate(s.charAt(1)))
        ? (s.toString().substring(0, 2).toLowerCase(Locale.US) + s.toString().substring(2))
        : (s.toString().substring(0, 1).toLowerCase(Locale.US) + s.toString().substring(1));
  }

  static String asArgumentsStringOld(final List<? extends VariableElement> parameters) {

    return parameters.stream().map(p -> p.getSimpleName().toString()).reduce((s1, s2) -> s1 + ", " + s2).orElse("");
  }

  static String asArgumentsString(List<DataArgument> arguments, List<TypeRestriction> restrictions) {

    return Stream
        .concat(arguments.stream().map(a -> "this." + a.fieldName()), restrictions.stream().map(tr -> "TypeEq.refl()"))
        .reduce((s1, s2) -> s1 + ", " + s2)
        .orElse("");
  }

  static String asLambdaParametersString(List<DataArgument> arguments, List<TypeRestriction> restrictions) {
    return asLambdaParametersString(arguments, restrictions, "");
  }

  static String asLambdaParametersString(List<DataArgument> arguments, List<TypeRestriction> restrictions,
      String suffix) {
    return joinStringsAsArguments(Stream.concat(arguments.stream(), restrictions.stream().map(TypeRestriction::typeEq))
        .map(da -> getFieldName(da) + suffix));
  }

  static String asLambdaParametersString(List<DataArgument> arguments, List<TypeRestriction> typeRestrictions,
      NameAllocator nameAllocator) {

    return joinStringsAsArguments(
        Stream.concat(arguments.stream(), typeRestrictions.stream().map(TypeRestrictions::getTypeEq))
            .map(DataArguments::getFieldName)
            .map(nameAllocator::newName));
  }

  static String asArgumentsString(List<DataArgument> arguments) {

    return joinStringsAsArguments(arguments.stream().map(DataArgument::fieldName));
  }

  static String joinStringsAsArguments(Stream<String> arguments) {

    return joinStrings(arguments, ", ");
  }

  static String joinStrings(Stream<String> strings, String joiner) {

    return strings.reduce((s1, s2) -> s1 + joiner + s2).orElse("");
  }

  static TypeName typeName(ClassName className, Stream<TypeName> typeArguments) {

    TypeName[] typeArgs = typeArguments.toArray(TypeName[]::new);

    return (typeArgs.length == 0) ? className : ParameterizedTypeName.get(className, typeArgs);
  }

  static Stream<ExecutableElement> getMethods(final List<? extends Element> amongElements) {

    return amongElements.stream().map(asExecutableElement::visit).flatMap(Utils::optionalAsStream);
  }

  static Stream<VariableElement> getFields(final List<? extends Element> amongElements) {

    return amongElements.stream().map(asVariableElement::visit).flatMap(Utils::optionalAsStream);
  }

  static <T> Predicate<T> p(Predicate<T> p) {
    return p;
  }

  static <A, B> Function<A, B> f(Function<A, B> f) {

    return f;
  }

  static MethodSpec.Builder overrideMethodBuilder(final ExecutableElement abstractMethod) {

    return MethodSpec.methodBuilder(abstractMethod.getSimpleName().toString())
        .addAnnotation(Override.class)
        .addModifiers(abstractMethod.getModifiers().stream().filter(m -> m != Modifier.ABSTRACT).collect(toList()))
        .addTypeVariables(abstractMethod.getTypeParameters()
            .stream()
            .map(TypeParameterElement::asType)
            .map(asTypeVariable::visit)
            .flatMap(tvOpt -> tvOpt.map(Collections::singleton).orElse(Collections.emptySet()).stream())
            .map(TypeVariableName::get)
            .collect(toList()))
        .returns(TypeName.get(abstractMethod.getReturnType()))
        .addParameters(abstractMethod.getParameters()
            .stream()
            .map(ve -> ParameterSpec.builder(TypeName.get(ve.asType()), ve.getSimpleName().toString()).build())
            .collect(toList()));
  }

  static <A, B> DeriveResult<List<B>> traverseResults(Collection<A> as, Function<A, DeriveResult<B>> f) {

    return traverseResults(as.stream().map(f).collect(toList()));
  }

  static <A> DeriveResult<List<A>> traverseResults(List<DeriveResult<A>> as) {

    List<A> results = new ArrayList<>();
    for (DeriveResult<A> a : as) {
      DeriveMessage errorMsg = a.match(err -> err, result -> {
        results.add(result);
        return null;
      });
      if (errorMsg != null) {
        return DeriveResult.error(errorMsg);
      }
    }
    return DeriveResult.result(results);
  }

  static <A, B> List<P2<A, B>> zip(List<? extends A> as, List<? extends B> bs) {

    return IntStream.range(0, Math.min(as.size(), bs.size())).<P2<A, B>>mapToObj(i -> p2(as.get(i), bs.get(i))).collect(
        toList());
  }

  static <A> List<P2<A, Integer>> zipWithIndex(List<? extends A> as) {
    return zip(as, IntStream.range(0, as.size()).boxed().collect(toList()));
  }

}
