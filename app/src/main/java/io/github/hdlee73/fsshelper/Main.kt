package io.github.hdlee73.fsshelper

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsshelper.criteria.CriteriaEnv
import io.github.hdlee73.fsshelper.criteria.CriteriaStore
import io.github.hdlee73.fsshelper.criteria.FineScreen
import io.github.hdlee73.fsshelper.criteria.LevelScreen
import io.github.hdlee73.fsshelper.criteria.PenaltyScreen
import io.github.hdlee73.fsshelper.criteria.RegulationModel
import io.github.hdlee73.fsshelper.dart.DartCorp
import io.github.hdlee73.fsshelper.dart.DartModel
import io.github.hdlee73.fsshelper.dart.DartScreen
import io.github.hdlee73.fsshelper.data.Recent
import io.github.hdlee73.fsshelper.data.RecentKind
import io.github.hdlee73.fsshelper.data.RecentStore
import io.github.hdlee73.fsshelper.data.SettingsRepo
import io.github.hdlee73.fsshelper.home.AboutScreen
import io.github.hdlee73.fsshelper.home.HomeBadges
import io.github.hdlee73.fsshelper.home.HomeScreen
import io.github.hdlee73.fsshelper.home.KeyGuideDialog
import io.github.hdlee73.fsshelper.home.KeyKind
import io.github.hdlee73.fsshelper.home.PrefetchUi
import io.github.hdlee73.fsshelper.home.SettingsScreen
import io.github.hdlee73.fsshelper.home.testDart
import io.github.hdlee73.fsshelper.home.testLaw
import io.github.hdlee73.fsshelper.law.LawModule
import io.github.hdlee73.fsshelper.law.LawScreen
import io.github.hdlee73.fsshelper.law.LawSearchModel
import io.github.hdlee73.fsshelper.law.LawUiState
import io.github.hdlee73.fsshelper.library.FormCatalog
import io.github.hdlee73.fsshelper.library.FormsScreen
import io.github.hdlee73.fsshelper.library.LibraryActions
import io.github.hdlee73.fsshelper.library.LibraryStore
import io.github.hdlee73.fsshelper.library.LibraryUi
import io.github.hdlee73.fsshelper.library.ManualScreen
import io.github.hdlee73.fsshelper.todo.TodoScreen
import io.github.hdlee73.fsshelper.todo.TodoStore
import io.github.hdlee73.fsshelper.ui.AppTheme
import io.github.hdlee73.fsshelper.ui.Dest
import io.github.hdlee73.fsshelper.ui.NavGroup
import io.github.hdlee73.fsshelper.ui.NavyBand
import io.github.hdlee73.fsshelper.ui.Navigator
import io.github.hdlee73.fsshelper.ui.Os
import io.github.hdlee73.fsshelper.update.UpdateModel
import io.github.hdlee73.fsshelper.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FssApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        Platform.init(this)
        graph = AppGraph()
    }
}

