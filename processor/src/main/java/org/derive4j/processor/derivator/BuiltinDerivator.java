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

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.derive4j.Make;
import org.derive4j.Makes;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DeriveContext;
import org.derive4j.processor.derivator.patternmatching.PatternMatchingDerivator;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;
import static org.derive4j.Make.constructors;
import static org.derive4j.Make.lambdaVisitor;
import static org.derive4j.processor.Utils.traverseResults;
import static org.derive4j.processor.api.DeriveResults.lazy;

public class BuiltinDerivator {

  private static final Function<Make, Stream<Make>> dependencies = Makes.cases()
      .lambdaVisitor(Stream::<Make>of)
      .constructors(Stream::of)
      .lazyConstructor(Stream::of)
      .patternMatching(() -> of(lambdaVisitor))
      .getters(() -> of(lambdaVisitor))
      .modifiers(() -> of(lambdaVisitor, constructors))
      .catamorphism(() -> of(lambdaVisitor))
      .hktCoerce(Stream::of);

  public static BiFunction<AlgebraicDataType, DeriveContext, DeriveResult<DerivedCodeSpec>> derivator(DeriveUtils deriveUtils) {
    ExportDerivator exportDerivator = new ExportDerivator(deriveUtils);
    return (adt, deriveContext) -> traverseResults(concat(of(exportDerivator.derive(adt)), deriveContext.makes()
        .stream()
        .map(Makes.cases()
            .lambdaVisitor(lazy(() -> MapperDerivator.derive(adt, deriveContext, deriveUtils)))
            .constructors(lazy(() -> StrictConstructorDerivator.derive(adt, deriveContext, deriveUtils)))
            .lazyConstructor(lazy(() -> LazyConstructorDerivator.derive(adt, deriveContext, deriveUtils)))
            .patternMatching(lazy(() -> PatternMatchingDerivator.derive(adt, deriveContext, deriveUtils)))
            .getters(lazy(() -> GettersDerivator.derive(adt, deriveContext, deriveUtils)))
            .modifiers(lazy(() -> ModiersDerivator.derive(adt, deriveContext, deriveUtils)))
            .catamorphism(lazy(() -> new CataDerivator(deriveUtils, deriveContext, adt).derive()))
            .hktCoerce(DeriveResult.result(DerivedCodeSpec.none())))).collect(toList())).map(
        codeSpecList -> codeSpecList.stream().reduce(DerivedCodeSpec.none(), DerivedCodeSpec::append));
  }

  public static Set<Make> makeWithDependencies(Make... makes) {

    EnumSet<Make> makeSet = EnumSet.noneOf(Make.class);

    makeSet.addAll(Arrays.asList(makes).stream().flatMap(m -> concat(dependencies.apply(m), of(m))).collect(toList()));

    return Collections.unmodifiableSet(makeSet);
  }

}
