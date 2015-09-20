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

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;
import java.util.List;

public abstract class TypeConstructor {

  private TypeConstructor() {}

  public interface Case<R> {
    R typeConstructor(TypeElement typeElement, DeclaredType declaredType, List<TypeVariable> typeVariables);
  }

  public abstract <R> R match(Case<R> typeConstructor);

  public static TypeConstructor typeConstructor(TypeElement typeElement, DeclaredType declaredType, List<TypeVariable> typeVariables) {
    return new TypeConstructor() {
      @Override
      public <R> R match(Case<R> typeConstructor) {
        return typeConstructor.typeConstructor(typeElement, declaredType, typeVariables);
      }
    };
  }

  public TypeElement typeElement() {
    return match((typeElement, declaredType, typeVariables) -> typeElement);
  }

  public DeclaredType declaredType() {
    return match((typeElement, declaredType, typeVariables) -> declaredType);
  }

  public List<TypeVariable> typeVariables() {
    return match((typeElement, declaredType, typeVariables) -> typeVariables);
  }

}
