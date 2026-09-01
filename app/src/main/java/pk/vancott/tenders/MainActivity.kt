package pk.vancott.tenders

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import pk.vancott.tenders.ui.*
import pk.vancott.tenders.ui.theme.VancottTheme
import pk.vancott.tenders.ui.theme.Void
import pk.vancott.tenders.work.TenderSyncWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        TenderSyncWorker.schedule(this)

        setContent {
            VancottTheme {
                val vm: TenderViewModel = viewModel()
                val s by vm.state.collectAsState()
                val results by vm.results.collectAsState()

                val askNotifications = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                var section by remember { mutableStateOf(Section.TENDERS) }
                var drawerOpen by remember { mutableStateOf(false) }
                var openUid by remember { mutableStateOf<String?>(null) }
                var filtersOpen by remember { mutableStateOf(false) }
                val open = openUid?.let { vm.tenderByUid(it) }

                if (s.starting) {
                    SplashScreen()
                    return@VancottTheme
                }

                Box(Modifier.fillMaxSize().background(Void)) {
                    when {
                        open != null -> {
                            BackHandler { openUid = null }
                            TenderDetailScreen(open, vm, onBack = { openUid = null })
                        }

                        filtersOpen -> {
                            BackHandler { filtersOpen = false }
                            FilterSheet(s, s.feed?.tenders.orEmpty(), vm) { filtersOpen = false }
                        }

                        else -> when (section) {
                            Section.TENDERS, Section.SHORTLIST -> {
                                // Shortlist is the tender list with one filter
                                // applied, so it behaves identically everywhere.
                                LaunchedEffect(section) {
                                    vm.setScope(
                                        if (section == Section.SHORTLIST) Scope.STARRED
                                        else Scope.ALL
                                    )
                                }
                                TenderListScreen(
                                    vm = vm,
                                    onOpen = { openUid = it.uid },
                                    onFilters = { filtersOpen = true },
                                    onMenu = { drawerOpen = true },
                                )
                            }

                            Section.ALERTS -> AlertsScreen(vm) { section = Section.TENDERS }
                            Section.NEWS -> NewsScreen(vm) { drawerOpen = true }
                            Section.ECONOMY -> EconomyScreen(vm) { drawerOpen = true }
                            Section.ABOUT -> AboutScreen(s) { drawerOpen = true }
                        }
                    }

                    if (drawerOpen) BackHandler { drawerOpen = false }
                    DrawerScrim(drawerOpen) { drawerOpen = false }

                    AnimatedVisibility(
                        visible = drawerOpen,
                        enter = slideInHorizontally { -it },
                        exit = slideOutHorizontally { -it },
                    ) {
                        DrawerMenu(
                            current = section,
                            counts = mapOf(
                                Section.TENDERS to results.size,
                                Section.SHORTLIST to s.notes.count { it.value.starred },
                                Section.ALERTS to s.searches.size,
                                Section.NEWS to (s.news?.stories?.size ?: 0),
                            ),
                            onPick = {
                                section = it
                                drawerOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}
