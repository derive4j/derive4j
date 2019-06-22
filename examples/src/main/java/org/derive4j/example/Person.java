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

@Data(@Derive(@Instances({ Show.class, Hash.class, Equal.class, Ord.class })))
public abstract class Person {

  public static void main(String[] args) {

    Person joe = Person(Name0("Joe"), Contacts.byMail(Address(10, "Main St")));

    // oops! there was a off by one error in the import process. We must increment
    // all street numbers!!

    // Easy with Derive4J
    Function<Person, Person> incrementStreetNumber = modContact(modPostalAddress(modNumber(number -> number + 1)));

    // correctedJoe is a copy of p with the street number incremented:
    Person correctedJoe = incrementStreetNumber.apply(joe);

    Optional<Integer> newStreetNumber = getPostalAddress(getContact(correctedJoe)).map(Addresses::getNumber);

    System.out.println(newStreetNumber); // print "Optional[11]" !!
  }

  public abstract <R> R match(@FieldNames({ "name", "contact" }) BiFunction<PersonName, Contact, R> Person);

}
