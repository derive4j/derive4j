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
import java.util.function.BiFunction;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DeriveContext;
import org.derive4j.processor.derivator.patternmatching.PatternMatchingDerivator;

import static org.derive4j.processor.Utils.traverseResults;
import static org.derive4j.processor.api.DeriveResults.lazy;

public class BuiltinDerivator {

  public static BiFunction<AlgebraicDataType, DeriveContext, DeriveResult<DerivedCodeSpec>> derivator(DeriveUtils deriveUtils) {
    return (adt, deriveContext) ->
       traverseResults(Arrays.asList(
          lazy(() -> StrictConstructorDerivator.derive(adt, deriveContext, deriveUtils)),
          lazy(() -> LazyConstructorDerivator.derive(adt, deriveContext, deriveUtils)),
          lazy(() -> MapperDerivator.derive(adt, deriveContext, deriveUtils)),
          lazy(() -> new CataDerivator(deriveUtils, deriveContext, adt).derive()),
          lazy(() -> GettersDerivator.derive(adt, deriveContext, deriveUtils)),
          lazy(() -> ModiersDerivator.derive(adt, deriveContext, deriveUtils)),
          lazy(() -> PatternMatchingDerivator.derive(adt, deriveContext, deriveUtils))
          )).map(codeSpecList -> codeSpecList.stream().reduce(DerivedCodeSpec.none(), DerivedCodeSpec::append));
  }

}