/** 앱 전체에서 쓰는 모델 묶음. 앱 프로세스에 하나만 있다(화면을 돌려도 유지). */
class AppGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings = SettingsRepo()
    val nav = Navigator()
    val law = LawModule(settings)
    val lawSearch = LawSearchModel(law, settings, scope)
    val dart = DartModel(settings, scope)
    val library = LibraryStore()
    val libraryUi = LibraryUi()
    val criteria = CriteriaStore()
    val regulation = RegulationModel(law.repository, scope)
    val todo = TodoStore()
    val recents = RecentStore()
    val update = UpdateModel(settings, scope)
    val prefetch = PrefetchUi()
    val messages = Channel<String>(Channel.BUFFERED)

    /** 이번 실행에서 이미 띄운 인증키 안내(법령·DART) */
    var keyGuideShown by mutableStateOf(setOf<String>())
    /** 지금 띄울 인증키 안내 */
    var keyGuide by mutableStateOf<KeyKind?>(null)

    fun message(text: String) { messages.trySend(text) }

    val criteriaEnv = CriteriaEnv(criteria, regulation, openLaw = { q -> searchLaw(q) }, message = { message(it) })

    val libraryActions = LibraryActions(
        writeForm = this::writeForm,
        openedManual = { title, page -> recents.touch(RecentKind.MANUAL, title, page.toString(), "검사매뉴얼 ${page + 1}쪽") },
        openedForm = { no -> FormCatalog.form(no).let { f -> recents.touch(RecentKind.FORM, f.label.trim(), no, "검사관련 서식") } },
        message = this::message,
    )

    init {
        nav.registerInnerBack(Dest.LAW) { lawSearch.back() }
        nav.registerInnerBack(Dest.DART) { dart.back() }
        nav.registerInnerBack(Dest.MANUAL) { libraryUi.backManual() }
        nav.registerInnerBack(Dest.FORMS) { libraryUi.backForms() }
        scope.launch {
            lawSearch.state.collect { st ->
                if (st is LawUiState.Tree) recents.touch(RecentKind.LAW, st.tree.query.raw, st.tree.query.raw, st.tree.law.name)
            }
        }
        scope.launch {
            dart.selected.collect { c -> if (c != null) recents.touch(RecentKind.CORP, c.name, listOf(c.corp, c.name, c.stock, c.cls).joinToString("|"), "기업 개황") }
        }
        // 매뉴얼·서식 묶음은 미리 풀어 둔다(처음 한 번만 몇 초).
        scope.launch { library.ensure() }
    }

    fun searchLaw(q: String) {
        nav.go(Dest.LAW)
        lawSearch.onQueryChange(q)
        lawSearch.submit(q)
    }

    /**
     * 서식을 한글 앱(한컴오피스 Viewer 등)으로 연다. 휴대폰에서는 서식 한 개만 잘라 낼 수 없어
     * 서식 전체 원본을 열고, 찾을 서식 번호를 알려 준다.
     */
    fun writeForm(no: String) {
        scope.launch {
            val src = withContext(Dispatchers.IO) { library.formsHwp() }
            if (src == null) { message("서식 한글 원본이 앱에 들어 있지 않습니다."); return@launch }
            val form = FormCatalog.form(no)
            if (Os.open(src)) message("한글 앱에서 '제${no}호'를 찾아 쓰세요. (${form.label.trim()})")
            else message("한글 파일을 열 앱이 없습니다. Play 스토어에서 '한컴오피스 Viewer'를 설치해 주세요.")
        }
    }

    /** 홈 화면 최근 목록에서 다시 열기 */
    fun openRecent(r: Recent) {
        when (r.type) {
            RecentKind.FORM -> { libraryUi.formNo = r.ref; nav.go(Dest.FORMS) }
            RecentKind.MANUAL -> { libraryUi.manualJump = r.ref.toIntOrNull(); nav.go(Dest.MANUAL) }
            RecentKind.LAW -> searchLaw(r.ref)
            RecentKind.CORP -> {
                val p = r.ref.split('|')
                dart.open(DartCorp(p[0], p.getOrElse(1) { r.title }, p.getOrElse(2) { "" }, p.getOrElse(3) { "" }))
                nav.go(Dest.DART)
            }
            RecentKind.FILE -> {
                val f = File(r.ref)
                if (f.isFile) Os.open(f) else { message("파일이 없습니다(지웠을 수 있습니다)."); recents.remove(r.key) }
            }
        }
    }

    /** 키 없이 법률 검색·기업 개황·기준 원문을 처음 열면 발급 안내를 띄운다(다시 보지 않기 전까지 실행마다 한 번). */
    fun maybeKeyGuide(dest: Dest) {
        val kind = when (dest) {
            Dest.LAW, Dest.LEVEL, Dest.PENALTY, Dest.FINE -> KeyKind.LAW.takeIf { settings.lawOc.isBlank() }
            Dest.DART -> KeyKind.DART.takeIf { settings.dartKey.isBlank() }
            else -> null
        } ?: return
        if (kind.id in settings.state.value.keyGuideHidden || kind.id in keyGuideShown) return
        keyGuideShown = keyGuideShown + kind.id
        keyGuide = kind
    }
}

