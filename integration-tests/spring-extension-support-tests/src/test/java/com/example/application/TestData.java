/*
 * Copyright 2023 Marco Collovati, Dario Götze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.application;

import java.util.List;

public class TestData {
    public static final List<String> NAMES_ASC = List.of(
            "Aaliyah",
            "Addison",
            "Adrian",
            "Alexa",
            "Alexandra",
            "Alexis",
            "Alyssa",
            "Andrew",
            "Aria",
            "Aubrey",
            "Autumn",
            "Ava",
            "Avery",
            "Bentley",
            "Blake",
            "Brandon",
            "Brayden",
            "Brooklyn",
            "Caleb",
            "Cameron",
            "Carter",
            "Charlie",
            "Chloe",
            "Christopher",
            "Cole",
            "Colton",
            "Connor",
            "Daniel",
            "Destiny",
            "Dylan",
            "Elena",
            "Elijah",
            "Ella",
            "Emily",
            "Emma",
            "Ethan",
            "Evan",
            "Gabriel",
            "Gabriella",
            "Gavin");
    public static final List<String> NAMES_DESC = List.of(
            "Zoey",
            "Zoe",
            "William",
            "Victoria",
            "Tyler",
            "Tristan",
            "Sophie",
            "Sophia",
            "Sofia",
            "Skylar",
            "Seth",
            "Scarlett",
            "Samuel",
            "Sadie",
            "Ryan",
            "Riley",
            "Peter",
            "Penelope",
            "Parker",
            "Paige",
            "Owen",
            "Olivia",
            "Nicholas",
            "Nathan",
            "Natalie",
            "Mila",
            "Micah",
            "Mia",
            "Maya",
            "Max",
            "Matthew",
            "Mason",
            "Makayla",
            "Madison",
            "Luke",
            "Logan",
            "Lily",
            "Lillian",
            "Liam",
            "Leah");
    public static final List<String> NAMES_UNSORTED = List.of(
            "Jason",
            "Homer",
            "Peter",
            "Emily",
            "Daniel",
            "Olivia",
            "William",
            "Sophia",
            "Matthew",
            "Emma",
            "Christopher",
            "Ava",
            "Nicholas",
            "Madison",
            "Ethan",
            "Chloe",
            "Andrew",
            "Grace",
            "Ryan",
            "Isabella",
            "Jacob",
            "Mia",
            "Logan",
            "Avery",
            "Liam",
            "Harper",
            "Caleb",
            "Ella",
            "Samuel",
            "Lily",
            "Nathan",
            "Addison",
            "Jackson",
            "Brooklyn",
            "Brandon",
            "Hannah",
            "Elijah",
            "Aria",
            "Justin",
            "Sofia");
    static final int RENDERED_ITEMS = NAMES_UNSORTED.size();
    static final UserData USER_74 = new UserData(74, "Lillian", "Sims");
    static final UserData USER_48 = new UserData(48, "Maya", "Rogers");
    static final UserData USER_54 = new UserData(54, "Makayla", "Webb");
    static final UserData USER_51 = new UserData(51, "Mason", "Cooper");

    record UserData(Integer id, String name, String surname) {}
}
