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

import fj.Equal;
import fj.Hash;
import fj.Ord;
import fj.Show;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Instances;

@Data(@Derive(@Instances({ Show.class, Hash.class, Equal.class, Ord.class })))
public enum Country {

  Fr {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.Fr();
    }
  },
  Ch {
    @Override
    public <R> R match(Cases<R> cases) {

      return cases.Ch();
    }
  };

  public abstract <R> R match(Cases<R> cases);

  interface Cases<R> {
    R Fr();

    R Ch();
  }
}
