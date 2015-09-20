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

import org.derive4j.Derived;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DeriveContext;
import org.derive4j.processor.derivator.patternmatching.PatternMatchingDerivator;
import org.derive4j.processor.Utils;

import java.util.List;
import java.util.function.Function;

import static org.derive4j.processor.Utils.traverseResults;

public interface BuiltinDerivator {

  static Function<List<Derived>, BuiltinDerivator> derivator(DeriveUtils deriveUtils) {

    return derivedList -> (adt, deriveContext) ->
        MapperDerivator.derive(adt, deriveContext, deriveUtils).bind(visitorMappers ->

            traverseResults(derivedList, d -> d.match(new Derived.Cases<DeriveResult<DerivedCodeSpec>>() {
              @Override
              public DeriveResult<DerivedCodeSpec> strictConstructors() {
                return StrictConstructorDerivator.derive(adt, deriveContext, deriveUtils);
              }

              @Override
              public DeriveResult<DerivedCodeSpec> lazyConstructor() {
                return LazyConstructorDerivator.derive(adt, deriveContext.deriveFlavour(), deriveUtils);
              }

              @Override
              public DeriveResult<DerivedCodeSpec> patternMatching() {
                return PatternMatchingDerivator.derive(adt, deriveContext, deriveUtils);
              }

              @Override
              public DeriveResult<DerivedCodeSpec> getters() {
                return GettersDerivator.derive(adt, deriveContext, deriveUtils);
              }

              @Override
              public DeriveResult<DerivedCodeSpec> modifiers() {
                return ModiersDerivator.derive(adt, deriveContext, deriveUtils);
              }

            })).map(codeSpecList -> Utils.appendCodeSpecs(visitorMappers, codeSpecList.stream().reduce(DerivedCodeSpec.none(), Utils::appendCodeSpecs))));
  }

  DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt, DeriveContext deriveContext);

}
