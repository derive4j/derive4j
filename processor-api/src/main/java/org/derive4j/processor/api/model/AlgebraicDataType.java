/*
 * Copyright (c) 2015, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * This file is part of "Derive4J - Processor API".
 *
 * "Derive4J - Processor API" is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * "Derive4J - Processor API" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with "Derive4J - Processor API".  If not, see <http://www.gnu.org/licenses/>.
 */
package org.derive4j.processor.api.model;

import java.util.ArrayList;
import java.util.List;
import org.derive4j.Data;
import org.derive4j.Derive;

import static java.util.Collections.unmodifiableList;
import static org.derive4j.Visibility.Smart;
import static org.derive4j.processor.api.model.AlgebraicDataTypes.getDataConstruction;
import static org.derive4j.processor.api.model.AlgebraicDataTypes.getFields;
import static org.derive4j.processor.api.model.AlgebraicDataTypes.getMatchMethod;
import static org.derive4j.processor.api.model.AlgebraicDataTypes.getTypeConstructor;

@Data(@Derive(withVisibility = Smart))
public abstract class AlgebraicDataType {

  AlgebraicDataType() {

  }

  public static AlgebraicDataType adt(TypeConstructor typeConstructor, MatchMethod matchMethod, DataConstruction dataConstruction,
      List<DataArgument> fields) {

    return AlgebraicDataTypes.adt0(typeConstructor, matchMethod, dataConstruction, unmodifiableList(new ArrayList<>(fields)));
  }

  public abstract <R> R match(Case<R> adt);

  public TypeConstructor typeConstructor() {

    return getTypeConstructor(this);
  }

  public MatchMethod matchMethod() {

    return getMatchMethod(this);
  }

  public DataConstruction dataConstruction() {

    return getDataConstruction(this);
  }

  public List<DataArgument> fields() {

    return getFields(this);
  }

  public interface Case<R> {
    R adt(TypeConstructor typeConstructor, MatchMethod matchMethod, DataConstruction dataConstruction, List<DataArgument> fields);
  }

}
