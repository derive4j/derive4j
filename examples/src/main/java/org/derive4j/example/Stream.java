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
import org.derive4j.Data;
import org.derive4j.FieldNames;
import org.derive4j.Flavour;

import static org.derive4j.example.Streams.cons;
import static org.derive4j.example.Streams.getHead;
import static org.derive4j.example.Streams.getTail;
import static org.derive4j.example.Streams.lazy;

@Data(flavour = Flavour.FJ)
interface Stream<A> {
  <R> R match(@FieldNames({ "head", "tail" }) F2<A, Stream<A>, R> cons);

  default A get(int i) {
    Stream<A> s = this;
    while (i-- > 0) {
      s = getTail(s);
    }
    return getHead(s);
  }

  default Stream<A> tail() {
    return lazy(() -> getTail(this));
  }

  static Stream<Long> fibs = fix0(fibs ->
  // fibs = 0 : 1 : zipWith (+) fibs (tail fibs)
  cons(0L, cons(1L, zipWith((i1, i2) -> i1 + i2, fibs, fibs.tail()))),
      Streams::lazy);

  public static void main(String[] args) {
    System.out.println(fibs.get(5000000));
    System.out.println(fibs.get(4000000));
  }

  static <A, B, C> F2<Stream<A>, Stream<B>, Stream<C>> zipWith(F2<A, B, C> f) {
    return fix2(rec -> (s1, s2) -> s1.match(
        (a, as) -> s2.match(
            (b, bs) -> cons(f.f(a, b), rec.f(as, bs)))),
        Streams::lazy);
  }

  static <A, B, C> Stream<C> zipWith(F2<A, B, C> f, Stream<A> as, Stream<B> bs) {
    return zipWith(f).f(as, bs);
  }

  static <A> A fix0(F<A, A> f, F<F0<A>, A> delay) {
    return new Object() {
      final A a = delay.f(() -> f.f(this.a));
    }.a;
  }

  static <A, B> F<A, B> fix(F<F<A, B>, F<A, B>> f, F<F0<B>, B> delay) {
    return new F<A, B>() {
      final F<A, B> rec = f.f(this);

      @Override
      public B f(A a) {
        return delay.f(() -> rec.f(a));
      }
    };
  }

  static <A, B, C> F2<A, B, C> fix2(F<F2<A, B, C>, F2<A, B, C>> f, F<F0<C>, C> delay) {
    return new F2<A, B, C>() {
      final F2<A, B, C> rec = f.f(this);

      @Override
      public C f(A a, B b) {
        return delay.f(() -> rec.f(a, b));
      }
    };
  }
}
