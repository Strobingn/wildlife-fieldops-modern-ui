package com.strobingn.wildlifefieldops.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every text color drawn on Home, against the background it actually sits on,
 * including translucent chip, button, and label washes. WCAG AA normal text is 4.5:1.
 *
 * Alphas match the composables: StatusChip 0.14, Today label 0.80, Schedule button
 * 0.18, nav indicator and drawer actions 0.14/0.16, drawer subtitle 0.75, close 0.90.
 */
class HomeTextContrastTest {

    @Test
    fun everyHomeTextPairMeetsAaInBothThemes() {
        val lines = mutableListOf<String>()
        val failures = mutableListOf<String>()
        listOf(lightChrome(), darkChrome()).forEach { chrome ->
            homeTextPairs(chrome).forEach { pair ->
                val ratio = Contrast.ratio(pair.foreground, pair.background)
                val yellow = Contrast.isYellowAmberOrangeOrLime(pair.foreground)
                val line = "%-5s %6.2f  %s".format(chrome.name, ratio, pair.name)
                lines += line
                if (ratio + 1e-9 < Contrast.AA_NORMAL) failures += "below AA: $line"
                if (yellow) failures += "yellow text: $line"
            }
            homeIconTints(chrome).forEach { tint ->
                val yellow = Contrast.isYellowAmberOrangeOrLime(tint.color)
                val line = "%-5s  icon  %s %s".format(
                    chrome.name,
                    tint.name,
                    tint.color.toULong().toString(16)
                )
                lines += line
                if (yellow) failures += "yellow icon-with-label: $line"
            }
        }
        val report = lines.joinToString("\n")
        println(report)
        assertTrue(failures.joinToString("\n") + "\n" + report, failures.isEmpty())
    }

    @Test
    fun urgentTokensAreRedNotTheOldYellow() {
        assertNotEquals(0xFFFFCC80, FieldSwatch.Dark.StatusUrgent)
        assertNotEquals(0xFF9A3412, FieldSwatch.Light.StatusUrgent)
        assertEquals(0xFF3A3A3A, FieldSwatch.Light.Primary)
        assertEquals(0xFFD0D0D0, FieldSwatch.Dark.Primary)
        assertFalse(Contrast.isYellowAmberOrangeOrLime(FieldSwatch.Light.AccentOrange))
        assertFalse(Contrast.isYellowAmberOrangeOrLime(FieldSwatch.Dark.AccentOrange))
        assertFalse(Contrast.isYellowAmberOrangeOrLime(FieldSwatch.Light.Success))
        assertFalse(Contrast.isYellowAmberOrangeOrLime(FieldSwatch.Dark.Success))
    }

    @Test
    fun homeSourcesMatchTheMeasuredWashes() {
        val dash = readSource("ui/screens/DashboardScreen.kt")
        val common = readSource("ui/components/CommonComponents.kt")
        val main = readSource("MainActivity.kt")
        val weather = readSource("ui/components/WeatherBanner.kt")
        val update = readSource("ui/screens/AppUpdateUi.kt")
        assertTrue(dash.contains("listOf(GradientStart, GradientMid, GradientEnd)"))
        assertFalse(dash.contains("PrimaryContainer"))
        assertFalse(dash.contains("OnHeroWarning"))
        assertFalse(dash.contains("overdue", ignoreCase = true))
        assertFalse(dash.contains("color = StatusUrgent"))
        assertTrue(dash.contains("OnPrimary.copy(alpha = 0.8f)"))
        assertTrue(dash.contains("OnPrimary.copy(alpha = 0.18f)"))
        assertTrue(dash.contains("ManualJobEntry.ACTION_LABEL"))
        assertTrue(dash.contains("VoiceJobEntry.ACTION_LABEL"))
        assertTrue(common.contains("color.copy(alpha = 0.14f)"))
        assertTrue(main.contains("Brush.verticalGradient(listOf(GradientStart, GradientEnd))"))
        assertTrue(main.contains("OnPrimary.copy(alpha = 0.75f)"))
        assertTrue(main.contains("OnPrimary.copy(alpha = 0.9f)"))
        assertTrue(main.contains("primary.copy(alpha = 0.16f)"))
        assertTrue(main.contains("primary.copy(alpha = 0.14f)"))
        assertTrue(weather.contains("color = TextPrimary") || weather.contains("color = TextSecondary"))
        assertTrue(update.contains("fun AppUpdateHomeChip"))
        assertTrue(update.contains("color = TextPrimary"))
    }

    private data class Chrome(
        val name: String,
        val page: Long,
        val card: Long,
        val tile: Long,
        val onSurface: Long,
        val onVariant: Long,
        val onMuted: Long,
        val onBackground: Long,
        val primary: Long,
        val onPrimary: Long,
        val onPrimaryContainer: Long,
        val dictateFill: Long,
        val dictateLabel: Long,
        val error: Long,
        val pending: Long,
        val inProgress: Long,
        val success: Long,
        val purple: Long,
        val heroWarning: Long,
        val urgentOnCard: Long,
        val accentOrange: Long,
        val accentBlue: Long,
        val accentCyan: Long,
        val hero: List<Long>
    )

