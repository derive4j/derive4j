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

import fj.Hash;
import fj.Show;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Instances;
import org.derive4j.hkt.TypeEq;

import static java.lang.System.out;
import static org.derive4j.example.Terms.If;
import static org.derive4j.example.Terms.IsZero;
import static org.derive4j.example.Terms.Succ;
import static org.derive4j.example.Terms.Zero;

// Implementation of a pseudo-GADT in Java, translating the examples from
// http://www.cs.ox.ac.uk/ralf.hinze/publications/With.pdf
// The technique presented below is, in fact, just an encoding of a normal Algebraic Data Type
// using a variation of the visitor pattern + the application of the Yoneda lemma to make it
// isomorphic to the targeted 'GADT'.
// Highlights:
// -> no cast and no subtyping.
// -> all of the eval function logic is static and not scattered all around Term subclasses.
@Data(@Derive(@Instances({ Show.class, Hash.class})))
public abstract class Term<T> {

  public static <T> T eval(final Term<T> term) {

    return Terms.caseOf(term).
        Zero(__ -> __.coerce(0)).
        Succ((t, __) -> __.coerce(eval(t) + 1)).
        Pred((t, __) -> __.coerce(eval(t) - 1)).
        IsZero((t, __) -> __.coerce(eval(t) == 0)).
        If((cond, then, otherwise) -> eval(cond)
            ? eval(then)
            : eval(otherwise));
  }

  public static void main(final String[] args) {

    Term<Integer> one = Succ(Zero());
    out.println(eval(one)); // "1"
    out.println(eval(IsZero(one))); // "false"
    // IsZero(IsZero(one)); // does not compile:
    // "The method IsZero(Term<Integer>) in the type Term<T> is not
    // applicable for the arguments (Term<Boolean>)"
    out.println(eval(If(IsZero(one), Zero(), one))); // "1"
    Term<Boolean> True = IsZero(Zero());
    Term<Boolean> False = IsZero(one);
    out.println(eval(If(True, True, False))); // "true"
    // out.println(prettyPrint(If(True, True, False), 0)); // "if IsZero(0)
    //  then IsZero(0)
    //  else IsZero(Succ(0))"
  }

  Term() {

  }

  public abstract <X> X match(Cases<T, X> cases);

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  @Override
  public abstract String toString();

  interface Cases<A, R> {
    R Zero(TypeEq<Integer, A> id);

    R Succ(Term<Integer> pred, TypeEq<Integer, A> id);

    R Pred(Term<Integer> succ, TypeEq<Integer, A> id);

    R IsZero(Term<Integer> a, TypeEq<Boolean, A> id);

    R If(Term<Boolean> cond, Term<A> then, Term<A> otherwise);
  }
}
