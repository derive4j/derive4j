/*
 * Copyright (c) 2017, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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
import fj.Hash;
import fj.Ord;
import fj.Show;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.FieldNames;
import org.derive4j.Instances;

import static org.derive4j.example.Addresses.Address;
import static org.derive4j.example.Addresses.modNumber;
import static org.derive4j.example.Contacts.getPostalAddress;
import static org.derive4j.example.Contacts.modPostalAddress;
import static org.derive4j.example.PersonNames.Name0;
import static org.derive4j.example.Persons.Person;
import static org.derive4j.example.Persons.getContact;
import static org.derive4j.example.Persons.modContact;

@Data(@Derive(@Instances({ Show.class, Hash.class, Equal.class, Ord.class})))
public abstract class Person {

  public static void main(String[] args) {

    Person joe = Person(Name0("Joe"), Contacts.byMail(Address(10, "Main St")));

    // oops! there was a off by one error in the import process. We must increment all street numbers!!

    // Easy with Derive4J
    Function<Person, Person> incrementStreetNumber = modContact(modPostalAddress(modNumber(number -> number + 1)));

    // correctedJoe is a copy of p with the street number incremented:
    Person correctedJoe = incrementStreetNumber.apply(joe);

    Optional<Integer> newStreetNumber = getPostalAddress(getContact(correctedJoe)).map(Addresses::getNumber);

    System.out.println(newStreetNumber); // print "Optional[11]" !!
  }

  public abstract <R> R match(@FieldNames({ "name", "contact" }) BiFunction<PersonName, Contact, R> Person);

}
