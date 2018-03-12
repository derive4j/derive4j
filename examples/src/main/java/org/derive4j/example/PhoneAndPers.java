package org.derive4j.example;

import fj.Equal;
import org.derive4j.Derive;
import org.derive4j.Instances;
import org.derive4j.example.PhoneAndPers.Phone.Landline;
import org.derive4j.example.PhoneAndPers.Phone.Mobile;
import org.derive4j.hkt.TypeEq;

public final class PhoneAndPers {
    private PhoneAndPers() {}

    @data @Derive(@Instances(Equal.class))
    interface Phone<T> {
        interface Cases<R, T> {
            R Landline(Integer number, TypeEq<Landline, T> eq);

            R Mobile(Integer number, TypeEq<Mobile, T> eq);
        }
        <R> R match(Cases<R, T> cases);

        enum Landline {}
        enum Mobile {}
    }

    @data @Derive(@Instances(Equal.class))
    interface Pers {
        interface Cases<R> {
            R OnePhonePers(Phone<Landline> landlinePhone);
            R TwoPhonesPers(Phone<Landline> landlinePhone, Phone<Mobile> mobilePhone);
        }
        <R> R match(Cases<R> cases);

        static Equal<Phone<Landline>> landPhoneEqual = PhoneImpl.phoneEqual();

        static Equal<Phone<Mobile>> mobPhoneEqual = PhoneImpl.phoneEqual();
    }
}
