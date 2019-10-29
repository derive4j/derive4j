package org.derive4j.example;

import org.derive4j.*;
import org.derive4j.hkt.__;

@Data(flavour = Flavour.FJ, value = @Derive(inClass = "_Exists", withVisibility = Visibility.Smart, make = {
    Make.constructors, Make.casesMatching, Make.caseOfMatching }))
abstract class Exists<T> implements __<Exists.µ, T> {
  public enum µ {
  }

  interface Cases<R, T> {
    R Exists(ExistsImpl<?, T> exists);

    R Exists2(ExistsImpl2<T, ?, T> exists2);
  }

  abstract <R> R match(Cases<R, T> cases);

  static final class ExistsImpl<A, B> {
    final A a;
    final B b;

    ExistsImpl(A a, B b) {
      this.a = a;
      this.b = b;
    }
  }

  static final class ExistsImpl2<A, B, C> {
    final A a;
    final B b;
    final C c;

    ExistsImpl2(A a, B b, C c) {
      this.a = a;
      this.b = b;
      this.c = c;
    }
  }

  @ExportAsPublic
  static <T> Exists<T> now(T t) {
    return _Exists.Exists(new ExistsImpl<>(t, t));
  }
}
