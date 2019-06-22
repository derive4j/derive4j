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

import fj.F;
import fj.F0;
import fj.F2;
import org.derive4j.Data;
import org.derive4j.FieldNames;
import org.derive4j.Flavour;

public abstract class Corecursive<A> {

  public static Corecursive<Integer> range(final int from, int toExclusive) {

    return nu(from, s -> (s == toExclusive) ? Steps.done() : Steps.yield(s, s + 1));
  }

  public static <A> Corecursive<A> mu(Mu<A> mu) {

    return new Corecursive<A>() {
      @Override
      <X> X match(Cases<A, X> cases) {

        return cases.build(mu);
      }
    };
  }

  public static <S, A> Corecursive<A> nu(S init, F<S, Step<A, S>> stepper) {

    return new Corecursive<A>() {
      @Override
      <X> X match(Cases<A, X> cases) {

        return cases.unfold(init, stepper);
      }
    };
  }

  public final <B> Corecursive<B> map(F<A, B> f) {

    return match(new Cases<A, Corecursive<B>>() {
      @Override
      public Corecursive<B> build(Mu<A> mu) {

        return mu(mu.map(f));
      }

      @Override
      public <S> Corecursive<B> unfold(S init, F<S, Step<A, S>> stepper) {

        F<Step<A, S>, Step<B, S>> mapValue = Steps.modValue(f);
        return nu(init, s -> mapValue.f(stepper.f(s)));
      }
    });
  }

  public final <X> X foldl(final F2<X, A, X> f, final X x) {

    return match(new Cases<A, X>() {
      @Override
      public X build(Mu<A> mu) {

        return mu.foldl(f, x);
      }

      @Override
      public <S> X unfold(S init, F<S, Step<A, S>> stepper) {

        class Acc implements F2<A, S, Boolean> {
          S s   = init;
          X acc = x;

          @Override
          public Boolean f(A a, S s) {

            acc = f.f(acc, a);
            this.s = s;
            return Boolean.TRUE;
          }
        }
        Acc acc = new Acc();

        while (stepper.f(acc.s).match(() -> Boolean.FALSE, acc)) {
        }
        return acc.acc;
      }
    });
  }

  public final int length() {

    return foldl((i, a) -> i + 1, 0);
  }

  abstract <X> X match(Cases<A, X> cases);

  interface Cases<A, X> {
    X build(Mu<A> mu);

    <S> X unfold(S init, F<S, Step<A, S>> stepper);
  }

  @Data(flavour = Flavour.FJ)
  abstract static class Step<A, S> {

    abstract <X> X match(F0<X> done, @FieldNames({ "value", "stepper" }) F2<A, S, X> yield);
  }

  abstract static class Mu<A> {

    abstract <X> X foldr(final F2<A, F0<X>, X> f, final X x);

    abstract <X> X foldl(final F2<X, A, X> f, final X x);

    final <B> Mu<B> map(F<A, B> f) {

      return new Mu<B>() {
        @Override
        <X> X foldr(F2<B, F0<X>, X> cons, X nil) {

          return Mu.this.foldr((h, t) -> cons.f(f.f(h), t), nil);
        }

        @Override
        <X> X foldl(F2<X, B, X> acc, X init) {

          return Mu.this.foldl((x, a) -> acc.f(x, f.f(a)), init);
        }
      };
    }
  }

}
