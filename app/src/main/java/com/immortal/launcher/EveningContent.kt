/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

internal data class EveningFact(
    val title: String,
    val body: String,
    val sourceName: String,
    val sourceUrl: String,
    val detail: String,
    val tryIt: String,
)

internal data class EveningWord(
    val english: String,
    val partOfSpeech: String,
    val definition: String,
    val example: String,
    val spanish: String,
    val spanishExample: String,
    val spanishTranslation: String,
    val spanishDefinition: String,
    val practice: String,
)

internal data class EveningQuestion(
    val prompt: String,
    val answer: String,
    val sourceName: String = "",
    val sourceUrl: String = "",
    val detail: String = "",
)

/** Original, offline reading and activity cards. Science sources checked October 2026. */
internal object EveningContent {
  private const val VENUS = "https://science.nasa.gov/venus/venus-facts/"
  private const val SATURN = "https://science.nasa.gov/saturn/facts/"
  private const val MOON = "https://science.nasa.gov/moon/facts/"
  private const val MARS = "https://science.nasa.gov/mars/facts/"
  private const val SUN = "https://science.nasa.gov/sun/facts/"
  private const val JUPITER = "https://science.nasa.gov/jupiter/jupiter-facts/"
  private const val OCEAN = "https://oceanservice.noaa.gov/facts/oceanwater.html"
  private const val CORAL = "https://oceanexplorer.noaa.gov/ocean-fact/coral-animal/"
  private const val TIDES = "https://oceanservice.noaa.gov/facts/tides.html"
  private const val BATS = "https://www.nps.gov/subjects/bats/echolocation.htm"
  private const val TREES = "https://www.nps.gov/seki/learn/nature/largest-trees-in-world.htm"
  private const val CONDENSATION = "https://www.usgs.gov/water-science-school/science/condensation-and-water-cycle"
  private const val EVAPORATION = "https://www.usgs.gov/water-science-school/science/evaporation-and-water-cycle"
  private const val EARTHQUAKES = "https://www.usgs.gov/faqs/what-earthquake-and-what-causes-them-happen"

