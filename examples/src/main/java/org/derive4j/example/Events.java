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

import org.derive4j.*;

public class Events {

  @Data(flavour = Flavour.Cyclops)
  interface EventV1 {

    interface Cases<R> {
      R newItem(Long ref, String itemName);

      R itemRemoved(Long ref);
    }

    <R> R match(Cases<R> cases);
  }

  @Data(flavour = Flavour.Cyclops)
  interface EventV2 {

    interface Cases<R> extends EventV1.Cases<R> { // extends V1 with:

      // new `initialStock` field in `newItem` event:
      R newItem(Long ref, String itemName, int initialStock);

      // default to 0 for old events:
      @Override
      default R newItem(Long ref, String itemName) {
        return newItem(ref, itemName, 0);
      }

      // new event:
      R itemRenamed(Long ref, String newName);
    }

    <R> R match(Cases<R> cases);

    static EventV2 fromV1(EventV1 v1Event) {
      // Events are functions!
      return v1Event::match;
    }
  }

}
