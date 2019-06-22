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

import com.squareup.javapoet.ClassName;
import java.util.Optional;
import org.derive4j.Data;

import static org.derive4j.processor.api.model.DeriveTargetClasses.getClassName;
import static org.derive4j.processor.api.model.DeriveTargetClasses.getExtend;
import static org.derive4j.processor.api.model.DeriveTargetClasses.getVisibility;

@Data
public abstract class DeriveTargetClass {

  DeriveTargetClass() {
  }

  public abstract <X> X match(Case<X> Case);

  public final ClassName className() {
    return getClassName(this);
  }

  public final DeriveVisibility visibility() {
    return getVisibility(this);
  }

  public final Optional<ClassName> extend() {
    return getExtend(this);
  }

  interface Case<X> {
    X TargetClass(ClassName className, DeriveVisibility visibility, Optional<ClassName> extend);
  }

}
