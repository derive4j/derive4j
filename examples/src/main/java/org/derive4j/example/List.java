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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
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

@Data(@Derive(extend = ListMethods.class, value = @Instances({ Show.class, Hash.class, Equal.class, Ord.class })))
public abstract class List<A> {

  public static List<Integer> naturals() {

    return integersFrom(0);
  }

  public static List<Integer> integersFrom(final int s) {

    return iterate(s, i -> i + 1);
  }

  public static List<Integer> range(final int from, int toExclusive) {
    IntFunction<List<Integer>> next = new IntFunction<List<Integer>>() {
      @Override
      public List<Integer> apply(int from) {
        return (from >= toExclusive) ? nil() : cons(from, lazy(() -> apply(from + 1)));
      }
    };
    return next.apply(from);
  }

  public final Option<A> find(Predicate<A> p) {
    return Lists.<A, Option<A>>cata(
        Options::none,
        (a, tail) -> p.test(a) ? Options.some(a) : tail, Options::lazy).apply(this);
  }

  public static <A> List<A> iterate(A seed, UnaryOperator<A> op) {

    return lazy(() -> cons(seed, iterate(op.apply(seed), op)));
  }

  List() {

  }

  public abstract <X> X list(Supplier<X> nil, @FieldNames({ "head", "tail" }) BiFunction<A, List<A>, X> cons);

  public final <B> List<B> map(Function<A, B> f) {
    return foldRight(Lists::nil, (h, tail) -> cons(f.apply(h), tail), Lists::lazy);
  }

  public final List<A> append(final List<A> list) {
    return foldRight(() -> list, Lists::cons, Lists::lazy);
  }

  public final List<A> filter(Predicate<A> p) {
    return Lists.<A, List<A>>cata(
        Lists::nil,
        (a, tail) -> p.test(a) ? cons(a, tail) : tail, Lists::lazy).apply(this);
  }

  public final <B> List<B> bind(Function<A, List<B>> f) {
    return foldRight(Lists::nil, (h, tail) -> f.apply(h).append(tail), Lists::lazy);
  }

  public final List<A> take(int n) {

    return (n <= 0) ? nil() : lazy(() -> list(Lists::nil, (head, tail) -> cons(head, tail.take(n - 1))));
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

  public final <B> B foldRight(Supplier<B> zero, BiFunction<A, B, B> f, Function<Supplier<B>, B> delay) {
    return Lists.cata(zero, f, delay).apply(this);
  }

  public static void main(String[] args) {
    List<Integer> naturals = naturals().take(20000).filter(i -> i > 10000).take(100);
    System.out.println(naturals.length());
    List<Integer> naturals2 = naturals().take(100).map(i -> i - 1);
    Lists.listShow(Show.intShow).println(naturals);
    System.out.println(Lists.listEqual(Equal.intEqual).eq(naturals, naturals));
    System.out.println(Lists.listOrd(Ord.intOrd).compare(naturals, naturals2));
  }

}
