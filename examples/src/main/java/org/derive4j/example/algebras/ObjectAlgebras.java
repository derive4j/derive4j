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
package org.derive4j.example.algebras;

import java.util.function.Function;
import org.derive4j.Data;

public class ObjectAlgebras {

  @Data
  interface Exp {

    interface ExpAlg<E, R> {
      R Lit(int lit);

      R Add(E e1, E e2);
    }

    <R> R accept(ExpAlg<Exp, R> alg);
  }

  @Data
  interface ExpMul {

    interface ExpMulAlg<E, R> extends Exp.ExpAlg<E, R> {
      R Mul(E e1, E e2);
    }

    <R> R accept(ExpMulAlg<ExpMul, R> alg);

    static Function<Exp, ExpMul> fromExp() {
      ExpMulAlg<ExpMul, ExpMul> factory = ExpMuls.factory();
      return Exps.cata(factory, ExpMuls::lazy);
    }
  }

}
