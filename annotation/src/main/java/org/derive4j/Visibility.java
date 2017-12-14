/*
 * Copyright (c) 2017, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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

@Data(@Derive(inClass = "Visibilities"))
public enum Visibility {

  Same {
    @Override
    public <R> R match(VisibilityCases<R> cases) {

      return cases.Same();
    }
  },

  Package {
    @Override
    public <R> R match(VisibilityCases<R> cases) {

      return cases.Package();
    }
  },

  Smart {
    @Override
    public <R> R match(VisibilityCases<R> cases) {

      return cases.Smart();
    }
  };

  public interface VisibilityCases<R> {
    R Same();

    R Package();

    R Smart();
  }

  public abstract <R> R match(VisibilityCases<R> cases);

}
