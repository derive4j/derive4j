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

import fj.Ord;
import fj.data.Option;
import fj.data.List;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Flavour;
import org.derive4j.Instances;
import org.derive4j.hkt.__;

@Data(flavour = Flavour.FJ)
@Derive(@Instances(Ord.class))
public abstract class Property<T> implements __<Property.µ, T> {
  public enum µ {
  }

  Property() {
  }

  interface Cases<T, R> {
    R Simple(String name, Option<Integer> value, List<String> params);

    R Multi(String name, List<Integer> values, List<String> params);
  }

  public abstract <R> R match(Cases<T, R> cases);
}
