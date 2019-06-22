/*
 * Copyright (c) 2019, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * This file is part of "Derive4J - Annotations API".
 *
 * "Derive4J - Annotations API" is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * "Derive4J - Annotations API" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with "Derive4J - Annotations API".  If not, see <http://www.gnu.org/licenses/>.
 */
package org.derive4j;

@Data
public enum Make {

  lambdaVisitor {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.lambdaVisitor();
    }
  },

  constructors {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.constructors();
    }
  },

  lazyConstructor {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.lazyConstructor();
    }
  },

  casesMatching {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.casesMatching();
    }
  },

  caseOfMatching {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.caseOfMatching();
    }
  },

  getters {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.getters();
    }
  },

  modifiers {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.modifiers();
    }
  },

  catamorphism {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.catamorphism();
    }
  },

  factory {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.factory();
    }
  };

  public interface Cases<R> {
    R lambdaVisitor();

    R constructors();

    R lazyConstructor();

    R casesMatching();

    R caseOfMatching();

    R getters();

    R modifiers();

    R catamorphism();

    R factory();
  }

  public abstract <R> R match(Cases<R> cases);

}
