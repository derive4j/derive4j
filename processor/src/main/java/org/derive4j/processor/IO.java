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
package org.derive4j.processor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.derive4j.processor.Unit.unit;

interface IO<A> {
  A run() throws IOException;

  default A runUnchecked() throws UncheckedIOException {
    try {
      return run();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  default IO<Unit> voided() {
    return () -> {
      run();
      return unit;
    };
  }

  default <B> IO<B> then(IO<B> ioB) {
    return () -> {
      run();
      return ioB.run();
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
