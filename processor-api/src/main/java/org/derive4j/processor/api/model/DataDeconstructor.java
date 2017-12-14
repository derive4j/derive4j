/*
 * Copyright (c) 2017, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import org.derive4j.Data;

import static org.derive4j.processor.api.model.DataDeconstructors.getVisitorMethod;
import static org.derive4j.processor.api.model.DataDeconstructors.getVisitorMethodType;
import static org.derive4j.processor.api.model.DataDeconstructors.getVisitorParam;
import static org.derive4j.processor.api.model.DataDeconstructors.getVisitorType;

@Data
public abstract class DataDeconstructor {

  public interface Case<R> {
    R deconstructor(VariableElement visitorParam, DeclaredType visitorType, ExecutableElement visitorMethod,
        ExecutableType visitorMethodType);
  }

  public static DataDeconstructor deconstructor(VariableElement visitorParam, DeclaredType visitorType,
      ExecutableElement visitorMethod, ExecutableType visitorMethodType) {

    return DataDeconstructors.deconstructor(visitorParam, visitorType, visitorMethod, visitorMethodType);
  }

  DataDeconstructor() {

  }

  public ExecutableElement visitorMethod() {

    return getVisitorMethod(this);
  }

  public ExecutableType visitorMethodType() {

    return getVisitorMethodType(this);
  }

  public VariableElement visitorParam() {

    return getVisitorParam(this);
  }

  public DeclaredType visitorType() {

    return getVisitorType(this);
  }

  public abstract <R> R match(Case<R> deconstructor);

}
