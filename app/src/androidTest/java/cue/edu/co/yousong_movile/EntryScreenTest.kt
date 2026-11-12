package cue.edu.co.yousong_movile

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import cue.edu.co.yousong_movile.ui.theme.YousongmovileTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class EntryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun entryOffersOnlyScanAndDoesNotJoinAutomatically() {
        var joined = false
        compose.setContent {
            YousongmovileTheme {
                EntryScreen("https://api.example.test", true, null) { joined = true }
            }
        }
        compose.onNodeWithText("Escanear QR").assertIsEnabled()
        compose.onNodeWithText("Servidor API", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Código o enlace del QR").assertDoesNotExist()
        compose.onNodeWithText("Consultar local / Reintentar").assertDoesNotExist()
        compose.onNodeWithText("Ingresar a buscar canciones").assertDoesNotExist()
        compose.runOnIdle { assertFalse(joined) }
    }

    @Test fun missingDeveloperOriginShowsErrorWithoutServerEditor() {
        compose.setContent { YousongmovileTheme { EntryScreen("", true, null) {} } }
        compose.onNodeWithText("El servicio no está configurado correctamente. Contacta al establecimiento.").assertExists()
        compose.onNodeWithText("Escanear QR").assertIsNotEnabled()
        compose.onNodeWithText("Servidor API", substring = true).assertDoesNotExist()
    }
}