  val facts: List<EveningFact> = listOf(
      EveningFact("A very slow spin", "Venus takes about 243 Earth days to spin once, but only about 225 Earth days to orbit the Sun. One full spin takes longer than its year!", "NASA", VENUS,
          detail = "A spin and an orbit are two different motions. Venus turns around its own axis very slowly while also traveling around the Sun. It completes an orbit before finishing one full turn. That makes its rotation period longer than its year. A sunrise-to-sunrise day is a different measurement, because both motions affect when the Sun appears again.",
          tryIt = "Imagine two clocks for a planet: one counts spins and one counts trips around the Sun. What would each clock measure?"),
      EveningFact("Rings made of pieces", "Saturn's rings are not solid hoops. They contain billions of pieces of ice and rock, from tiny grains to enormous chunks.", "NASA", SATURN,
          detail = "From far away, thousands of separate pieces can look like one smooth shape. Saturn's rings are a spectacular example: their icy and rocky pieces travel around the planet. They range from dust-sized grains to much larger chunks. The rings also have gaps, so a picture that looks like a simple hoop actually hides a complicated neighborhood.",
          tryIt = "Picture a ring built from countless snowballs instead of one solid sheet. What detail would a close-up picture need to show?"),
      EveningFact("The far side gets sunshine", "The Moon's far side is not always dark. As the Moon travels around Earth, both its near side and far side have times of sunlight and darkness.", "NASA", MOON,
          detail = "Far side tells us which direction a part of the Moon faces, not whether it is lit. The Moon turns as it orbits Earth, keeping roughly the same face toward us. Sunlight still reaches different parts along that journey. At new moon, much of the side we cannot see is sunny while the side facing Earth is having its night.",
          tryIt = "Imagine sending a postcard from the Moon's far side. Explain why you would need to check the local time before calling it dark."),
      EveningFact("A rusty red planet", "Mars looks reddish because iron minerals in its soil react with oxygen and rust. That rusty material helps give the Red Planet its nickname.", "NASA", MARS,
          detail = "A planet's color can offer clues about its materials. On Mars, iron-bearing minerals have changed through a reaction called oxidation, producing the reddish color we associate with rust. This does not mean the whole planet is one giant rusty metal ball. The clue comes from minerals in the dirt and dust that cover much of its surface.",
          tryIt = "Invent a one-sentence museum label for a red sample from Mars. Include what its color suggests and one thing the color cannot prove."),
      EveningFact("Our closest star", "The Sun is a star. Deep in its core, hydrogen joins to make helium, releasing energy that eventually reaches us as heat and light.", "NASA", SUN,
          detail = "The Sun's energy begins deep inside it, where conditions allow hydrogen nuclei to join into helium. This process is called nuclear fusion. Energy then travels outward through the Sun before escaping into space. The bright layer we call its surface is not solid ground. A star can shine brilliantly without being a rocky object or a burning campfire.",
          tryIt = "Imagine explaining the Sun to a visitor who thinks every bright thing is a lamp. Which two clues would show that it is a star?"),
      EveningFact("A planet full of ocean water", "About 97 percent of Earth's water is in the ocean. Lakes, rivers, ice, groundwater and water vapor share the much smaller remainder.", "NOAA", OCEAN,
          detail = "Two big ocean numbers answer different questions. Over 70 percent tells us how much of Earth's surface the ocean covers. About 97 percent tells us what share of Earth's water is in it. Surface coverage is like the space a rug covers on a floor; water amount is about volume. Confusing those measurements would give a very different picture of our planet.",
          tryIt = "Imagine Earth's water as 100 tokens. Put about 97 in the ocean group. How many remain for all the other places water can be?"),
      EveningFact("Coral is alive", "Corals are animals, not rocks or plants. Many coral colonies contain hundreds or thousands of tiny animals called polyps.", "NOAA", CORAL,
          detail = "A coral colony can look like a branching plant or a bumpy rock, but its tiny polyps are animals. Each has a soft body with a mouth and tentacles for catching food. Many make hard skeletons that protect them. A colony brings many small animals together, so the large shape you notice first can hide a whole community of living bodies.",
          tryIt = "Imagine zooming into a coral colony in a picture book. What labels would help readers spot the animals inside the larger shape?"),
      EveningFact("The sea rises and falls", "The Moon's and Sun's gravity help create tides: the regular rise and fall of sea level along a coast.", "NOAA", TIDES,
          detail = "Tides are very long waves moving through the ocean under the influence of the Moon's and Sun's gravity. At a coast, we notice them as changes in sea level. High tide is the higher level and low tide the lower one. The vertical difference is the tidal range. It is a change in water height, rather than the distance a wave runs across a beach.",
          tryIt = "In an imaginary harbor, high water is 5 meters above a marker and low water is 2 meters above it. What is the tidal range?"),
      EveningFact("Listening for dinner", "Many bats find insects using echolocation. They make calls, then listen for sound bouncing back from objects around them.", "NPS", BATS,
          detail = "A bat's call can bounce off an insect and return as an echo. That returning sound gives the bat information about its surroundings while it flies. Many calls are too high in pitch for people to hear. As a bat closes in on prey, it can call more rapidly. What seems like silent flight to us can be a busy exchange of sounds for the bat.",
          tryIt = "Design an imaginary bat comic with three panels: call, bounce, return. Where would you draw the insect and the returning sound?"),
      EveningFact("Where glass droplets come from", "Droplets outside a cold glass can come from water vapor in the surrounding air. The vapor cools and becomes liquid; this is condensation.", "USGS", CONDENSATION,
          detail = "Air can contain water even when it looks clear, because water vapor is an invisible gas. Near the cold outside of a glass, some vapor cools and changes into liquid droplets. The glass has not necessarily leaked: the water can arrive from the air around it. This change from gas to liquid also helps explain familiar foggy windows and cloud droplets.",
          tryIt = "Imagine the same cold glass filled with a blue drink. If clear droplets appear outside, why might leaking be the wrong explanation?"),
      EveningFact("A giant measured by volume", "The General Sherman Tree, a giant sequoia in California, is the world's largest tree by trunk volume. Largest by volume does not mean tallest!", "NPS", TREES,
          detail = "Biggest needs a measuring rule. A tree could be very tall but have a narrow trunk, while a shorter tree could have a much thicker trunk. Trunk volume measures how much space that trunk fills in three dimensions. The General Sherman Tree leads by that measure. The park's ranking leaves out branches, leaves and roots, so even its measurement has a clear boundary.",
          tryIt = "Imagine judging a tree contest. Choose height, trunk width or trunk volume, then explain why your winner might differ from another judge's."),
      EveningFact("A disappearing puddle", "When a puddle evaporates, its liquid water becomes water vapor in the air. The water changes form instead of simply vanishing.", "USGS", EVAPORATION,
          detail = "Liquid water can become a gas without boiling. During evaporation, water leaves the liquid and enters the surrounding air as invisible vapor. That is why a puddle can shrink even when nobody scoops water out of it. Condensation is the reverse change, when vapor becomes liquid again. Together, these changes help move water between Earth's surface and the air.",
          tryIt = "Tell a short journey in which a puddle's water becomes vapor and later a droplet. Name the two changes along the way."),
      EveningFact("Earth's moving puzzle pieces", "Earth's tectonic plates move slowly. When rocks suddenly slip along a fault, released energy can shake the ground in an earthquake.", "USGS", EARTHQUAKES,
          detail = "Slow motion can lead to a sudden change. As tectonic plates move, friction can keep rocks from sliding freely along a fault. Stress builds until a section slips, releasing energy as waves that travel through Earth. Those waves cause shaking away from the fault too. The earthquake is the sudden release; the plates' long, slow movement sets the scene.",
          tryIt = "Imagine explaining an earthquake with two comic panels labeled before and during. Which panel would show built-up stress, and which would show waves?"),
      EveningFact("A giant with a quick spin", "Jupiter is the largest planet in our solar system, yet it spins once in only about 10 hours. Its day is much shorter than Earth's.", "NASA", JUPITER,
          detail = "Size does not tell you how long a planet takes to turn. Huge Jupiter spins in about 10 Earth hours, so it has a much shorter day than Earth. Its year measures a different journey: one orbit around the Sun takes about 12 Earth years. A planet can therefore have very quick days and very long years at the same time. The two clocks count different motions.",
          tryIt = "An imaginary probe waits 20 Earth hours near Jupiter. About how many Jupiter spins pass? Why does that tell us nothing about completed years?"),
  )