class MainActivity : ComponentActivity() {
    private var onPicked: ((File) -> Unit)? = null
    private var pendingSave: Pair<File, (Boolean) -> Unit>? = null

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val cb = onPicked
        onPicked = null
        if (uri == null || cb == null) return@registerForActivityResult
        runCatching {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.ifBlank { null } ?: "picked"
            val out = File(File(cacheDir, "picked").apply { mkdirs() }, name)
            contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
            out
        }.onSuccess(cb)
    }

    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val (src, done) = pendingSave ?: return@registerForActivityResult
        pendingSave = null
        if (uri == null) { done(false); return@registerForActivityResult }
        done(runCatching { contentResolver.openOutputStream(uri)!!.use { out -> src.inputStream().use { it.copyTo(out) } } }.isSuccess)
    }

    fun pickFile(mimes: Array<String>, cb: (File) -> Unit) {
        onPicked = cb
        openDoc.launch(mimes)
    }

    fun saveFileAs(src: File, name: String, done: (Boolean) -> Unit) {
        pendingSave = src to done
        createDoc.launch(name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Platform.activity = this
        // 위쪽 상태 표시줄은 남색 앱 막대와 이어지게(흰 글자)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val graph = (application as FssApp).graph
        graph.update.autoCheck()
        graph.dart.ensureIndex()
        setContent {
            val s by graph.settings.state.collectAsState()
            AppTheme(s.theme) { Shell(graph) }
        }
    }

    override fun onDestroy() {
        if (Platform.activity === this) Platform.activity = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shell(g: AppGraph) {
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var lastBackAt by remember { mutableStateOf(0L) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        for (m in g.messages) {
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(m) }
        }
    }
    // 뒤로가기(버튼·제스처): 메뉴가 열려 있으면 닫고, 화면 안 단계 → 직전 화면 순서로 되돌린다.
    // 홈에서 더 갈 곳이 없으면 바로 끄지 않고 한 번 더 누를 때 끈다.
    BackHandler {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            g.nav.back() -> Unit
            System.currentTimeMillis() - lastBackAt < 2000 -> (ctx as? ComponentActivity)?.finish()
            else -> { lastBackAt = System.currentTimeMillis(); Toast.makeText(ctx, "한 번 더 누르면 앱을 닫습니다.", Toast.LENGTH_SHORT).show() }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DrawerContent(g) { d -> g.nav.go(d); scope.launch { drawer.close() } }
        },
    ) {
        Scaffold(
            topBar = { AppBar(g) { scope.launch { drawer.open() } } },
            snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize().imePadding()) { Content(g) }
        }
    }
}

