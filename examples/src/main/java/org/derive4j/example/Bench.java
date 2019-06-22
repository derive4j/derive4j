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

import java.util.function.IntSupplier;

public class Bench {

  static final int COUNT      = 50000;
  static final int ITERATIONS = 25;
  static final int WARMUP     = 100;
  static final int FILTER     = 10000;
  static final int BIND       = 10;

  public static void main(String[] args) throws InterruptedException {

    // Derive4J Lazy List: Average time: 82.222062 ms
    timed(Bench::derive4JList);

    // Vavr Lazy List: Average time: 86.286897 ms
    timed(Bench::vavrStream);

    // FJ Ephemeral List: Average time: 82.043869 ms
    timed(Bench::fjStream);
  }

  static int derive4JList() {
    return List.range(0, COUNT)
        .bind(i -> List.range(i, i + BIND))
        .filter(i -> i >= FILTER)
        .map(i -> i + 1)
        .foldLeft((i, a) -> i + a, 0);
  }

  static int vavrStream() {
    return io.vavr.collection.Stream.range(0, COUNT)
        .flatMap(i -> io.vavr.collection.Stream.range(i, i + BIND))
        .filter(i -> i >= FILTER)
        .map(i -> i + 1)
        .foldLeft(0, (i, a) -> i + a);
  }

  static int fjStream() {
    return fj.data.Stream.range(0, COUNT)
        .bind(i -> fj.data.Stream.range(i, i + BIND))
        .filter(i -> i >= FILTER)
        .map(i -> i + 1)
        .foldLeft((i, a) -> i + a, 0);
  }

  static void timed(IntSupplier operation) throws InterruptedException {
    int count = 0;
    for (int i = 0; i < WARMUP; i++) {
      count += operation.getAsInt();
    }
    long vs = 0;
    for (int i = 0; i < ITERATIONS; i++) {
      Thread.sleep(100);
      System.gc(); // try to get rid of potential GC pauses
      Thread.sleep(200);
      long t = System.nanoTime();
      count += operation.getAsInt();
      vs += (System.nanoTime() - t);
    }
    System.out.printf("Result: %d - Average time after %d iterations: %f ms\n", count, ITERATIONS,
        (vs / 1000000.0) / ITERATIONS);
  }

}
