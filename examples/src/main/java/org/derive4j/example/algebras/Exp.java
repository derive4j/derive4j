package org.derive4j.example.algebras;

import fj.Unit;
import org.derive4j.Data;

@Data
@FunctionalInterface
interface Exp {

  interface ExpAlg<T, E, A> {
    A Lit(int lit);

    A Add(E e1, E e2);
  }

  <R> R accept(ExpAlg<Unit, Exp, R> alg);
}

@Data
@FunctionalInterface
interface ExpMul {

  interface ExpMulAlg<E, A> extends Exp.ExpAlg<Unit, E, A> {
    A Mul(E e1, E e2);
  }

  <A> A accept(ExpMulAlg<ExpMul, A> alg);

  static ExpMul fromExp(Exp exp) {
    return Exps.cata(ExpMuls::Lit, ExpMuls::Add, ExpMuls::lazy).apply(exp);
  }
}
