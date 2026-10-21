/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import java.math.BigInteger
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class EveningContentTest {
  @Test fun dailySelectionWrapsAndOffsetsCanMoveBothWays() {
    assertEquals(0, EveningContent.index(0, 0, 14))
    assertEquals(13, EveningContent.index(13, 0, 14))
    assertEquals(0, EveningContent.index(14, 0, 14))
    assertEquals(0, EveningContent.index(13, 1, 14))
    assertEquals(13, EveningContent.index(0, -1, 14))
    assertEquals(13, EveningContent.index(-1, 0, 14))
    assertEquals(0, EveningContent.index(-14, 0, 14))
    assertEquals(1, EveningContent.index(-14, 1, 14))
    assertEquals(0, EveningContent.index(Long.MIN_VALUE, Int.MAX_VALUE, 1))
  }

  @Test fun extremeDayAndOffsetAdditionDoesNotOverflow() {
    // BigInteger provides an independent unbounded-arithmetic oracle.
    for (day in listOf(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 0L, 20_746L)) {
      for (offset in listOf(Int.MIN_VALUE, Int.MAX_VALUE, -14, 0, 14)) {
        for (size in listOf(1, 14, 31, Int.MAX_VALUE)) {
          val expected = BigInteger.valueOf(day)
              .add(BigInteger.valueOf(offset.toLong()))
              .mod(BigInteger.valueOf(size.toLong())).toInt()
          assertEquals("day=$day offset=$offset size=$size", expected, EveningContent.index(day, offset, size))
        }
      }
    }
  }

  @Test fun emptyOrNegativeBankSizesAreRejected() {
    assertThrows(IllegalArgumentException::class.java) { EveningContent.index(1, 0, 0) }
    assertThrows(IllegalArgumentException::class.java) { EveningContent.index(1, 0, -1) }
  }

  @Test fun everyBankCoversTwoWeeksWithoutDuplicateEntries() {
    val banks: List<List<*>> = listOf(
        EveningContent.facts, EveningContent.words, EveningContent.puzzles,
        EveningContent.science, EveningContent.kindness,
        EveningContent.reflection,
    )
    banks.forEach { bank ->
      assertEquals(14, bank.size)
      assertEquals(bank.size, bank.distinct().size)
      assertEquals((0..13).toSet(), (0L..13L).map { EveningContent.index(it, 0, bank.size) }.toSet())
    }
  }

  @Test fun factsAndScienceHaveCompactTextAndPrimaryHttpsSources() {
    EveningContent.facts.forEach {
      assertCompact(it.title, 45)
      assertCompact(it.body, 190)
      assertReadingLength(it.detail, 300, 550)
      assertReadingLength(it.tryIt, 70, 180)
      assertNotEquals("Expanded facts should add explanation", it.body, it.detail)
      assertPrimarySource(it.sourceName, it.sourceUrl)
    }
    EveningContent.science.forEach {
      assertCompact(it.prompt, 150)
      assertCompact(it.answer, 180)
      assertReadingLength(it.detail, 150, 400)
      assertNotEquals("Science explanations should add reasoning", it.answer, it.detail)
      assertPrimarySource(it.sourceName, it.sourceUrl)
    }
    assertEquals(14, EveningContent.facts.map { it.title }.distinct().size)
    assertEquals(14, EveningContent.science.map { it.prompt }.distinct().size)
  }

  @Test fun bilingualCardsHaveDefinitionsAndPairedExamplesWithAccents() {
    EveningContent.words.forEach {
      assertCompact(it.english, 30)
      assertCompact(it.spanish, 35)
      assertCompact(it.definition, 120)
      assertCompact(it.example, 100)
      assertCompact(it.spanishExample, 100)
      assertCompact(it.spanishTranslation, 100)
      assertReadingLength(it.spanishDefinition, 40, 130)
      assertReadingLength(it.practice, 80, 200)
      assertNotEquals("Definitions need both languages", it.definition, it.spanishDefinition)
      assertTrue("Practice should use the target word", it.practice.contains(it.english, ignoreCase = true))
      assertTrue(it.partOfSpeech in setOf("noun", "verb", "adjective"))
      if (it.partOfSpeech == "noun") {
        assertTrue("Nouns need their Spanish article: ${it.spanish}", it.spanish.startsWith("el ") || it.spanish.startsWith("la "))
      }
      assertNotEquals("Examples need both languages", it.spanishExample, it.spanishTranslation)
    }
    assertEquals(14, EveningContent.words.map { it.english }.distinct().size)
    val empathy = EveningContent.words.single { it.english == "empathy" }
    assertEquals("la empatía", empathy.spanish)
    assertEquals("Empathy helps us understand others.", empathy.spanishTranslation)
    val observe = EveningContent.words.single { it.english == "observe" }
    assertEquals("observar", observe.spanish)
    assertEquals("I like to observe the clouds.", observe.spanishTranslation)
  }

  @Test fun activityAndPuzzleTextFitsCardsWithoutRequiringOnlineSources() {
    listOf(EveningContent.kindness, EveningContent.reflection).forEach { bank ->
      bank.forEach { assertCompact(it, 170) }
    }
    EveningContent.puzzles.forEach {
      assertCompact(it.prompt, 150)
      assertCompact(it.answer, 180)
      assertReadingLength(it.detail, 150, 400)
      assertNotEquals("Puzzle explanations should add reasoning", it.answer, it.detail)
      assertEquals("", it.sourceName)
      assertEquals("", it.sourceUrl)
    }
  }

  @Test fun pairedScienceCardsShareTheirPrimarySources() {
    // Both banks cover the same topics in the same daily rotation order.
    EveningContent.facts.zip(EveningContent.science).forEach { (fact, question) ->
      assertEquals(fact.sourceName, question.sourceName)
      assertEquals(fact.sourceUrl, question.sourceUrl)
    }
    assertEquals(14, EveningContent.facts.map { it.sourceUrl }.distinct().size)
  }

  @Test fun expandedReadingStaysOfflineAndContainsNoMarkupOrPlaceholders() {
    val reading = EveningContent.facts.flatMap { listOf(it.detail, it.tryIt) } +
        EveningContent.words.flatMap { listOf(it.spanishDefinition, it.practice) } +
        (EveningContent.science + EveningContent.puzzles).map { it.detail }
    reading.forEach {
      assertFalse("Reading must work without embedded links: $it", it.contains(Regex("https?://|www\\.", RegexOption.IGNORE_CASE)))
      assertFalse("Plain text only: $it", it.contains(Regex("<[^>]+>")))
      assertFalse("Unfinished content: $it", it.contains(Regex("\\b(TODO|TBD|FIXME)\\b")))
    }
  }

  @Test fun trickyPuzzlesHaveCorrectResultsAndUsefulExplanations() {
    val elapsedTime = EveningContent.puzzles.single { it.prompt.startsWith("A game starts") }
    val finishMinutes = 6 * 60 + 35 + 50
    assertTrue(elapsedTime.answer.startsWith("${finishMinutes / 60}:${finishMinutes % 60}."))
    assertTrue(elapsedTime.answer.contains("25 minutes"))

    val digitPuzzle = EveningContent.puzzles.single { it.prompt.startsWith("I am a two-digit") }
    val solutions = (10..99).filter { it / 10 + it % 10 == 9 && it / 10 == 2 * (it % 10) }
    assertEquals(listOf(63), solutions)
    assertTrue(digitPuzzle.answer.startsWith("${solutions.single()}."))

    val fractionPuzzle = EveningContent.puzzles.single { it.prompt.startsWith("Which is greater") }
    assertTrue(2 * 5 > 3 * 3)
    assertTrue(fractionPuzzle.answer.startsWith("2/3."))
    assertTrue(fractionPuzzle.answer.contains("10/15 and 9/15"))

    val averagePuzzle = EveningContent.puzzles.single { it.prompt.startsWith("The average") }
    assertTrue(averagePuzzle.answer.startsWith("${3 * 8 - 6 - 9}."))
  }

  private fun assertReadingLength(text: String, minimum: Int, maximum: Int) {
    assertCompact(text, maximum)
    assertTrue("${text.length} characters is below $minimum: $text", text.length >= minimum)
  }

  private fun assertCompact(text: String, maximum: Int) {
    assertTrue("Blank content", text.isNotBlank())
    assertTrue("${text.length} characters exceeds $maximum: $text", text.length <= maximum)
    assertEquals("Text should not have outer whitespace", text.trim(), text)
  }

  private fun assertPrimarySource(name: String, url: String) {
    val domains = mapOf("NASA" to "nasa.gov", "NOAA" to "noaa.gov", "NPS" to "nps.gov", "USGS" to "usgs.gov")
    val domain = domains[name]
    assertNotNull("Unrecognized primary source: $name", domain)
    val uri = URI(url)
    assertEquals("https", uri.scheme)
    assertTrue("Unexpected source host: ${uri.host}", uri.host == domain || uri.host.endsWith(".$domain"))
    assertFalse(uri.path.isNullOrBlank())
    assertNull("Sources must not contain credentials", uri.userInfo)
    assertNull("Sources should link to the article without tracking parameters", uri.query)
    assertEquals(-1, uri.port)
  }
}
