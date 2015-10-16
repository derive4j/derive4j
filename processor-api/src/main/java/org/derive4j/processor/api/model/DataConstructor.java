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

import javax.lang.model.type.TypeVariable;
import java.util.List;

public abstract class DataConstructor {

  private DataConstructor() {
  }

  public static DataConstructor constructor(String name, List<DataArgument> arguments,
                                            List<TypeVariable> typeVariables, List<TypeRestriction> typeRestrictions,
                                            DataDeconstructor deconstructor) {
    return new DataConstructor() {
      @Override
      public <R> R match(Case<R> dataConstructor) {
        return dataConstructor.constructor(name, arguments, typeVariables, typeRestrictions, deconstructor);
      }
    };
  }

  public abstract <R> R match(Case<R> constructor);

  public String name() {
    return match((name, arguments, typeVariables, typeRestrictions, deconstructor) -> name);
  }


  public List<DataArgument> arguments() {
    return match((name, arguments, typeVariables, typeRestrictions, deconstructor) -> arguments);
  }


  public List<TypeVariable> typeVariables() {
    return match((name, arguments, typeVariables, typeRestrictions, deconstructor) -> typeVariables);
  }

  public DataDeconstructor deconstructor() {
    return match((name, arguments, typeVariables, typeRestrictions, deconstructor) -> deconstructor);
  }


  public List<TypeRestriction> typeRestrictions() {
    return match((name, arguments, typeVariables, typeRestrictions, deconstructor) -> typeRestrictions);
  }

  public interface Case<R> {
    R constructor(String name, List<DataArgument> arguments, List<TypeVariable> typeVariables, List<TypeRestriction> typeRestrictions, DataDeconstructor deconstructor);
  }
}
