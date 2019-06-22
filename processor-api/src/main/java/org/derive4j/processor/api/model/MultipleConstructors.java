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
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import org.derive4j.Data;
import org.derive4j.Derive;

import static java.util.Collections.unmodifiableList;
import static org.derive4j.Visibility.Smart;
import static org.derive4j.processor.api.model.MultipleConstructorsSupport.cases;
import static org.derive4j.processor.api.model.MultipleConstructorsSupport.getConstructors;

@Data(@Derive(inClass = "MultipleConstructorsSupport", withVisibility = Smart))
public abstract class MultipleConstructors {

  public interface Cases<R> {

    R visitorDispatch(VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors);

    R functionsDispatch(List<DataConstructor> constructors);

  }

  public static MultipleConstructors visitorDispatch(VariableElement visitorParam, DeclaredType visitorType,
      List<DataConstructor> constructors) {

    return MultipleConstructorsSupport.visitorDispatch0(visitorParam, visitorType,
        unmodifiableList(new ArrayList<>(constructors)));
  }

  public static MultipleConstructors functionsDispatch(List<DataConstructor> constructors) {

    return MultipleConstructorsSupport.functionsDispatch0(unmodifiableList(new ArrayList<>(constructors)));
  }

  MultipleConstructors() {

  }

  public abstract <R> R match(Cases<R> cases);

  public boolean isVisitorDispatch() {

    return cases().visitorDispatch((visitorParam, visitorType, constructors) -> true)
        .functionsDispatch(__ -> false)
        .apply(this);
  }

  public List<DataConstructor> constructors() {

    return getConstructors(this);
  }

}
