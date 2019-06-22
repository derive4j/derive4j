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
public enum Flavour {

  JDK {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.Jdk();
    }
  },

  FJ {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.Fj();
    }
  },

  Fugue {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.Fugue();
    }
  },

  Fugue2 {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.Fugue2();
    }
  },

  Javaslang {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.Javaslang();
    }
  },

  Vavr {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.Vavr();
    }
  },

  HighJ {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.HighJ();
    }
  },

  Guava {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.Guava();
    }
  },

  Cyclops {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.Cyclops();
    }
  };

  public interface Cases<R> {
    R Jdk();

    R Fj();

    R Fugue();

    R Fugue2();

    R Javaslang();

    R Vavr();

    R HighJ();

    R Guava();

    R Cyclops();
  }

  public abstract <R> R match(Cases<R> cases);

}
