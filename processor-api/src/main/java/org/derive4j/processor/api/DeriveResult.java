/*
 * Copyright (c) 2019, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * This file is part of "Derive4J - Processor API".
 *
 * "Derive4J - Processor API" is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * "Derive4J - Processor API" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with "Derive4J - Processor API".  If not, see <http://www.gnu.org/licenses/>.
 */
package org.derive4j.processor.api;

import java.util.function.Function;
import org.derive4j.Data;

import static org.derive4j.processor.api.DeriveResults.modResult;

@Data
public abstract class DeriveResult<A> {

  public static <A> DeriveResult<A> error(DeriveMessage errorMsg) {

    return DeriveResults.error(errorMsg);
  }

  public static <A> DeriveResult<A> result(A result) {

    return DeriveResults.result(result);
  }

  DeriveResult() {

  }

  public <B> DeriveResult<B> map(Function<A, B> f) {

    return modResult(f).apply(this);
  }

  public <B> DeriveResult<B> bind(Function<A, DeriveResult<B>> f) {

    return match(DeriveResult::error, f);
  }

  public abstract <R> R match(Function<DeriveMessage, R> error, Function<A, R> result);

}
