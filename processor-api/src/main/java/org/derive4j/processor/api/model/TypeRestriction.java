/**
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

import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

public abstract class TypeRestriction {

  private TypeRestriction() {
  }

  public static TypeRestriction typeRestriction(TypeVariable typeParameter, TypeMirror type, DataArgument idFunction) {
    return new TypeRestriction() {
      @Override
      public <R> R match(Case<R> typeRestriction) {
        return typeRestriction.typeRestriction(typeParameter, type, idFunction);
      }
    };
  }

  public TypeVariable restrictedTypeParameter() {
    return match((typeParameter, type, idFunction) -> typeParameter);
  }

  public TypeMirror type() {
    return match((typeParameter, type, idFunction) -> type);
  }

  public DataArgument dataArgument() {
    return match((typeParameter, type, idFunction) -> idFunction);
  }

  public abstract <R> R match(Case<R> typeRestriction);

  public interface Case<R> {
    R typeRestriction(TypeVariable typeParameter, TypeMirror type, DataArgument idFunction);
  }

}
