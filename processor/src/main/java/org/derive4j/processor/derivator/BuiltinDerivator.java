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
import java.util.function.Function;
import java.util.stream.Stream;
import org.derive4j.Make;
import org.derive4j.Makes;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.derivator.patternmatching.PatternMatchingDerivator;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;
import static org.derive4j.Make.constructors;
import static org.derive4j.Make.lambdaVisitor;
import static org.derive4j.processor.Utils.traverseResults;

public class BuiltinDerivator {

  public static Derivator derivator(DeriveUtils deriveUtils) {

    Derivator exportDerivator = new ExportDerivator(deriveUtils);

    Function<Make, Derivator> makeDerivators = Makes.cases().<Derivator>lambdaVisitor(
        new MapperDerivator(deriveUtils)).constructors(new StrictConstructorDerivator(deriveUtils))
        .lazyConstructor(new LazyConstructorDerivator(deriveUtils))
        .patternMatching(new PatternMatchingDerivator(deriveUtils))
        .firstClassPatternMatching(__ -> DeriveResult.result(DerivedCodeSpec.none()))
        .getters(new GettersDerivator(deriveUtils))
        .modifiers(new ModiersDerivator(deriveUtils))
        .catamorphism(new CataDerivator(deriveUtils))
        .hktCoerce(__ -> DeriveResult.result(DerivedCodeSpec.none()));

    return adt -> traverseResults(
        concat(of(exportDerivator), adt.deriveConfig().makes().stream().map(makeDerivators)).map(d -> d.derive(adt))
            .collect(toList())).map(
        codeSpecList -> codeSpecList.stream().reduce(DerivedCodeSpec.none(), DerivedCodeSpec::append));
  }

  public static Set<Make> makeWithDependencies(Make... makes) {

    EnumSet<Make> makeSet = EnumSet.noneOf(Make.class);

    makeSet.addAll(Arrays.asList(makes).stream().flatMap(m -> concat(dependencies.apply(m), of(m))).collect(toList()));

    return Collections.unmodifiableSet(makeSet);
  }

  private static final Function<Make, Stream<Make>> dependencies = Makes.cases()
      .lambdaVisitor(Stream::<Make>of)
      .constructors(Stream::of)
      .lazyConstructor(Stream::of)
      .patternMatching(() -> of(lambdaVisitor))
      .firstClassPatternMatching(() -> of(lambdaVisitor))
      .getters(() -> of(lambdaVisitor))
      .modifiers(() -> of(lambdaVisitor, constructors))
      .catamorphism(() -> of(lambdaVisitor))
      .hktCoerce(Stream::of);

}
