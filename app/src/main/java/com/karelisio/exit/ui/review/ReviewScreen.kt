package com.karelisio.exit.ui.review

import com.karelisio.exit.plural
import com.karelisio.exit.str
import androidx.compose.ui.res.stringResource
import com.karelisio.exit.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.exit.domain.PlanEngine
import com.karelisio.exit.domain.WeeklyReviewCalculator
import com.karelisio.exit.ui.AppViewModel
import com.karelisio.exit.ui.components.CountUpText
import com.karelisio.exit.ui.components.ExitCard
import com.karelisio.exit.ui.components.ExitTopBar
import com.karelisio.exit.ui.components.Fmt
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.components.SmokeBurst
import com.karelisio.exit.ui.components.cascade
import com.karelisio.exit.ui.navigation.Navigator
import java.time.LocalDate
import java.time.ZoneId

/** Bilan des 7 derniers jours, comparé à la semaine d'avant, avec célébration à l'ouverture. */
@Composable
fun ReviewScreen(vm: AppViewModel, nav: Navigator) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val review = remember(d) { WeeklyReviewCalculator.compute(d.config, d.smokes, d.cravings, LocalDate.now(), ZoneId.systemDefault()) }
    var burst by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { burst++ }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = { ExitTopBar(stringResource(R.string.review_ta_semaine), scroll, onBack = { nav.back() }) },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ExitCard(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.cascade(0)) {
                        val on = MaterialTheme.colorScheme.onPrimaryContainer
                        Row {
                            ShapeIcon(Icons.Rounded.CalendarMonth, MaterialShapes.Cookie9Sided, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.review_du_au, Fmt.dayShort(review.start), Fmt.dayShort(review.end)), style = MaterialTheme.typography.labelLarge, color = on)
                        CountUpText(review.smokes.toDouble(), { plural(R.plurals.cigarettes_count, it.toInt(), it.toInt()) }, MaterialTheme.typography.displaySmall, color = on)
                        val change = review.change
                        Text(
                            when {
                                change == null -> stringResource(R.string.review_premiere_semaine_suivie_c_est)
                                change < 0 -> stringResource(R.string.review_less, -change)
                                change == 0 -> stringResource(R.string.review_autant_que_la_semaine_d)
                                else -> stringResource(R.string.review_more, change)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = on,
                        )
                    }
                }
                item { Line(stringResource(R.string.stats_envies_surmontees), "${review.cravingsOvercome} / ${review.cravingsTotal}", Modifier.cascade(1)) }
                item { Line(stringResource(R.string.review_bonus_tenu), if (review.bonusMinutes > 0) Fmt.duration(review.bonusMinutes * 60_000L) else "—", Modifier.cascade(2)) }
                item { Line(stringResource(R.string.review_economise_cette_semaine), Fmt.money(review.money), Modifier.cascade(3)) }
                item { Line(stringResource(R.string.review_jours_sans_fumer), "${review.smokeFreeDays}", Modifier.cascade(4)) }
                review.bestDay?.let { (day, count) ->
                    item { Line(stringResource(R.string.review_meilleur_jour), "${Fmt.dayLabel(day)} · $count", Modifier.cascade(5)) }
                }
                review.morningDelay?.let { delay ->
                    item {
                        val prev = review.previousMorningDelay
                        val diff = prev?.let { delay - it }
                        Line(
                            stringResource(R.string.review_premiere_du_matin_apres_le),
                            PlanEngine.formatDuration(delay) + (diff?.takeIf { it != 0 }?.let { if (it > 0) stringResource(R.string.review_min, it) else stringResource(R.string.review_min_2, it) } ?: ""),
                            Modifier.cascade(6),
                        )
                    }
                }
            }
        }
        SmokeBurst(burst, Modifier.fillMaxSize())
    }
}

@Composable
private fun Line(label: String, value: String, modifier: Modifier = Modifier) {
    ExitCard(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
