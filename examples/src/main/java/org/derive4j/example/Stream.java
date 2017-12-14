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

import fj.F;
import fj.F0;
import fj.F2;
import org.derive4j.Data;
import org.derive4j.FieldNames;
import org.derive4j.Flavour;

public abstract class Stream<A> {

  public static Stream<Integer> range(final int from, int toExclusive) {

    return nu(from, s -> (s == toExclusive)
        ? Steps.done()
        : Steps.yield(s, s + 1));
  }

  public static <A> Stream<A> mu(Mu<A> mu) {

    return new Stream<A>() {
      @Override
      <X> X match(Cases<A, X> cases) {

        return cases.build(mu);
      }
    };
  }

  public static <S, A> Stream<A> nu(S init, F<S, Step<A, S>> stepper) {

    return new Stream<A>() {
      @Override
      <X> X match(Cases<A, X> cases) {

        return cases.unfold(init, stepper);
      }
    };
  }

  public final <B> Stream<B> map(F<A, B> f) {

    return match(new Cases<A, Stream<B>>() {
      @Override
      public Stream<B> build(Mu<A> mu) {

        return mu(mu.map(f));
      }

      @Override
      public <S> Stream<B> unfold(S init, F<S, Step<A, S>> stepper) {

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
          S s = init;
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
