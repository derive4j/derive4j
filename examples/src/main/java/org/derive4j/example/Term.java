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
@Data(@Derive(@Instances({ Show.class, Hash.class })))
public abstract class Term<T> {

  public static <T> T eval(final Term<T> term) {

    return Terms.caseOf(term)
        .Zero(__ -> __.coerce(0))
        .Succ((t, __) -> __.coerce(eval(t) + 1))
        .Pred((t, __) -> __.coerce(eval(t) - 1))
        .IsZero((t, __) -> __.coerce(eval(t) == 0))
        .If((cond, then, otherwise) -> eval(cond) ? eval(then) : eval(otherwise));
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
    // then IsZero(0)
    // else IsZero(Succ(0))"
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
