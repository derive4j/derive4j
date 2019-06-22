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

import fj.Equal;
import fj.F;
import fj.F0;
import fj.Hash;
import fj.Ord;
import fj.Show;
import fj.control.Trampoline;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Flavour;
import org.derive4j.Instances;

import static org.derive4j.example.Expressions.Add;
import static org.derive4j.example.Expressions.Const;
import static org.derive4j.example.Expressions.Mult;
import static org.derive4j.example.Expressions.expressionHash;
import static org.derive4j.example.Expressions.expressionShow;

@Data(value = @Derive(@Instances({ Show.class, Hash.class, Equal.class, Ord.class })), flavour = Flavour.FJ)
public abstract class Expression {

  public static Integer eval(Expression expression) {

    return stackSafeEval.f(expression).run();
  }

  public static void main(String[] args) {
    Expression expr = Add(Const(1), Mult(Const(2), Mult(Const(3), Const(3))));
    expressionShow().println(expr);
    expressionHash().hash(expr);
    System.out.println(eval(expr)); // (1+(2*(3*3))) = 19
  }

  private static final F<Expression, Integer> eval = Expressions.cata(
      value -> value,
      (left, right) -> left + right,
      (left, right) -> left * right,
      expr -> -expr,
      F0::f);

  private static final F<Expression, Trampoline<Integer>> stackSafeEval = Expressions.cata(
      value -> Trampoline.pure(value),
      (left, right) -> left.zipWith(right, (l, r) -> l + r),
      (left, right) -> left.zipWith(right, (l, r) -> l * r),
      expr -> expr.map(i -> -i),
      Trampoline::suspend);

  public abstract <R> R match(Cases<Expression, R> cases);

  interface Cases<E, R> {
    R Const(int value);

    R Add(E left, E right);

    R Mult(E left, E right);

    R Neg(E expr);
  }
}
