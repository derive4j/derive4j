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

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import org.derive4j.Data;

@Data
public abstract class MessageLocalization {

  public interface Cases<R> {
    R onElement(Element element);

    R onAnnotation(Element element, AnnotationMirror annotation);

    R onAnnotationValue(Element element, AnnotationMirror annotation, AnnotationValue annotationValue);
  }

  public static MessageLocalization onElement(Element e) {

    return MessageLocalizations.onElement(e);
  }

  public static MessageLocalization onAnnotation(Element e, AnnotationMirror a) {

    return MessageLocalizations.onAnnotation(e, a);
  }

  public static MessageLocalization onAnnotationValue(Element e, AnnotationMirror a, AnnotationValue v) {

    return MessageLocalizations.onAnnotationValue(e, a, v);
  }

  MessageLocalization() {

  }

  public abstract <R> R match(Cases<R> cases);

}
