/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val pocketGreetings = listOf("Little questions. Big discoveries.", "A little curiosity goes a long way!", "Hard things get easier when we try.", "Be curious. Be brave. Be kind.")
internal enum class PocketLesson { Fact, English, Spanish, Puzzle, Science, Kindness, Family }

/** The persisted Evening face is presented as Discover, available at any time. */
@Composable
internal fun EveningDashboard(active: Boolean) {
  PocketArtHost { PocketDailyBoard(active) }
}

@Composable
private fun PocketDailyBoard(active: Boolean) {
  val visible = dashboardIsVisible(active)
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  val day = PortalOtaProtocol.date(PortalOtaProtocol.localDay(now)) / 86_400_000L
  val remote = PortalOta.current?.day(now)
  LaunchedEffect(visible) {
    if (visible) while (true) {
      now = System.currentTimeMillis()
      delay(60_000 - now % 60_000)
    }
  }
  val fact = remote?.fact ?: EveningContent.facts[EveningContent.index(day, 0, EveningContent.facts.size)]
  val word = remote?.word ?: EveningContent.words[EveningContent.index(day, 0, EveningContent.words.size)]
  val puzzle = remote?.puzzle ?: EveningContent.puzzles[EveningContent.index(day, 0, EveningContent.puzzles.size)]
  val science = remote?.science ?: EveningContent.science[EveningContent.index(day, 0, EveningContent.science.size)]
  val kindness = remote?.kindness ?: EveningContent.kindness[EveningContent.index(day, 0, EveningContent.kindness.size)]
  val reflection = remote?.reflection ?: EveningContent.reflection[EveningContent.index(day, 0, EveningContent.reflection.size)]
  val content = remote ?: PortalDay("bundled-$day", PortalOtaProtocol.localDay(now), fact, word, puzzle, science, kindness, reflection, "")
  // The lesson keeps all content from its opening tap, including across local midnight.
  var lesson by remember { mutableStateOf<Pair<PocketLesson, PortalDay>?>(null) }
  DisposableEffect(lesson != null) {
    val held = lesson != null
    if (held) PortalOta.holdLesson()
    onDispose { if (held) PortalOta.releaseLesson() }
  }
  fun open(kind: PocketLesson) { lesson = kind to content }
  var greeting by remember { mutableIntStateOf(0) }
  val dogTilt by animateFloatAsState(if (greeting % 2 == 0) -3f else 3f, tween(180), label = "dog greeting")

  BoxWithConstraints(Modifier.fillMaxSize().background(PocketCream)) {
    val wide = maxWidth >= DASHBOARD_WIDE_MIN_DP.dp
    PocketArtwork(R.drawable.pocket_garden, Modifier.fillMaxSize(), scale = ContentScale.Crop)
    Column(Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 14.dp, bottom = 84.dp)) {
      Box(Modifier.fillMaxWidth().height(232.dp)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
          Text("Stay curious", style = text(if (wide) 62 else 52, FontWeight.ExtraBold, PocketInk))
          Text(pocketGreetings[greeting], style = text(21, FontWeight.Medium, PocketMuted), textAlign = TextAlign.Center)
        }
        Box(Modifier.align(Alignment.BottomStart).padding(start = if (wide) 24.dp else 0.dp)
            .size(if (wide) 440.dp else 320.dp, 232.dp).graphicsLayer { rotationZ = dogTilt }
            .clickable(role = Role.Button, onClickLabel = "Show a friendly thought") { greeting = (greeting + 1) % pocketGreetings.size }) {
          PocketArtwork(R.drawable.pocket_dog, Modifier.fillMaxSize(), description = "Your dog, waving hello")
        }
      }
      Spacer(Modifier.height(14.dp))
      @Composable fun Fact(modifier: Modifier) {
        PocketFactCard(fact, modifier) { open(PocketLesson.Fact) }
      }
      @Composable fun Words(spanish: Boolean, modifier: Modifier) {
        PocketWordCard(word, spanish, modifier) { open(if (spanish) PocketLesson.Spanish else PocketLesson.English) }
      }
      @Composable fun Puzzle(modifier: Modifier) {
        PocketQuestionCard("Brain teaser", "🧩", puzzle, false, modifier) { open(PocketLesson.Puzzle) }
      }
      @Composable fun Science(modifier: Modifier) {
        PocketQuestionCard("Science question", "🧪", science, true, modifier) { open(PocketLesson.Science) }
      }
      @Composable fun Kindness(modifier: Modifier) {
        PocketKindnessCard(kindness, modifier) { open(PocketLesson.Kindness) }
      }
      @Composable fun Family(modifier: Modifier) {
        PocketFamilyCard(reflection, modifier) { open(PocketLesson.Family) }
      }
      if (wide) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
          Row(Modifier.weight(.72f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Fact(Modifier.weight(1.15f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
              Words(false, Modifier.weight(1f).fillMaxWidth())
              Words(true, Modifier.weight(1f).fillMaxWidth())
            }
            Puzzle(Modifier.weight(.82f).fillMaxHeight())
            Science(Modifier.weight(1f).fillMaxHeight())
          }
          Row(Modifier.weight(.28f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Kindness(Modifier.weight(.39f).fillMaxHeight())
            Family(Modifier.weight(.61f).fillMaxHeight())
          }
        }
      } else {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
          Row(Modifier.fillMaxWidth().height(540.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Fact(Modifier.weight(1f).fillMaxHeight()); Puzzle(Modifier.weight(1f).fillMaxHeight())
          }
          Row(Modifier.fillMaxWidth().height(340.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Words(false, Modifier.weight(1f).fillMaxHeight()); Words(true, Modifier.weight(1f).fillMaxHeight())
          }
          Science(Modifier.fillMaxWidth().height(460.dp))
          Kindness(Modifier.fillMaxWidth().height(280.dp))
          Family(Modifier.fillMaxWidth().height(300.dp))
        }
      }
    }
  }
  lesson?.let { (selected, snapshot) ->
    PocketLearningDialog(selected, snapshot.fact, snapshot.word, snapshot.puzzle, snapshot.science, snapshot.kindness, snapshot.reflection) { lesson = null }
  }
}

