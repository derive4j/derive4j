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

public class Bench {

  static final int COUNT = 500000;
  static final int ITERATIONS = 200;
  static final int WARMUP = 100;

  public static void main(String[] args) {

    // Average time after 200 iterations: 17.869925 ms
    timed(() -> List.range(0, COUNT).length());

    // Average time after 200 iterations: 26.725065 ms
    timed(() -> javaslang.collection.Stream.range(0, COUNT).length());

    // Average time after 200 iterations: 24.768288 ms
    timed(() -> fj.data.Stream.range(0, COUNT).length());

    // Average time after 200 iterations: 4.771267 ms
    timed(() -> java.util.stream.Stream.iterate(0, i -> i + 1).limit(COUNT).reduce(0, (i1, i2) -> i1 + 1));

    timed(() -> Stream.range(0, COUNT).length());
  }

  static void timed(Runnable stuff) {

    for (int i = 0; i < WARMUP; i++) {
      System.gc(); // try to get rid of potential GC pauses
      stuff.run();
    }
    long vs = 0;
    for (int i = 0; i < ITERATIONS; i++) {
      System.gc(); // try to get rid of potential GC pauses
      long t = System.nanoTime();
      stuff.run();
      vs += (System.nanoTime() - t);
    }
    System.out.printf("Average time after %d iterations: %f ms\n", ITERATIONS, (vs / 1000000.0) / ITERATIONS);
  }

}