  val words: List<EveningWord> = listOf(
      EveningWord("curiosity", "noun", "A wish to learn or find out more.", "My curiosity led me to ask how rain forms.", "la curiosidad", "La curiosidad me ayuda a aprender.", "Curiosity helps me learn.",
          spanishDefinition = "El deseo de aprender, descubrir cosas o entender mejor una idea.",
          practice = "Choose an everyday mystery, such as why a ball bounces. Make a sentence using curiosity, then add one question you would investigate."),
      EveningWord("perseverance", "noun", "Continuing to try when something is difficult.", "With perseverance, I learned the tricky rhythm.", "la perseverancia", "La perseverancia me ayuda a seguir intentando.", "Perseverance helps me keep trying.",
          spanishDefinition = "La decisión de seguir intentando algo aunque sea difícil.",
          practice = "Imagine a puzzle that takes several tries. Describe one new strategy using perseverance; asking for help can be part of continuing."),
      EveningWord("respect", "noun", "Care for other people's feelings, choices and belongings.", "I show respect by listening without interrupting.", "el respeto", "El respeto empieza por escuchar.", "Respect begins with listening.",
          spanishDefinition = "El cuidado que mostramos por los sentimientos, las decisiones y las cosas de otras personas.",
          practice = "Imagine two friends want different games. Use respect in a sentence that explains how both friends can have a turn to speak."),
      EveningWord("observe", "verb", "To notice something carefully, using your senses.", "We observe the leaf's shape and color.", "observar", "Me gusta observar las nubes.", "I like to observe the clouds.",
          spanishDefinition = "Prestar atención a algo para notar sus detalles con los sentidos.",
          practice = "Pick an object you can already see. Use observe to describe two details, then separate what you notice from what you are guessing."),
      EveningWord("patience", "noun", "The ability to wait or keep trying calmly.", "Growing a garden takes patience.", "la paciencia", "La paciencia me ayuda a esperar mi turno.", "Patience helps me wait my turn.",
          spanishDefinition = "La capacidad de esperar o seguir intentando algo con calma.",
          practice = "Imagine teaching a friend a new game. Use patience in a sentence about what you could do while your friend learns the rules."),
      EveningWord("courage", "noun", "The ability to do something worthwhile even when you feel afraid.", "It took courage to ask for help.", "el valor", "Tuve el valor de pedir ayuda.", "I had the courage to ask for help.",
          spanishDefinition = "La capacidad de hacer algo importante aunque sintamos miedo.",
          practice = "Invent a character who needs help with a hard task. Write or say a sentence using courage about how the character could ask for support."),
      EveningWord("empathy", "noun", "Trying to understand how another person feels.", "Empathy helped me understand my friend's disappointment.", "la empatía", "La empatía nos ayuda a entender a los demás.", "Empathy helps us understand others.",
          spanishDefinition = "El esfuerzo por comprender cómo se siente otra persona.",
          practice = "Imagine a friend missed the winning goal. Use empathy in a sentence about listening; add a question instead of assuming how the friend feels."),
      EveningWord("effort", "noun", "The energy and attention you put into doing something.", "My effort helped the whole team improve.", "el esfuerzo", "Mi esfuerzo cuenta, aunque todavía esté aprendiendo.", "My effort matters, even while I am still learning.",
          spanishDefinition = "La energía y la atención que dedicamos a hacer algo.",
          practice = "Picture a team practicing a tricky song. Use effort in a sentence about something the team can control, such as practicing one small section."),
      EveningWord("gratitude", "noun", "The feeling of being thankful.", "I felt gratitude when my neighbor helped us.", "la gratitud", "Siento gratitud por tu ayuda.", "I feel gratitude for your help.",
          spanishDefinition = "El sentimiento de agradecimiento por algo bueno o por la ayuda recibida.",
          practice = "Think of a made-up character who receives a helpful favor. Use gratitude in a sentence that names exactly what the character appreciates."),
      EveningWord("teamwork", "noun", "People working together toward a shared goal.", "Teamwork helped us finish the mural.", "el trabajo en equipo", "El trabajo en equipo nos ayuda a resolver problemas.", "Teamwork helps us solve problems.",
          spanishDefinition = "La colaboración de varias personas para lograr una meta común.",
          practice = "Invent a team building a cardboard city. Use teamwork in a sentence, and give two teammates different jobs that help the same goal."),
      EveningWord("improve", "verb", "To become better at something, often through practice.", "I improve my drawing by noticing new details.", "mejorar", "Quiero mejorar con la práctica.", "I want to improve with practice.",
          spanishDefinition = "Hacer algo cada vez mejor o lograr un resultado mejor con la práctica.",
          practice = "Choose an imaginary skill, like dragon drawing. Use improve in a sentence with one small practice step and a way to notice progress."),
      EveningWord("explore", "verb", "To look closely at a place or idea to learn about it.", "Let's explore three ways to solve the puzzle.", "explorar", "Podemos explorar una idea nueva.", "We can explore a new idea.",
          spanishDefinition = "Examinar un lugar o una idea con atención para aprender más.",
          practice = "Imagine a puzzle with two possible solutions. Use explore in a sentence about trying both ideas before deciding which one works."),
      EveningWord("responsibility", "noun", "A task or duty you are trusted to take care of.", "Watering our plant is my responsibility.", "la responsabilidad", "Cuidar la planta es mi responsabilidad.", "Taking care of the plant is my responsibility.",
          spanishDefinition = "Una tarea o un deber que nos comprometemos a cuidar o cumplir.",
          practice = "Imagine caring for a classroom plant. Use responsibility in a sentence about the job, then explain what to do if you need help keeping it."),
      EveningWord("cooperate", "verb", "To work with other people to get something done.", "We cooperate by sharing ideas and taking turns.", "cooperar", "Podemos cooperar para ordenar la mesa.", "We can cooperate to tidy the table.",
          spanishDefinition = "Trabajar con otras personas y ayudarse para conseguir algo.",
          practice = "Imagine three friends tidying a game shelf. Use cooperate in a sentence about sharing the jobs, then say how they could agree on a plan."),
  )

