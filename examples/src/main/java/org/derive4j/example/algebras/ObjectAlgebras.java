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
