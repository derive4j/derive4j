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

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import java.util.List;

public abstract class DataConstructors {

  private DataConstructors() {
  }

  public static DataConstructors visitorDispatch(VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors) {
    return new DataConstructors() {
      @Override
      public <R> R match(Cases<R> cases) {
        return cases.visitorDispatch(visitorParam, visitorType, constructors);
      }
    };
  }

  public static DataConstructors functionsDispatch(List<DataConstructor> constructors) {
    return new DataConstructors() {
      @Override
      public <R> R match(Cases<R> cases) {
        return cases.functionsDispatch(constructors);
      }
    };
  }

  public abstract <R> R match(Cases<R> cases);

  public interface Cases<R> {

    R visitorDispatch(VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors);

    R functionsDispatch(List<DataConstructor> constructors);

  }

  public boolean isVisitorDispatch() {
    return match(new Cases<Boolean>() {
      @Override
      public Boolean visitorDispatch(VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors) {
        return true;
      }

      @Override
      public Boolean functionsDispatch(List<DataConstructor> constructors) {
        return false;
      }
    });
  }

  public List<DataConstructor> constructors() {
    return match(new Cases<List<DataConstructor>>() {
      @Override
      public List<DataConstructor> visitorDispatch(VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors) {
        return constructors;
      }

      @Override
      public List<DataConstructor> functionsDispatch(List<DataConstructor> constructors) {
        return constructors;
      }
    });
  }

}