  val puzzles: List<EveningQuestion> = listOf(
      EveningQuestion("What is three quarters of 28?", "21. One quarter is 28 ÷ 4 = 7; three quarters is 3 × 7.",
          detail = "Start by splitting 28 into four equal groups. Each group has 7, so taking three groups gives 7 + 7 + 7. Check by adding the missing quarter back: 21 + 7 returns to 28. Equal groups are the key to finding a fraction of an amount."),
      EveningQuestion("A game starts at 6:35 and lasts 50 minutes. When does it finish?", "7:25. Add 25 minutes to reach 7:00, then another 25 minutes.",
          detail = "A clock changes hours after 60 minutes, so adding 50 straight to 35 needs a regrouping step. Split the duration at the next hour: 25 minutes gets to 7:00 and 25 more gets to 7:25. Check that both parts total 50 minutes."),
      EveningQuestion("I am a two-digit number. My digits add to 9. My tens digit is twice my ones digit. What am I?", "63. The ones digit is 3, the tens digit is 6, and 6 + 3 = 9.",
          detail = "Think of the ones digit as one equal part and the tens digit as two of those parts. Their sum, 9, is three parts altogether, making each part 3. Put 6 in the tens place and 3 in the ones place, then check both clues separately."),
      EveningQuestion("Which is greater: 2/3 or 3/5?", "2/3. With a common denominator of 15, they are 10/15 and 9/15.",
          detail = "Fractions are easiest to compare when the pieces are the same size. Split each third into five pieces and each fifth into three pieces. Both now use fifteenths. Ten of those pieces is more than nine, so 2/3 wins by just 1/15."),
      EveningQuestion("Three notebooks cost $4 each. You pay with $20. How much change do you get?", "$8. The notebooks cost 3 × $4 = $12, and $20 − $12 = $8.",
          detail = "Find the total cost before thinking about change. Three groups of 4 make 12. Change is the amount needed to reach 20 from 12. Count up 8, then check by adding cost and change: 12 + 8 gives the original payment of 20."),
      EveningQuestion("A rectangle is 8 cm long and 5 cm wide. What is its perimeter?", "26 cm. Add all four sides: 8 + 5 + 8 + 5.",
          detail = "Perimeter means the distance around the edge. A rectangle has two long sides and two short sides, so you need both 8s and both 5s. You can also add one length and one width, then double: 2 × (8 + 5). The units are centimeters, not square centimeters."),
      EveningQuestion("A pattern starts 3, 6, 12, 24. If it keeps doubling, what comes next?", "48. Each number is twice the one before it.",
          detail = "Look at the operation between neighboring numbers: 3 becomes 6, then 12, then 24 by multiplying by 2. Apply that same rule once more to get 48. A short sequence can fit other rules too; here the question specifically tells us to keep doubling."),
      EveningQuestion("Four friends share 3 identical pizzas equally. How much pizza does each get?", "3/4 of a pizza. Cut each pizza into 4 equal pieces; each friend gets 3 pieces.",
          detail = "Sharing equally means all four friends get the same amount. Divide each of the three pizzas into quarters and give one quarter from each pizza to each friend. Everyone receives three quarters. Check the total: four shares of 3/4 make three whole pizzas."),
      EveningQuestion("Maya is ahead of Leo in a line. Leo is ahead of Sam. Who is first among these three?", "Maya. Their order is Maya, Leo, then Sam.",
          detail = "Use the clues in order instead of guessing from the names. Maya goes before Leo, and Leo goes before Sam, so Maya must also go before Sam. This establishes the order of these three people even if other people are elsewhere in the line."),
      EveningQuestion("Without multiplying each step, what is 25 × 16?", "400. Split 16 into 4 × 4: 25 × 4 = 100, then 100 × 4 = 400.",
          detail = "Multiplication lets you regroup factors without changing the result. Since 16 is 4 × 4, pair 25 with one 4 first to make a friendly 100. Multiply that by the remaining 4. This mental strategy turns a less familiar fact into two easier ones."),
      EveningQuestion("A bag has 3 blue and 2 red counters. You pick one without looking. What fraction of the counters are blue?", "3/5. There are 5 counters in total, and 3 are blue.",
          detail = "The denominator counts all possible counters: 3 blue plus 2 red makes 5. The numerator counts the blue ones, giving 3/5. If each counter is equally likely to be picked, this fraction is also the chance of blue. It does not guarantee blue on any single pick."),
      EveningQuestion("The average of three numbers is 8. Two numbers are 6 and 9. What is the third?", "9. The total must be 3 × 8 = 24. Subtract 6 + 9 = 15 to get 9.",
          detail = "An average of 8 across three numbers means their total is the same as three equal groups of 8. That total is 24. The known numbers already contribute 15, leaving 9. Check by adding 6 + 9 + 9 and dividing the result by 3."),
      EveningQuestion("Using only whole squares, how many 2 cm by 2 cm squares cover a 6 cm by 4 cm rectangle?", "6 squares. Three fit along the 6 cm side and two along the 4 cm side: 3 × 2.",
          detail = "Count how many squares fit in each direction. A 2 cm side fits three times along 6 cm and twice along 4 cm, making two rows of three. As a second check, divide the rectangle's area, 24 square centimeters, by each square's area of 4."),
      EveningQuestion("You read 18 pages on Monday and 7 more pages than that on Tuesday. How many pages altogether?", "43 pages. Tuesday's total is 18 + 7 = 25; then 18 + 25 = 43.",
          detail = "Read the phrase seven more carefully: Tuesday is seven more than Monday, rather than seven pages total. First find Tuesday's 25 pages. Then combine the two days. You can check another way: two lots of 18 plus the extra 7 also gives 43."),
  )

