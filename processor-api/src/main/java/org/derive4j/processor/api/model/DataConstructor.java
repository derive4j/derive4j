/*
 * Copyright (c) 2019, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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

import java.util.List;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;
import org.derive4j.Data;

import static org.derive4j.processor.api.model.DataConstructors.getArguments;
import static org.derive4j.processor.api.model.DataConstructors.getDeconstructor;
import static org.derive4j.processor.api.model.DataConstructors.getName;
import static org.derive4j.processor.api.model.DataConstructors.getIndex;
import static org.derive4j.processor.api.model.DataConstructors.getReturnedType;
import static org.derive4j.processor.api.model.DataConstructors.getTypeRestrictions;
import static org.derive4j.processor.api.model.DataConstructors.getTypeVariables;

@Data
public abstract class DataConstructor {

  public interface Case<R> {
    R constructor(String name, int index, List<TypeVariable> typeVariables, List<DataArgument> arguments,
        List<TypeRestriction> typeRestrictions, DeclaredType returnedType, DataDeconstructor deconstructor);
  }

  DataConstructor() {

  }

  public abstract <R> R match(Case<R> constructor);

  public String name() {

    return getName(this);
  }

  public int index() {
    return getIndex(this);
  }

  public List<TypeVariable> typeVariables() {

    return getTypeVariables(this);
  }

  public List<DataArgument> arguments() {

    return getArguments(this);
  }

  public DeclaredType returnedType() {

    return getReturnedType(this);
  }

  public DataDeconstructor deconstructor() {

    return getDeconstructor(this);
  }

  public List<TypeRestriction> typeRestrictions() {

    return getTypeRestrictions(this);
  }
}
