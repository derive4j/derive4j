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
import java.util.Optional;
import java.util.function.Function;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.ExportAsPublic;
import org.derive4j.FieldNames;
import org.derive4j.Instances;
import org.derive4j.Visibility;

@Data(@Derive(withVisibility = Visibility.Smart, value = @Instances({ Show.class, Hash.class, Equal.class,
    Ord.class })))
public abstract class PersonName {

  PersonName() {

  }

  public abstract <R> R match(@FieldNames("value") Function<String, R> Name);

  /**
   * This method is reexported with public modifier as
   * {@link PersonNames#parseName(String)}. Also the javadoc is copied over.
   *
   * @param value
   *          unparse string
   * @return a valid {@link PersonName}, maybe.
   */
  @ExportAsPublic
  static Optional<PersonName> parseName(String value) {
    // A name cannot be only spaces, must not start or and with space.
    return (value.trim().isEmpty() || value.endsWith(" ") || value.startsWith(" "))
        ? Optional.empty()
        : Optional.of(PersonNames.Name0(value));
  }

}
