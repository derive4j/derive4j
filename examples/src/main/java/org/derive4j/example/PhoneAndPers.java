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
