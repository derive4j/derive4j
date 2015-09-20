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

import java.util.Collections;
import java.util.List;

public abstract class DataConstruction {

  private DataConstruction() {
  }

  ;

  public static DataConstruction multipleConstructors(DataConstructors constructors) {
    return new DataConstruction() {
      @Override
      public <R> R match(final Cases<R> cases) {
        return cases.multipleConstructors(constructors);
      }
    };
  }

  public static DataConstruction oneConstructor(final DataConstructor constructor) {
    return new DataConstruction() {
      @Override
      public <R> R match(final Cases<R> cases) {
        return cases.oneConstructor(constructor);
      }
    };
  }

  public static DataConstruction noConstructor() {
    return new DataConstruction() {
      @Override
      public <R> R match(final Cases<R> cases) {
        return cases.noConstructor();
      }
    };
  }

  public abstract <R> R match(Cases<R> cases);

  public List<DataConstructor> constructors() {
    return match(new Cases<List<DataConstructor>>() {
      @Override
      public List<DataConstructor> multipleConstructors(DataConstructors constructors) {
        return constructors.constructors();
      }

      @Override
      public List<DataConstructor> oneConstructor(DataConstructor constructor) {
        return Collections.singletonList(constructor);
      }

      @Override
      public List<DataConstructor> noConstructor() {
        return Collections.emptyList();
      }
    });
  }

  public boolean isVisitorDispatch() {
    return match(new Cases<Boolean>() {
      @Override
      public Boolean multipleConstructors(DataConstructors constructors) {
        return constructors.isVisitorDispatch();
      }

      @Override
      public Boolean oneConstructor(DataConstructor constructor) {
        return false;
      }

      @Override
      public Boolean noConstructor() {
        return false;
      }
    });
  }


  public interface Cases<R> {

    R multipleConstructors(DataConstructors constructors);

    R oneConstructor(DataConstructor constructor);

    R noConstructor();
  }

}
