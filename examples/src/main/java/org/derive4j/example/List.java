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

import fj.Equal;
import fj.Hash;
import fj.Ord;
import fj.Show;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.FieldNames;
import org.derive4j.Instances;

import static org.derive4j.example.Lists.cons;
import static org.derive4j.example.Lists.lazy;
import static org.derive4j.example.Lists.nil;

@Data(@Derive(@Instances({ Show.class, Hash.class, Equal.class, Ord.class})))
public abstract class List<A> {

  public static List<Integer> naturals() {

    return integersFrom(0);
  }

  public static List<Integer> integersFrom(final int s) {

    return iterate(s, i -> i + 1);
  }

  public static List<Integer> range(final int from, int toExclusive) {

    return (from == toExclusive)
        ? nil()
        : cons(from, lazy(() -> range(from + 1, toExclusive)));
  }

  public static <A> List<A> iterate(A seed, UnaryOperator<A> op) {

    return lazy(() -> cons(seed, iterate(op.apply(seed), op)));
  }

  List() {

  }

  public abstract <X> X list(Supplier<X> nil, @FieldNames({ "head", "tail" }) BiFunction<A, List<A>, X> cons);

  public final <B> List<B> map(Function<A, B> f) {

    return lazy(() -> list(Lists::nil, (h, tail) -> cons(f.apply(h), tail.map(f))));
  }

  public final List<A> append(final List<A> list) {

    return lazy(() -> list(() -> list, (head, tail) -> cons(head, tail.append(list))));
  }

  public final List<A> filter(Predicate<A> p) {

    return lazy(() -> list(Lists::nil, (h, tail) -> p.test(h)
        ? cons(h, tail.filter(p))
        : tail.filter(p)));
  }

  public final <B> List<B> bind(Function<A, List<B>> f) {

    return lazy(() -> list(Lists::nil, (h, t) -> f.apply(h).append(t.bind(f))));
    // alternative implementation using foldRight:
    //return lazy(() -> foldRight((h, tail) -> f.apply(h).append(lazy(tail)), nil()));
  }

  public final List<A> take(int n) {

    return (n <= 0)
        ? nil()
        : lazy(() -> list(Lists::nil, (head, tail) -> cons(head, tail.take(n - 1))));
  }

  public final void forEach(Consumer<A> effect) {
    // a bit ugly due to lack of TCO
    class ConsVisitor implements BiFunction<A, List<A>, Boolean> {
      List<A> l = List.this;

      @Override
      public Boolean apply(A head, List<A> tail) {

        effect.accept(head);
        l = tail;
        return true;
      }
    }
    ConsVisitor consVisitor = new ConsVisitor();
    while (consVisitor.l.list(() -> false, consVisitor)) {
    }
  }

  public final <B> B foldLeft(final BiFunction<B, A, B> f, final B zero) {
    // again, ugly due to lack of TCO
    class Acc implements Consumer<A> {
      B acc = zero;

      @Override
      public void accept(A a) {

        acc = f.apply(acc, a);
      }
    }
    Acc acc = new Acc();
    forEach(acc);
    return acc.acc;
  }

  public final int length() {

    return foldLeft((i, a) -> i + 1, 0);
  }

  public final <B> B foldRight(final BiFunction<A, Supplier<B>, B> f, final B zero) {

    return Lists.cata(() -> zero, f).apply(this);
  }

  public static void main(String[] args) {
    List<Integer> naturals = naturals().take(100);
    List<Integer> naturals2 = naturals().take(100).map(i-> i-1);
    Lists.listShow(Show.intShow).println(naturals);
    System.out.println(Lists.listEqual(Equal.intEqual).eq(naturals, naturals));
    System.out.println(Lists.listOrd(Ord.intOrd).compare(naturals, naturals2));
  }

}
