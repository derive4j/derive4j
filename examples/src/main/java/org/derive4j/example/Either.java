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
package org.derive4j.example;

import fj.*;
import org.derive4j.*;

@Data(flavour = Flavour.FJ)
@Derive(@Instances({ Show.class, Hash.class, Equal.class, Ord.class }))
public abstract class Either<A, B> {

  Either() {

  }

  /**
   * The catamorphism for either. Folds over this either breaking into left or
   * right.
   *
   * @param left
   *          The function to call if this is left.
   * @param right
   *          The function to call if this is right.
   * @return The reduced value.
   */
  public abstract <X> X either(F<A, X> left, F<B, X> right);

  // In case you need to interact with unsafe code that
  // expects hashCode/equal/toString to be implemented:

  @Deprecated
  @Override
  public final boolean equals(Object obj) {
    return Equal.equals0(Either.class, this, obj, Eithers.eitherEqual(Equal.anyEqual(), Equal.anyEqual()));
  }

  @Deprecated
  @Override
  public final int hashCode() {
    return Eithers.<A, B>eitherHash(Hash.anyHash(), Hash.anyHash()).hash(this);
  }

  @Deprecated
  @Override
  public final String toString() {
    return Eithers.<A, B>eitherShow(Show.anyShow(), Show.anyShow()).showS(this);
  }
}