/** 남색 앱 막대: 메뉴 열기, 지금 화면 이름, (홈이 아니면) 뒤로 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(g: AppGraph, onMenu: () -> Unit) {
    val update by g.update.state.collectAsState()
    val d = g.nav.current
    TopAppBar(
        title = { Text(if (d == Dest.HOME) "FSS_Helper" else d.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, "메뉴 열기") } },
        actions = {
            if (update is UpdateState.Available || update is UpdateState.Ready) {
                TextButton(onClick = { g.nav.go(Dest.ABOUT) }) { Text("새 버전", color = Color(0xFFFFD166)) }
            }
            if (g.nav.canGoBack && d != Dest.HOME) IconButton(onClick = { g.nav.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = NavyBand, titleContentColor = Color.White,
            navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
        ),
    )
}

@Composable
private fun Content(g: AppGraph) {
    val current = g.nav.current
    LaunchedEffect(current) { g.maybeKeyGuide(current) }
    when (current) {
        Dest.HOME -> {
            val todo by g.todo.data.collectAsState()
            val lib by g.library.data.collectAsState()
            val saved by g.dart.saved.collectAsState()
            val badges = HomeBadges(buildMap {
                todo.items.count { !it.done }.takeIf { it > 0 }?.let { put(Dest.TODO, "$it") }
                lib.formNos.size.takeIf { it > 0 }?.let { put(Dest.FORMS, "${it}종") }
                saved.newCount.values.sum().takeIf { it > 0 }?.let { put(Dest.DART, "새 공시 $it") }
            })
            HomeScreen(g.recents, badges, onTool = { g.nav.go(it) }, onRecent = g::openRecent, onSearch = g::searchLaw)
        }
        Dest.MANUAL -> ManualScreen(g.library, g.libraryUi, g.libraryActions)
        Dest.FORMS -> FormsScreen(g.library, g.libraryUi, g.libraryActions)
        Dest.LAW -> LawScreen(g.lawSearch, g.settings, g.law.cache, onMessage = g::message, onKeyGuide = { g.keyGuide = KeyKind.LAW })
        Dest.DART -> DartScreen(g.dart, onOpenSettings = { g.keyGuide = KeyKind.DART }, onMessage = g::message)
        Dest.LEVEL -> LevelScreen(g.criteriaEnv)
        Dest.PENALTY -> PenaltyScreen(g.criteriaEnv)
        Dest.FINE -> FineScreen(g.criteriaEnv)
        Dest.TODO -> TodoScreen(g.todo)
        Dest.SETTINGS -> SettingsScreen(g.settings, g.law, g.dart, g.prefetch, g.scope, onMessage = g::message)
        Dest.ABOUT -> AboutScreen(g.update, onMessage = g::message)
    }
    g.keyGuide?.let { kind ->
        when (kind) {
            KeyKind.LAW -> KeyGuideDialog(kind, g.settings, g.settings.userLawOc,
                onSave = { g.settings.setLawOc(it); g.regulation.ensure(force = true); g.message("법령 OC를 저장했습니다.") }, onTest = { testLaw(g.law) }, onDismiss = { g.keyGuide = null })
            KeyKind.DART -> KeyGuideDialog(kind, g.settings, g.settings.userDartKey,
                onSave = { g.settings.setDartKey(it); g.dart.ensureIndex(force = true); g.message("DART 인증키를 저장했습니다. 회사 목록을 받아 옵니다.") }, onTest = { testDart(g.dart) }, onDismiss = { g.keyGuide = null })
        }
    }
}

/** 왼쪽 메뉴(시안 3: 남색 바탕, 흰 글자, 고른 줄은 밝은 남색 칩) */
@Composable
private fun DrawerContent(g: AppGraph, onGo: (Dest) -> Unit) {
    val todo by g.todo.data.collectAsState()
    val saved by g.dart.saved.collectAsState()
    val update by g.update.state.collectAsState()
    val openTodo = todo.items.count { !it.done }
    val newFilings = saved.newCount.values.sum()
    ModalDrawerSheet(drawerContainerColor = NavyBand, drawerContentColor = Color.White, modifier = Modifier.width(284.dp)) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.app_logo), null, Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)))
                Text("FSS_Helper", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 10.dp))
                Text("v${BuildInfo.VERSION}", style = MaterialTheme.typography.labelSmall, color = DrawerMuted, modifier = Modifier.padding(start = 8.dp))
            }
            var lastGroup: NavGroup? = null
            Dest.entries.filter { !it.lower }.forEach { d ->
                if (d.group != lastGroup && d.group.title.isNotBlank()) {
                    Text(d.group.title, style = MaterialTheme.typography.labelSmall, color = DrawerMuted, modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp))
                }
                lastGroup = d.group
                val count = when (d) {
                    Dest.TODO -> openTodo.takeIf { it > 0 }?.toString()
                    Dest.DART -> newFilings.takeIf { it > 0 }?.toString()
                    else -> null
                }
                DrawerItem(d, g.nav.current == d, count) { onGo(d) }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color.White.copy(alpha = 0.14f))
            Dest.entries.filter { it.lower }.forEach { d ->
                val badge = if (d == Dest.ABOUT && (update is UpdateState.Available || update is UpdateState.Ready)) "N" else null
                DrawerItem(d, g.nav.current == d, badge) { onGo(d) }
            }
            Spacer(Modifier.height(8.dp))
            Text("이현덕 with Claude", style = MaterialTheme.typography.labelSmall, color = DrawerMuted, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

private val DrawerMuted = Color(0xFF8C9AB5)

@Composable
private fun DrawerItem(d: Dest, selected: Boolean, count: String?, onClick: () -> Unit) {
    val bg = if (selected) Color(0xFF2F4B82) else Color.Transparent
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(6.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(if (d.group == NavGroup.HOME || d.lower) Color.White.copy(alpha = 0.12f) else d.tint), contentAlignment = Alignment.Center) {
            Icon(d.icon, null, Modifier.size(15.dp), tint = Color.White)
        }
        Text(d.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = Color.White)
        if (count != null) Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFD166)).padding(horizontal = 7.dp)) {
            Text(count, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1B2B4B), fontWeight = FontWeight.Bold)
        }
    }
}
