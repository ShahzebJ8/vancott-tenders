package pk.vancott.tenders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import pk.vancott.tenders.ui.TenderDetailScreen
import pk.vancott.tenders.ui.TenderListScreen
import pk.vancott.tenders.ui.TenderViewModel
import pk.vancott.tenders.ui.theme.VancottTheme
import pk.vancott.tenders.work.TenderSyncWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep checking for new SMD tenders even when the app is closed.
        TenderSyncWorker.schedule(this)

        setContent {
            VancottTheme {
                val vm: TenderViewModel = viewModel()
                // Single-activity, two screens. A navigation library would be
                // more machinery than two screens can justify.
                var openUid by remember { mutableStateOf<String?>(null) }
                val open = openUid?.let { vm.tenderByUid(it) }

                if (open != null) {
                    BackHandler { openUid = null }
                    TenderDetailScreen(open, onBack = { openUid = null })
                } else {
                    TenderListScreen(vm, onOpen = { openUid = it.uid })
                }
            }
        }
    }
}
