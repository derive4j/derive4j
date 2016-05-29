/*
 * Copyright (c) 2015, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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
package org.derive4j.exemple;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.derive4j.Data;
import org.derive4j.FieldNames;

import static org.derive4j.exemple.Addresses.Address;
import static org.derive4j.exemple.Addresses.modNumber;
import static org.derive4j.exemple.Contacts.getPostalAddress;
import static org.derive4j.exemple.Contacts.modPostalAddress;
import static org.derive4j.exemple.PersonNames.Name;
import static org.derive4j.exemple.Persons.Person;
import static org.derive4j.exemple.Persons.getContact;
import static org.derive4j.exemple.Persons.modContact;

@Data public abstract class Person {

  public abstract <R> R match(@FieldNames({ "name", "contact" }) BiFunction<PersonName, Contact, R> Person);

  public static void main(String[] args) {

    Person joe = Person(Name("Joe"), Contacts.byMail(Address(10, "Main St")));

    // oups! there was a off my one error in the import process. We must increment all street numbers!!

    // Easy with Derive4J
    Function<Person, Person> incrementStreetNumber = modContact(modPostalAddress(modNumber(number -> number + 1)));

    // newP is a copy of p with the street number incremented:
    Person correctedJoe = incrementStreetNumber.apply(joe);

    Optional<Integer> newStreetNumber = getPostalAddress(getContact(correctedJoe)).map(Addresses::getNumber);

    System.out.println(newStreetNumber); // print "Optional[11]" !!
  }

}
