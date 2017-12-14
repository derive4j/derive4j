/*
 * Copyright (c) 2017, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *  * Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.derive4j.example;

import fj.*;
import org.derive4j.*;

@Data(flavour = Flavour.FJ)
@Derive(@Instances({ Show.class, Hash.class, Equal.class, Ord.class}))
public abstract class Either<A, B> {

  Either() {

  }

  /**
   * The catamorphism for either. Folds over this either breaking into left or right.
   *
   * @param left The function to call if this is left.
   * @param right The function to call if this is right.
   * @return The reduced value.
   */
  public abstract <X> X either(F<A, X> left, F<B, X> right);


  // In case you need to interact with unsafe code that
  // expects hashCode/equal/toString to be implemented:

  @Deprecated
  @Override
  public final boolean equals(Object obj) {
    return Equal.equals0(Either.class, this, obj,
        Eithers.eitherEqual(Equal.anyEqual(), Equal.anyEqual()));
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