    private data class Pair(val name: String, val foreground: Long, val background: Long)
    private data class IconTint(val name: String, val color: Long)

    private fun lightChrome() = Chrome(
        name = "LIGHT",
        page = FieldSwatch.Light.Background,
        card = FieldSwatch.Light.Card,
        tile = FieldSwatch.Light.Elevated,
        onSurface = FieldSwatch.Light.OnSurface,
        onVariant = FieldSwatch.Light.OnSurfaceVariant,
        onMuted = FieldSwatch.Light.OnSurfaceMuted,
        onBackground = FieldSwatch.Light.OnBackground,
        primary = FieldSwatch.Light.Primary,
        onPrimary = FieldSwatch.Light.OnPrimary,
        onPrimaryContainer = FieldSwatch.Light.OnPrimaryContainer,
        dictateFill = FieldSwatch.Light.SecondaryContainer,
        dictateLabel = FieldSwatch.Light.OnPrimaryContainer,
        error = FieldSwatch.Light.Error,
        pending = FieldSwatch.Light.StatusPending,
        inProgress = FieldSwatch.Light.StatusInProgress,
        success = FieldSwatch.Light.Success,
        purple = FieldSwatch.Light.AccentPurple,
        heroWarning = FieldSwatch.Light.OnHeroWarning,
        urgentOnCard = FieldSwatch.Light.StatusUrgent,
        accentOrange = FieldSwatch.Light.AccentOrange,
        accentBlue = FieldSwatch.Light.AccentBlue,
        accentCyan = FieldSwatch.Light.AccentCyan,
        hero = listOf(
            FieldSwatch.Light.GradientStart,
            FieldSwatch.Light.GradientMid,
            FieldSwatch.Light.GradientEnd
        )
    )

    private fun darkChrome() = Chrome(
        name = "DARK",
        page = FieldSwatch.Dark.Background,
        card = FieldSwatch.Dark.Card,
        tile = FieldSwatch.Dark.Card,
        onSurface = FieldSwatch.Dark.OnSurface,
        onVariant = FieldSwatch.Dark.OnSurfaceVariant,
        onMuted = FieldSwatch.Dark.OnSurfaceMuted,
        onBackground = FieldSwatch.Dark.OnBackground,
        primary = FieldSwatch.Dark.Primary,
        onPrimary = FieldSwatch.Dark.OnPrimary,
        onPrimaryContainer = FieldSwatch.Dark.OnPrimaryContainer,
        dictateFill = FieldSwatch.Dark.PrimaryDark,
        dictateLabel = FieldSwatch.Dark.OnPrimary,
        error = FieldSwatch.Dark.Error,
        pending = FieldSwatch.Dark.StatusPending,
        inProgress = FieldSwatch.Dark.StatusInProgress,
        success = FieldSwatch.Dark.Success,
        purple = FieldSwatch.Dark.AccentPurple,
        heroWarning = FieldSwatch.Dark.OnHeroWarning,
        urgentOnCard = FieldSwatch.Dark.StatusUrgent,
        accentOrange = FieldSwatch.Dark.AccentOrange,
        accentBlue = FieldSwatch.Dark.AccentBlue,
        accentCyan = FieldSwatch.Dark.AccentCyan,
        hero = listOf(
            FieldSwatch.Dark.GradientStart,
            FieldSwatch.Dark.GradientMid,
            FieldSwatch.Dark.GradientEnd
        )
    )

