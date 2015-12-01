/*
 * Copyright (c) 2015, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * This file is part of "Derive4J - Annotation Processor".
 *
 * "Derive4J - Annotation Processor" is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * "Derive4J - Annotation Processor" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "Derive4J - Annotation Processor".  If not, see <http://www.gnu.org/licenses/>.
 */
package org.derive4j.processor.derivator;

import org.derive4j.Flavour;
import org.derive4j.Flavours;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.derive4j.processor.derivator.FlavourImpl.EitherType.eitherType;
import static org.derive4j.processor.derivator.FlavourImpl.OptionType.optionTye;

public final class FlavourImpl {

  public static TypeElement findF0(Flavour flavour, Elements elements) {
    return elements.getTypeElement(
        Flavours.cases()
            .Jdk(() -> Supplier.class.getName())
            .Fj(() -> "fj.F0")
            .Fugue(() -> Supplier.class.getName())
            .Fugue2(() -> "com.google.common.base.Supplier")
            .apply(flavour)
    );
  }

  public static TypeElement findF(Flavour flavour, Elements elements) {
    return elements.getTypeElement(
        Flavours.cases()
            .Jdk(() -> Function.class.getName())
            .Fj(() -> "fj.F")
            .Fugue(() -> Function.class.getName())
            .Fugue2(() -> "com.google.common.base.Function")
            .apply(flavour)
    );
  }

  public static OptionType findOptionType(Flavour flavour, Elements elements) {
    return Flavours.cases()
        .Jdk(() -> optionTye(elements.getTypeElement(Optional.class.getName()), "empty", "of"))
        .Fj(() -> optionTye(elements.getTypeElement("fj.data.Option"), "none", "some"))
        .Fugue(() -> optionTye(elements.getTypeElement("io.atlassian.fugue.Option"), "none", "some"))
        .Fugue2(() -> optionTye(elements.getTypeElement("com.atlassian.fugue.Option"), "none", "some"))
        .apply(flavour);
  }

  public static Optional<EitherType> findEitherType(Flavour flavour, Elements elements) {
    return Flavours.cases()
        .Jdk(() -> Optional.<EitherType>empty())
        .Fj(() -> Optional.of(eitherType(elements.getTypeElement("fj.data.Either"), "left", "right")))
        .Fugue(() -> Optional.of(eitherType(elements.getTypeElement("io.atlassian.fugue.Either"), "left", "right")))
        .Fugue2(() -> Optional.of(eitherType(elements.getTypeElement("com.atlassian.fugue.Either"), "left", "right")))
        .apply(flavour);
  }

  public static abstract class OptionType {
    public static OptionType optionTye(TypeElement typeElement, String noneConstructor, String someConstructor) {
      return new OptionType() {
        @Override
        public <R> R optionType(Case<R> optionType) {
          return optionType.optionType(typeElement, noneConstructor, someConstructor);
        }
      };
    }

    public abstract <R> R optionType(Case<R> optionType);

    public TypeElement typeElement() {
      return optionType((typeElement, noneConstructor, someConstructor) -> typeElement);
    }

    public String noneConstructor() {
      return optionType((typeElement, noneConstructor, someConstructor) -> noneConstructor);
    }

    public String someConstructor() {
      return optionType((typeElement, noneConstructor, someConstructor) -> someConstructor);
    }

    public interface Case<R> {
      R optionType(TypeElement typeElement, String noneConstructor, String someConstructor);
    }
  }

  public static abstract class EitherType {
    public static EitherType eitherType(TypeElement typeElement, String leftConstructor, String rightConstructor) {
      return new EitherType() {
        @Override
        public <R> R eitherType(Case<R> eitherType) {
          return eitherType.eitherType(typeElement, leftConstructor, rightConstructor);
        }
      };
    }

    public abstract <R> R eitherType(Case<R> optionType);

    public interface Case<R> {
      R eitherType(TypeElement typeElement, String leftConstructor, String rightConstructor);
    }
  }

}
