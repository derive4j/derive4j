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

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;
import org.derive4j.Data;
import org.derive4j.Derive;

import static java.util.Collections.unmodifiableList;
import static org.derive4j.Visibility.Smart;
import static org.derive4j.processor.api.model.TypeConstructors.getDeclaredType;
import static org.derive4j.processor.api.model.TypeConstructors.getTypeElement;
import static org.derive4j.processor.api.model.TypeConstructors.getTypeVariables;

@Data(@Derive(withVisibility = Smart))
public abstract class TypeConstructor {

  public interface Case<R> {
    R typeConstructor(TypeElement typeElement, DeclaredType declaredType, List<TypeVariable> typeVariables);
  }

  public static TypeConstructor typeConstructor(TypeElement typeElement, DeclaredType declaredType,
      List<TypeVariable> typeVariables) {

    return TypeConstructors.typeConstructor0(typeElement, declaredType,
        unmodifiableList(new ArrayList<>(typeVariables)));
  }

  TypeConstructor() {

  }

  public abstract <R> R match(Case<R> typeConstructor);

  public TypeElement typeElement() {

    return getTypeElement(this);
  }

  public DeclaredType declaredType() {

    return getDeclaredType(this);
  }

  public List<TypeVariable> typeVariables() {

    return getTypeVariables(this);
  }

}