  val science: List<EveningQuestion> = listOf(
      EveningQuestion("Venus is farther from the Sun than Mercury. Why is Venus still the hotter planet?", "Venus has a thick atmosphere that traps heat in a powerful greenhouse effect.", "NASA", VENUS,
          detail = "Distance from the Sun is only part of the story. A planet's atmosphere also affects how easily energy escapes. Venus has a very thick atmosphere that keeps heat from escaping efficiently, so its surface gets hotter than Mercury's despite being farther away."),
      EveningQuestion("Which planet has an average density lower than water: Earth or Saturn?", "Saturn. Its average density is lower than water's, even though it is enormous.", "NASA", SATURN,
          detail = "Density compares mass with volume: how much matter is packed into a given space. Something can be enormous yet have a low average density. Saturn's average includes all of its material, including dense inner regions, rather than just its outer gases."),
      EveningQuestion("Why does roughly the same side of the Moon always face Earth?", "The Moon takes the same amount of time to spin once as it takes to orbit Earth once.", "NASA", MOON,
          detail = "Imagine a Moon that orbits without turning at all: different sides would face Earth along the way. To keep one face aimed at Earth, the Moon must turn once per orbit. This matching of two motions is called synchronous rotation."),
      EveningQuestion("What makes the soil on Mars look reddish?", "Iron minerals oxidize: they react with oxygen and form rust, giving the soil its reddish color.", "NASA", MARS,
          detail = "The reddish clue is about chemistry, rather than Mars being hot like a glowing coal. Iron-containing minerals have reacted with oxygen to form rusty materials. These minerals color the dirt and dust, helping us connect an appearance with a material change."),
      EveningQuestion("Does the Sun have a solid surface like Earth?", "No. The visible layer we call its surface is the photosphere, part of the Sun's atmosphere.", "NASA", SUN,
          detail = "A visible edge does not have to be solid. The photosphere is the layer from which much of the Sun's visible light escapes, making it look like a surface. It is part of the solar atmosphere, so there is no rocky floor there to stand on."),
      EveningQuestion("Does the ocean cover more or less than half of Earth's surface?", "More than half: it covers over 70 percent of Earth's surface.", "NOAA", OCEAN,
          detail = "Imagine Earth's surface split into 100 equal patches. More than 70 would be ocean, leaving fewer than 30 for land. That answers a surface-area question. It is different from asking what percentage of all Earth's water is stored in the ocean."),
      EveningQuestion("What is a coral polyp?", "A small animal. Many coral colonies are made of hundreds or thousands of polyps living together.", "NOAA", CORAL,
          detail = "A polyp has an animal's body, including a mouth and tentacles for catching food. Many polyps live together as a colony and build protective skeletons. The colony can look like a rock, but that outer shape does not tell you whether its builders are alive."),
      EveningQuestion("What do scientists call the difference in height between high tide and low tide?", "The tidal range. It measures how far sea level changes between those two tides.", "NOAA", TIDES,
          detail = "Range is a difference between levels, so subtract the low-tide height from the high-tide height using the same reference point. If the levels are 4 meters and 1 meter, the range is 3 meters. It measures vertical change, rather than the time between tides."),
      EveningQuestion("Why can't we hear many bats' echolocation calls with our own ears?", "Their calls are ultrasound: sound at frequencies above the range of human hearing.", "NPS", BATS,
          detail = "Pitch depends on sound frequency: how rapidly the sound vibrates. Many bat calls vibrate faster than human ears can detect. Being unable to hear a call does not mean no sound exists; special recording equipment can turn those calls into forms we can study."),
      EveningQuestion("What change happens when water vapor forms liquid droplets in a cloud?", "Condensation: water changes from a gas into liquid droplets.", "USGS", CONDENSATION,
          detail = "The water is still water, but its state changes. Invisible vapor can cool and form tiny liquid droplets that make a cloud visible. The droplets are different from the gas they formed from. Condensation is the reverse of evaporation, which takes liquid into the air as vapor."),
      EveningQuestion("Can the largest tree by trunk volume be different from the tallest tree?", "Yes. Volume measures how much space the trunk takes up; height measures how tall it is.", "NPS", TREES,
          detail = "A tall, narrow trunk might contain less wood than a shorter, wider one. Height describes one dimension; volume describes space in three dimensions. Choosing a winner therefore requires choosing the measurement first, and the park's volume ranking counts trunks only."),
      EveningQuestion("Must a puddle boil for its water to evaporate?", "No. Evaporation can happen below boiling temperature; liquid water slowly becomes water vapor.", "USGS", EVAPORATION,
          detail = "Boiling is not required for water to become vapor. Evaporation also happens at ordinary temperatures, so a puddle can shrink while staying cool enough to touch. The water has moved into the air as a gas; it has not stopped existing."),
      EveningQuestion("How does energy released by a sudden slip on a fault reach places farther away?", "It travels through Earth as seismic waves. These waves cause the shaking we feel.", "USGS", EARTHQUAKES,
          detail = "The slipping rocks release stored energy, and waves carry that energy outward through Earth. That is why shaking can reach a place where no fault is slipping directly beneath it. The whole plate does not need to travel to that place for the wave to arrive."),
      EveningQuestion("Jupiter spins once in about 10 hours. Is its day longer or shorter than Earth's 24-hour day?", "Shorter. One full spin marks a day, so Jupiter completes a day much faster than Earth.", "NASA", JUPITER,
          detail = "Compare both rotations using the same unit, Earth hours: about 10 against 24. Jupiter fits more than two spins into the time Earth takes for one. This describes day length; Jupiter's year depends on its much slower trip around the Sun."),
  )

