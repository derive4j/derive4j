/*
 * Copyright (c) 2018, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *  * Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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

    @ExportAsPublic
    static Show<PhoneAndPers.Phone<PhoneAndPers.Phone.Mobile>> mobPhoneShow() {
      return PhoneInstances.mobPhoneShow;
    }
  }

}
