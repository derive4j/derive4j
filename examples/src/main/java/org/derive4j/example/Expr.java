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
import fj.Hash;
import fj.Ord;
import fj.Show;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.FieldNames;
import org.derive4j.Instances;

import static org.derive4j.example.Exprs.Add;
import static org.derive4j.example.Exprs.Const;
import static org.derive4j.example.Exprs.Mult;

@Data(@Derive(@Instances({ Show.class, Hash.class, Equal.class, Ord.class })))
public abstract class Expr {

  public static Integer eval(Expr expression) {

    return expression.match(
        i -> i,
        (left, right) -> eval(left) + eval(right),
        (left, right) -> eval(left) * eval(right),
        (expr) -> -eval(expr));
  }

  public static void main(String[] args) {

    Expr expr = Add(Const(1), Mult(Const(2), Mult(Const(3), Const(3))));
    System.out.println(eval(expr)); // (1+(2*(3*3))) = 19
  }

  public abstract <R> R match(@FieldNames("value") IntFunction<R> Const,
      @FieldNames({ "left", "right" }) BiFunction<Expr, Expr, R> Add,
      @FieldNames({ "left", "right" }) BiFunction<Expr, Expr, R> Mult, @FieldNames("expr") Function<Expr, R> Neg);

}