  val kindness: List<String> = listOf(
      "Try thanking someone for one specific thing they did today.",
      "Ask someone about their day, then listen without planning your reply.",
      "Think of a way to include someone who might feel left out.",
      "Try giving a compliment about someone's effort or kindness.",
      "Offer to help with one small shared task, if the help is welcome.",
      "Practice disagreeing kindly: ‘I see it differently because…’",
      "Let someone finish speaking before you share your idea.",
      "Think of a time someone changed their mind. How could you make that feel safe?",
      "Ask before borrowing something, and return it with care.",
      "Think of one kind thing you can say to yourself after a mistake.",
      "Invite someone to choose the next game or activity.",
      "If you hurt someone's feelings, try naming what happened and asking how to make it right.",
      "Notice someone whose quiet work helps your day. Consider thanking them.",
      "Try asking, ‘Would you like help, or would you like me to listen?’",
  )

  val reflection: List<String> = listOf(
      "What was difficult today, and what helped you take the next step?",
      "What question would you like to understand better tomorrow?",
      "When did you show kindness today, even in a small way?",
      "What mistake taught you something useful? You can keep your answer to yourself.",
      "What is one thing you understand now that you did not understand last week?",
      "When did someone help you feel included? What did they do?",
      "What is one small step you could try before asking for help with a hard task?",
      "What made you laugh or feel curious today?",
      "What is something you practiced today, even if it is still tricky?",
      "How could you show respect when you and a friend want different things?",
      "What is one thing you feel thankful for today?",
      "Think of a time you changed your mind after learning something new.",
      "What would you tell a friend who is learning something difficult?",
      "What is one small thing you would like to try tomorrow?",
  )

  /** Reduce before adding so even extreme day/offset values cannot overflow. */
  fun index(day: Long, offset: Int, size: Int): Int {
    require(size > 0) { "Content bank must not be empty" }
    val modulus = size.toLong()
    return ((Math.floorMod(day, modulus) + Math.floorMod(offset.toLong(), modulus)) % modulus).toInt()
  }
}
