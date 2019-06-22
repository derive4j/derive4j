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
package org.derive4j.processor.api;

import java.util.Collections;
import java.util.List;
import org.derive4j.Data;
import org.derive4j.ExportAsPublic;

import static java.util.Collections.singletonList;

@Data
public abstract class DeriveMessage {

  public interface Case<R> {
    R message(String text, List<MessageLocalization> localizations);
  }

  DeriveMessage() {
  }

  public abstract <R> R match(Case<R> message);

  @ExportAsPublic
  static DeriveMessage message(String msg, MessageLocalization localization) {

    return DeriveMessages.message(msg, singletonList(localization));
  }

  @ExportAsPublic
  static DeriveMessage message(String msg) {

    return DeriveMessages.message(msg, Collections.emptyList());
  }

}
