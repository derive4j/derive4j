/*
 * Copyright (c) 2019, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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
package org.derive4j.example;

import fj.Equal;
import fj.Show;
import org.derive4j.Derive;
import org.derive4j.ExportAsPublic;
import org.derive4j.Instances;
import org.derive4j.example.PhoneAndPers.Phone.Landline;
import org.derive4j.example.PhoneAndPers.Phone.Mobile;
import org.derive4j.hkt.TypeEq;

public final class PhoneAndPers {
  private PhoneAndPers() {
  }

  @data
  @Derive(extend = PhoneInstances2.class)
  interface Phone<T> {
    interface Cases<R, T> {
      R Landline(Integer number, TypeEq<Landline, T> eq);

      R Mobile(Integer number, TypeEq<Mobile, T> eq);
    }

    <R> R match(Cases<R, T> cases);

    enum Landline {
    }

    enum Mobile {
    }
  }

  @data
  @Derive(@Instances({ Equal.class, Show.class }))
  interface Pers {
    interface Cases<R> {
      R OnePhonePers(Phone<Landline> landlinePhone);

      R TwoPhonesPers(Phone<Landline> landlinePhone, Phone<Mobile> mobilePhone);
    }

    <R> R match(Cases<R> cases);
  }

  static abstract class PhoneInstances {
    // static final Equal<PhoneAndPers.Phone<PhoneAndPers.Phone.Landline>>
    // landPhoneEqual = null;
    static final Show<PhoneAndPers.Phone<PhoneAndPers.Phone.Landline>> landPhoneShow = null;

    // static final Equal<PhoneAndPers.Phone<PhoneAndPers.Phone.Mobile>>
    // mobPhoneEqual = null;
    static final Show<PhoneAndPers.Phone<PhoneAndPers.Phone.Mobile>> mobPhoneShow = null;

  }

  static abstract class PhoneInstances2 extends PhoneInstances {
    @ExportAsPublic
    static <T> Equal<PhoneAndPers.Phone<T>> allPhoneEqual() {
      return null;
    }

  }

}
