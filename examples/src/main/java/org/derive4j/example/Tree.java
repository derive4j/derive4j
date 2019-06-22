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
import fj.F2;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.FieldNames;
import org.derive4j.Flavour;
import org.derive4j.Instances;

@Data(flavour = Flavour.FJ)
@Derive(@Instances(Equal.class))
public abstract class Tree<A> {
  Tree() {
  }

  public abstract <X> X tree(@FieldNames({ "root", "subForest" }) F2<A, List<Tree<A>>, X> tree);

  static <A> Equal<List<A>> listEqual(Equal<A> aEqual) {
    return Lists.listEqual(aEqual);
  }

}
