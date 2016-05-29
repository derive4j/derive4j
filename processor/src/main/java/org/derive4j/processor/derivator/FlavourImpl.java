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

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import org.derive4j.Data;
import org.derive4j.Flavour;
import org.derive4j.Flavours;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.model.DeriveContext;

import static org.derive4j.processor.derivator.EitherTypes.eitherType;
import static org.derive4j.processor.derivator.OptionTypes.optionType;

public final class FlavourImpl {

  public static TypeElement findF0(Flavour flavour, Elements elements) {

    return elements.getTypeElement(Flavours.cases()
        .Jdk(Supplier.class::getName)
        .Fj(() -> "fj.F0")
        .Fugue(Supplier.class::getName)
        .Fugue2(() -> "com.google.common.base.Supplier")
        .Javaslang(Supplier.class::getName)
        .HighJ(Supplier.class.getName())
        .apply(flavour));
  }

  public static TypeElement findF(Flavour flavour, Elements elements) {

    return elements.getTypeElement(Flavours.cases()
        .Jdk(Function.class.getName())
        .Fj("fj.F")
        .Fugue(Function.class.getName())
        .Fugue2("com.google.common.base.Function")
        .Javaslang("javaslang.Function1")
        .HighJ("org.highj.function.F1")
        .apply(flavour));
  }

  public static OptionType findOptionType(Flavour flavour, Elements elements) {

    return Flavours.cases()
        .Jdk(() -> optionType(elements.getTypeElement(Optional.class.getName()), "empty", "of"))
        .Fj(() -> optionType(elements.getTypeElement("fj.data.Option"), "none", "some"))
        .Fugue(() -> optionType(elements.getTypeElement("io.atlassian.fugue.Option"), "none", "some"))
        .Fugue2(() -> optionType(elements.getTypeElement("com.atlassian.fugue.Option"), "none", "some"))
        .Javaslang(() -> optionType(elements.getTypeElement("javaslang.control.Option"), "none", "some"))
        .HighJ(() -> optionType(elements.getTypeElement("org.highj.data.Maybe"), "Nothing", "Just"))
        .apply(flavour);
  }

  public static Optional<EitherType> findEitherType(Flavour flavour, Elements elements) {

    return Flavours.cases()
        .Jdk(Optional::<EitherType>empty)
        .Fj(() -> Optional.of(eitherType(elements.getTypeElement("fj.data.Either"), "left", "right")))
        .Fugue(() -> Optional.of(eitherType(elements.getTypeElement("io.atlassian.fugue.Either"), "left", "right")))
        .Fugue2(() -> Optional.of(eitherType(elements.getTypeElement("com.atlassian.fugue.Either"), "left", "right")))
        .Javaslang(() -> Optional.of(eitherType(elements.getTypeElement("javaslang.control.Either"), "left", "right")))
        .HighJ(() -> Optional.of(eitherType(elements.getTypeElement("org.highj.data.Either"), "Left", "Right")))
        .apply(flavour);
  }

  public static String supplierApplyMethod(DeriveUtils deriveUtils, DeriveContext deriveContext) {

    return deriveUtils.allAbstractMethods(findF0(deriveContext.flavour(), deriveUtils.elements())).get(0).getSimpleName().toString();
  }

  public static String functionApplyMethod(DeriveUtils deriveUtils, DeriveContext deriveContext) {

    return deriveUtils.allAbstractMethods(findF(deriveContext.flavour(), deriveUtils.elements())).get(0).getSimpleName().toString();
  }

  @Data public abstract static class OptionType {

    public abstract <R> R optionType(Case<R> optionType);

    public TypeElement typeElement() {

      return OptionTypes.getTypeElement(this);
    }

    public String noneConstructor() {

      return OptionTypes.getNoneConstructor(this);
    }

    public String someConstructor() {

      return OptionTypes.getSomeConstructor(this);
    }

    public interface Case<R> {
      R optionType(TypeElement typeElement, String noneConstructor, String someConstructor);
    }
  }

  @Data public abstract static class EitherType {

    public abstract <R> R eitherType(Case<R> optionType);

    public interface Case<R> {
      R eitherType(TypeElement typeElement, String leftConstructor, String rightConstructor);
    }
  }

}
