/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A daily piece opens in place; returning to the board never advances its content. */
@Composable
internal fun PocketLearningDialog(
    lesson: PocketLesson, fact: EveningFact, word: EveningWord, puzzle: EveningQuestion,
    science: EveningQuestion, kindness: String, reflection: String,
    onClose: () -> Unit,
) {
  val title = when (lesson) {
    PocketLesson.Fact -> "A little more wonder"
    PocketLesson.English, PocketLesson.Spanish -> "One idea. Two languages."
    PocketLesson.Puzzle -> "Put your thinking cap on"
    PocketLesson.Science -> "Let's find out why"
    PocketLesson.Kindness -> "Make a little difference"
    PocketLesson.Family -> "A moment together"
  }
  val art = when (lesson) {
    PocketLesson.Fact -> R.drawable.pocket_planet
    PocketLesson.Puzzle -> R.drawable.pocket_puzzle
    PocketLesson.Science -> R.drawable.pocket_science
    else -> R.drawable.pocket_dog
  }
  Dialog(onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(32.dp)) {
      val wide = maxWidth >= 1100.dp
      PocketPanel(PocketCream, Modifier.fillMaxSize(), RoundedCornerShape(44.dp), padding = if (wide) 42.dp else 28.dp) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(title, Modifier.weight(1f), style = text(if (wide) 48 else 42, FontWeight.ExtraBold, PocketInk))
            PocketButton("Back  ×", onClose, compact = true)
          }
          Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            if (wide) {
              Column(Modifier.width(280.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (lesson == PocketLesson.Kindness) PocketHeart(Modifier.size(240.dp))
                else PocketArtwork(art, Modifier.fillMaxWidth().height(280.dp))
                Text(if (lesson == PocketLesson.Kindness) "Choose kindness.\nShow you care." else "Stay curious.\nTake your time.",
                    style = text(30, FontWeight.Bold, PocketInk))
              }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
              when (lesson) {
                PocketLesson.Fact -> {
                  LessonHeading(fact.title)
                  LessonBody(fact.body)
                  LessonBody(fact.detail)
                  LessonPrompt("Try a little thinking", fact.tryIt, PocketCoral)
                  LessonSource(fact.sourceName)
                }
                PocketLesson.English, PocketLesson.Spanish -> {
                  LessonPrompt("English · ${word.partOfSpeech}", word.english, PocketSky, big = true)
                  LessonBody(word.definition)
                  LessonBody("For example: ${word.example}")
                  LessonPrompt("Español", word.spanish, PocketMint, big = true)
                  LessonBody(word.spanishDefinition)
                  LessonBody(word.spanishExample)
                  Text(word.spanishTranslation, style = text(28, FontWeight.Normal, PocketMuted))
                  LessonPrompt("Make it your own", word.practice, PocketYellow)
                }
                PocketLesson.Puzzle -> QuestionLesson(puzzle, false)
                PocketLesson.Science -> QuestionLesson(science, true)
                PocketLesson.Kindness -> {
                  Text("Today's small kindness", style = text(24, FontWeight.Bold, PocketMuted))
                  Text(kindness, style = text(if (wide) 56 else 52, FontWeight.ExtraBold, PocketInk))
                  LessonPrompt("Notice", "Who might appreciate this today? Think about what would feel helpful to them.", PocketYellow)
                  LessonPrompt("Try", "Choose a small, welcome action. You can be kind quietly; it does not need an audience.", PocketMint)
                  LessonPrompt("Reflect", "How did the other person respond? What did you notice about the way you felt?", PocketLilac)
                  LessonBody("Kindness can include you, too. If the first idea does not work, you can listen, ask, and try a different way.")
                }
                PocketLesson.Family -> {
                  LessonHeading(reflection)
                  LessonPrompt("One voice at a time", "Let each person share, or pass. Listen all the way through before you reply.", PocketSky)
                  LessonPrompt("Stay curious", "Try asking: ‘Can you tell me more?’ or ‘What helped you understand that?’", PocketMint)
                  LessonPrompt("Finish with care", "Thank someone for sharing. You can have different experiences and still treat one another with respect.", PocketYellow)
                }
              }
              Spacer(Modifier.height(12.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun QuestionLesson(question: EveningQuestion, science: Boolean) {
  var revealed by remember(question) { mutableStateOf(false) }
  LessonHeading(question.prompt)
  LessonBody(if (science) "Make a prediction first. What do you think is happening, and why?" else "Take your time. You can say your thinking aloud, make a quick sketch, or work it out together.")
  PocketButton(if (revealed) "Hide the explanation" else "I've had a think — show me!", { revealed = !revealed })
  if (revealed) {
    LessonPrompt("Here's the idea", question.answer, if (science) PocketBlue else PocketLilac)
    LessonBody(question.detail)
    if (science) LessonBody("What part of your first prediction would you keep or change now? Changing your mind is part of learning.")
    else LessonBody("Can you explain the answer in your own words? A different way to solve it can be just as useful.")
    if (question.sourceName.isNotBlank()) LessonSource(question.sourceName)
  }
}

@Composable
private fun LessonHeading(value: String) { Text(value, style = text(40, FontWeight.ExtraBold, PocketInk)) }

@Composable
private fun LessonBody(value: String) { Text(value, style = text(30, FontWeight.Medium, PocketInk)) }

@Composable
private fun LessonPrompt(label: String, value: String, color: Color, big: Boolean = false) {
  Column(Modifier.fillMaxWidth().background(color.copy(alpha = .58f), RoundedCornerShape(26.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(label, style = text(24, FontWeight.Bold, PocketMuted))
    Text(value, style = text(if (big) 48 else 32, FontWeight.Bold, PocketInk))
  }
}

@Composable
private fun LessonSource(name: String) { Text("Source: $name", style = text(24, FontWeight.Normal, PocketMuted)) }