    private fun homeTextPairs(chrome: Chrome): List<Pair> {
        val pairs = mutableListOf<Pair>()
        fun solid(name: String, fg: Long, bg: Long) {
            pairs += Pair(name, fg, bg)
        }
        fun wash(name: String, fg: Long, surface: Long, alpha: Double) {
            pairs += Pair(name, fg, Contrast.composite(fg, surface, alpha))
        }
        fun faded(name: String, ink: Long, surface: Long, alpha: Double) {
            pairs += Pair(name, Contrast.composite(ink, surface, alpha), surface)
        }

        solid("greeting + date on page", chrome.onVariant, chrome.page)
        solid("FieldOps title on page", chrome.onBackground, chrome.page)
        solid("section title on page", chrome.onBackground, chrome.page)
        solid("section action (View all) on page", chrome.primary, chrome.page)
        solid("empty title on page", chrome.onSurface, chrome.page)
        solid("empty subtitle on page", chrome.onVariant, chrome.page)

        solid("stat / metric / quick-action label on tile", chrome.onVariant, chrome.tile)
        solid("stat value + metric value on tile", chrome.onSurface, chrome.tile)
        solid("At a glance title on tile", chrome.onSurface, chrome.tile)
        solid("job title + reminder title on tile", chrome.onSurface, chrome.tile)
        solid("job customer, address, reminder date on tile", chrome.onVariant, chrome.tile)

        solid("weather title + temperature on tile", chrome.onSurface, chrome.tile)
        solid("weather description + humidity/wind on tile", chrome.onVariant, chrome.tile)
        solid("weather place + unavailable reason on tile", chrome.onMuted, chrome.tile)

        solid("update chip title on card", chrome.onSurface, chrome.card)
        solid("update chip subtitle + banner body on card", chrome.onVariant, chrome.card)
        solid("update banner View on card", chrome.primary, chrome.card)
        solid("update banner Later on card", chrome.onVariant, chrome.card)

        solid("trap / next-step title on card", chrome.onSurface, chrome.card)
        solid("trap / next-step body on card", chrome.onVariant, chrome.card)
        solid("trap due + next-step time on card", chrome.onMuted, chrome.card)

        solid("sync Synced / Syncing on tile", chrome.onVariant, chrome.tile)
        solid("sync Pending on tile", chrome.onMuted, chrome.tile)
        solid("sync failed on tile", chrome.error, chrome.tile)

        solid("bottom nav unselected on tile", chrome.onVariant, chrome.tile)
        wash("bottom nav selected on indicator", chrome.primary, chrome.tile, 0.16)

        solid("New Job FAB label on primary", chrome.onPrimary, chrome.primary)
        solid("Dictate FAB label on secondary fill", chrome.dictateLabel, chrome.dictateFill)

        solid("drawer tool label on sheet", chrome.onSurface, chrome.tile)
        solid("drawer TOOLS caption on sheet", chrome.onVariant, chrome.tile)
        wash("drawer New Job / Dictate label on action wash", chrome.primary, chrome.tile, 0.14)

        wash("job chip PENDING/LEAD/ESTIMATE on tile", chrome.pending, chrome.tile, 0.14)
        wash("job chip SCHEDULED/IN PROGRESS/TRAPPING/EXCLUSION on tile", chrome.inProgress, chrome.tile, 0.14)
        wash("job chip COMPLETED/CLOSED on tile", chrome.success, chrome.tile, 0.14)
        wash("job chip CANCELLED on tile", chrome.error, chrome.tile, 0.14)
        wash("job chip INVOICED on tile", chrome.purple, chrome.tile, 0.14)
        wash("job chip PAID on tile", chrome.primary, chrome.tile, 0.14)

        val samples = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
        samples.forEach { t ->
            val under = gradientAt(chrome.hero, t)
            val where = "hero t=${"%.2f".format(t)}"
            faded("Today label on $where", chrome.onPrimary, under, 0.8)
            solid("jobs scheduled on $where", chrome.onPrimary, under)
            wash("Schedule button on $where", chrome.onPrimary, under, 0.18)
            solid("route button on $where", chrome.onPrimary, under)
        }
        samples.forEach { t ->
            val under = Contrast.blend(chrome.hero.first(), chrome.hero.last(), t)
            val where = "drawer header t=${"%.2f".format(t)}"
            solid("drawer title on $where", chrome.onPrimary, under)
            faded("drawer subtitle on $where", chrome.onPrimary, under, 0.75)
            faded("drawer close on $where", chrome.onPrimary, under, 0.9)
        }
        return pairs
    }

    private fun homeIconTints(chrome: Chrome): List<IconTint> = listOf(
        IconTint("Active stat icon AccentBlue", chrome.accentBlue),
        IconTint("Pending stat icon StatusPending", chrome.pending),
        IconTint("Done stat icon Success", chrome.success),
        IconTint("Inspections stat icon AccentCyan", chrome.accentCyan),
        IconTint("Customers overview icon AccentPurple", chrome.purple),
        IconTint("Follow-ups overview icon AccentOrange", chrome.accentOrange),
        IconTint("Overdue overview icon StatusUrgent", chrome.urgentOnCard),
        IconTint("quick action New Job / Dictate Primary", chrome.primary),
        IconTint("reminder icon AccentOrange", chrome.accentOrange),
        IconTint("AI button icon AccentPurple", chrome.purple),
        IconTint("weather icon Primary", chrome.primary)
    )

    private fun gradientAt(stops: List<Long>, t: Double): Long {
        val clamped = t.coerceIn(0.0, 1.0)
        val scaled = clamped * (stops.size - 1)
        val index = scaled.toInt().coerceAtMost(stops.size - 2)
        val local = scaled - index
        return Contrast.blend(stops[index], stops[index + 1], local)
    }

    private fun readSource(relativeUnderJava: String): String {
        val suffix = "src/main/java/com/strobingn/wildlifefieldops/$relativeUnderJava"
        val file = listOf(File(suffix), File("app/$suffix"), File("../$suffix"))
            .firstOrNull { it.isFile }
            ?: error("Missing $suffix (cwd=${File(".").canonicalPath})")
        return file.readText()
    }
}
