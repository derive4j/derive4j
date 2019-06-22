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

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeVariable;
import org.derive4j.Data;

import static org.derive4j.processor.api.model.MatchMethods.getElement;
import static org.derive4j.processor.api.model.MatchMethods.getReturnTypeVariable;

@Data
public abstract class MatchMethod {

  public interface Case<R> {
    R matchMethod(ExecutableElement element, TypeVariable returnTypeVariable);
  }

  public static MatchMethod matchMethod(ExecutableElement element, TypeVariable returnTypeVariable) {

    return MatchMethods.matchMethod(element, returnTypeVariable);
  }

  MatchMethod() {

  }

  public abstract <R> R match(Case<R> matchMethod);

  public TypeVariable returnTypeVariable() {

    return getReturnTypeVariable(this);
  }

  public ExecutableElement element() {

    return getElement(this);
  }

}
