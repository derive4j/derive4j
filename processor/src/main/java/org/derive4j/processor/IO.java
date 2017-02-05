package org.derive4j.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.derive4j.processor.Unit.unit;

interface IO<A> {
 A run() throws IOException;

 default IO<Unit> voided() {
   return () -> {
     run();
     return unit;
   };
 }

 interface Effect {
   void run() throws IOException;
  }

  static IO<Unit> effect(Effect e) {
   return () -> {
     e.run();
     return unit;
   };
  }

  static <A, B> IO<List<B>> traverse(List<A> as, Function<A, IO<B>> f) {
    return () -> {
      List<B> bs = new ArrayList<B>(as.size());
      for (A a : as) {
        bs.add(f.apply(a).run());
      }
      return bs;
    };
  }

}