@Composable
private fun PocketFactCard(fact: EveningFact, modifier: Modifier, onOpen: () -> Unit) {
  PocketPanel(PocketCoral, modifier, RoundedCornerShape(48.dp), onClick = onOpen, clickLabel = "Explore this fact") {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      PocketBadge("💡", "Fun fact")
      Text(fact.title, style = text(36, FontWeight.ExtraBold, PocketInk))
      Text(fact.body, style = text(28, FontWeight.Medium, PocketInk))
      Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        PocketArtwork(R.drawable.pocket_planet, Modifier.fillMaxSize().padding(8.dp))
      }
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        PocketButton("Explore  ›", onOpen)
        Text("Source: ${fact.sourceName}", style = text(22, FontWeight.Normal, PocketInk))
      }
    }
  }
}

@Composable
private fun PocketWordCard(word: EveningWord, spanish: Boolean, modifier: Modifier, onOpen: () -> Unit) {
  PocketPanel(if (spanish) PocketMint else PocketSky, modifier, RoundedCornerShape(76.dp), padding = 20.dp,
      onClick = onOpen, clickLabel = "Practice this word in English and Spanish") {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      PocketBadge(if (spanish) "💬" else "📖", if (spanish) "Español" else "English")
      Text(if (spanish) word.spanish else word.english, style = text(42, FontWeight.ExtraBold, PocketInk))
      Text(if (spanish) word.spanishDefinition else word.definition, style = text(28, FontWeight.Medium, PocketInk))
      Spacer(Modifier.weight(1f))
    }
  }
}

@Composable
private fun PocketQuestionCard(label: String, icon: String, question: EveningQuestion, science: Boolean, modifier: Modifier, onOpen: () -> Unit) {
  var revealed by remember(question) { mutableStateOf(false) }
  PocketPanel(if (science) PocketBlue else PocketLilac, modifier,
      if (science) RoundedCornerShape(topStart = 108.dp, topEnd = 108.dp, bottomStart = 40.dp, bottomEnd = 40.dp) else RoundedCornerShape(46.dp),
      onClick = onOpen, clickLabel = "Explore the question and its explanation") {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      PocketBadge(icon, if (revealed) "$label · Answer" else label,
          if (science) Modifier.align(Alignment.CenterHorizontally) else Modifier)
      Text(if (revealed) question.answer else question.prompt, style = text(30, FontWeight.Bold, PocketInk))
      Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        PocketArtwork(if (science) R.drawable.pocket_science else R.drawable.pocket_puzzle,
            Modifier.fillMaxSize().padding(8.dp))
      }
      if (question.sourceName.isNotEmpty()) Text("Source: ${question.sourceName}", style = text(22, FontWeight.Normal, PocketInk))
      PocketButton(if (revealed) "Flip back" else "Flip for answer", { revealed = !revealed }, Modifier.fillMaxWidth(), compact = true)
    }
  }
}

@Composable
private fun PocketKindnessCard(prompt: String, modifier: Modifier, onOpen: () -> Unit) {
  PocketPanel(PocketYellow, modifier, RoundedCornerShape(90.dp), padding = 12.dp, onClick = onOpen, clickLabel = "Try today's kindness challenge") {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      PocketBadge("♥", "Kindness challenge")
      Text(prompt, style = text(32, FontWeight.Bold, PocketInk))
    }
  }
}

@Composable
private fun PocketFamilyCard(prompt: String, modifier: Modifier, onOpen: () -> Unit) {
  PocketPanel(Color(0xFFFFF2D8), modifier, PocketTicketShape, padding = 28.dp, onClick = onOpen, clickLabel = "Start a family conversation") {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PocketBadge("☀", "Family conversation")
        Text(prompt, style = text(34, FontWeight.ExtraBold, PocketInk))
      }
      PocketConversation(Modifier.size(112.dp))
    }
  }
}
