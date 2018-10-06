/*
 * Copyright (c) 2018, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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
