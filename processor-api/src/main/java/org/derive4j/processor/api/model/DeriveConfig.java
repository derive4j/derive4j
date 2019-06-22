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
import java.util.Map;
import java.util.Set;
import org.derive4j.ArgOption;
import org.derive4j.Data;
import org.derive4j.Flavour;
import org.derive4j.Make;

import static org.derive4j.processor.api.model.DeriveConfigs.getArgOptions;
import static org.derive4j.processor.api.model.DeriveConfigs.getDerivedInstances;
import static org.derive4j.processor.api.model.DeriveConfigs.getFlavour;
import static org.derive4j.processor.api.model.DeriveConfigs.getMakes;
import static org.derive4j.processor.api.model.DeriveConfigs.getTargetClass;

@Data
public abstract class DeriveConfig {

  public interface Case<X> {
    X Config(Flavour flavour, DeriveTargetClass targetClass, Set<Make> makes, Set<ArgOption> argOptions,
        Map<ClassName, DerivedInstanceConfig> derivedInstances);
  }

  DeriveConfig() {
  }

  public abstract <X> X match(Case<X> Case);

  public final Flavour flavour() {
    return getFlavour(this);
  }

  public final DeriveTargetClass targetClass() {
    return getTargetClass(this);
  }

  public final Set<Make> makes() {
    return getMakes(this);
  }

  public final Set<ArgOption> argOptions() {
    return getArgOptions(this);
  }

  public final Map<ClassName, DerivedInstanceConfig> derivedInstances() {
    return getDerivedInstances(this);
  }

}
