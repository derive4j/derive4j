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

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.derive4j.processor.derivator.Flavours.EitherType.eitherType;
import static org.derive4j.processor.derivator.Flavours.OptionType.optionTye;

public final class Flavours {

  public static TypeElement findF0(Flavour flavour, Elements elements) {
    return elements.getTypeElement(flavour.match(new Flavour.Cases<String>() {

      @Override
      public String Jdk() {
        return Supplier.class.getName();
      }

      @Override
      public String Fj() {
        return "fj.F0";
      }

      @Override
      public String Fugue() {
        return Jdk();
      }

      @Override
      public String Fugue2() {
        return "com.google.common.base.Supplier";
      }
    }));
  }

  public static TypeElement findF1(Flavour flavour, Elements elements) {
    return elements.getTypeElement(flavour.match(new Flavour.Cases<String>() {

      @Override
      public String Jdk() {
        return Function.class.getName();
      }

      @Override
      public String Fj() {
        return "fj.F1";
      }

      @Override
      public String Fugue() {
        return Jdk();
      }

      @Override
      public String Fugue2() {
        return "com.google.common.base.Function";
      }
    }));
  }

  public static OptionType findOptionType(Flavour flavour, Elements elements) {
    return flavour.match(new Flavour.Cases<OptionType>() {

      @Override
      public OptionType Jdk() {
        return optionTye(elements.getTypeElement(Optional.class.getName()), "empty", "of");
      }

      @Override
      public OptionType Fj() {
        return optionTye(elements.getTypeElement("fj.data.Option"), "none", "some");
      }

      @Override
      public OptionType Fugue() {
        return optionTye(elements.getTypeElement("io.atlassian.fugue.Option"), "none", "some");
      }

      @Override
      public OptionType Fugue2() {
        return optionTye(elements.getTypeElement("com.atlassian.fugue.Option"), "none", "some");
      }
    });
  }

  public static Optional<EitherType> findEitherType(Flavour flavour, Elements elements) {
    return flavour.match(new Flavour.Cases<Optional<EitherType>>() {

      @Override
      public Optional<EitherType> Jdk() {
        return Optional.empty();
      }

      @Override
      public Optional<EitherType> Fj() {
        return Optional.of(eitherType(elements.getTypeElement("fj.data.Either"), "left", "right"));
      }

      @Override
      public Optional<EitherType> Fugue() {
        return Optional.of(eitherType(elements.getTypeElement("io.atlassian.fugue.Either"), "left", "right"));
      }

      @Override
      public Optional<EitherType> Fugue2() {
        return Optional.of(eitherType(elements.getTypeElement("com.atlassian.fugue.Either"), "left", "right"));
      }

    });
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

    public interface Case<R> {
      R optionType(TypeElement typeElement, String noneConstructor, String someConstructor);
    }

    public TypeElement typeElement() {
      return optionType((typeElement, noneConstructor, someConstructor) -> typeElement);
    }

    public String noneConstructor() {
      return optionType((typeElement, noneConstructor, someConstructor) -> noneConstructor);
    }

    public String someConstructor() {
      return optionType((typeElement, noneConstructor, someConstructor) -> someConstructor);
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
