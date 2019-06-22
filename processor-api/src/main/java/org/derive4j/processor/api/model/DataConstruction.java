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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.derive4j.Data;

import static org.derive4j.processor.api.model.DataConstructions.cases;

@Data
public abstract class DataConstruction {

  public interface Cases<R> {

    R multipleConstructors(MultipleConstructors constructors);

    R oneConstructor(DataConstructor constructor);

    R noConstructor();
  }

  public static DataConstruction multipleConstructors(MultipleConstructors constructors) {

    return DataConstructions.multipleConstructors(constructors);
  }

  public static DataConstruction oneConstructor(final DataConstructor constructor) {

    return DataConstructions.oneConstructor(constructor);
  }

  public static DataConstruction noConstructor() {

    return DataConstructions.noConstructor();
  }

  private static final Function<DataConstruction, List<DataConstructor>> getConstructors = cases()
      .multipleConstructors(MultipleConstructorsSupport::getConstructors)
      .oneConstructor(Collections::singletonList)
      .noConstructor(Collections::emptyList);

  DataConstruction() {

  }

  public abstract <R> R match(Cases<R> cases);

  public List<DataConstructor> constructors() {

    return getConstructors.apply(this);
  }

  public boolean isVisitorDispatch() {

    return DataConstructions.getConstructors(this).map(MultipleConstructors::isVisitorDispatch).orElse(false);
  }

}
