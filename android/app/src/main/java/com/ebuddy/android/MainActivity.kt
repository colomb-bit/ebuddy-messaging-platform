package com.ebuddy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebuddy.android.domain.*
import com.ebuddy.android.ui.*

private val Navy = Color(0xFF0C1020)
private val Blue = Color(0xFF2E7DFF)
private val Soft = Color(0xFFF4F6FB)

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val app = application as EbuddyApp
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Soft)) {
                val vm: EbuddyViewModel = viewModel(factory = EbuddyViewModelFactory(app.repository))
                EbuddyRoot(vm)
            }
        }
    }
}

@Composable
fun EbuddyRoot(vm: EbuddyViewModel) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    var showRegister by rememberSaveable { mutableStateOf(false) }
    val error = ui.error
    if (error != null) {
        LaunchedEffect(error) { kotlinx.coroutines.delay(3500); vm.clearError() }
    }
    when {
        ui.session == null && showRegister -> RegisterScreen(ui.loading, { u, p, d -> vm.register(u, p, d) }) { showRegister = false }
        ui.session == null -> LoginScreen(ui.loading, { u, p -> vm.login(u, p) }) { showRegister = true }
        ui.selected == null -> ContactsScreen(ui, vm)
        else -> ChatScreen(ui.selected!!, messages, vm)
    }
    if (error != null && ui.session != null) {
        SnackbarHost(remember { SnackbarHostState() }, modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun LoginScreen(loading: Boolean, onLogin: (String, String) -> Unit, onRegister: () -> Unit) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    AuthCard("Welcome back", "Sign in to continue", user, { user = it }, pass, { pass = it }, loading, "Sign in") {
        onLogin(user.trim(), pass)
    }
    TextButton(onClick = onRegister, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) { Text("Create a new account") }
}

@Composable
fun RegisterScreen(loading: Boolean, onRegister: (String, String, String) -> Unit, onBack: () -> Unit) {
    var display by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Navy).padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Create account", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Blue)
                OutlinedTextField(display, { display = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("Password (8+ characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button(onClick = { onRegister(user, pass, display) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Creating…" else "Sign up") }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
            }
        }
    }
}

@Composable
private fun AuthCard(title: String, subtitle: String, user: String, onUser: (String) -> Unit, pass: String, onPass: (String) -> Unit, loading: Boolean, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Navy).padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("eBuddy", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Blue)
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, color = Color.Gray)
                OutlinedTextField(user, onUser, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, onPass, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button(onClick = onAction, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Connecting…" else action) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(ui: UiState, vm: EbuddyViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text("Contacts") }, actions = { TextButton(onClick = vm::logout) { Text("Exit") } }) }, floatingActionButton = { FloatingActionButton(onClick = vm::loadContacts) { Text("↻") } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text("Your eBuddy circle", Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
            LazyColumn { items(ui.contacts) { c -> ContactRow(c) { vm.select(c) } }; if (ui.contacts.isEmpty()) item { Text("No contacts yet", Modifier.padding(20.dp), color = Color.Gray) } }
        }
    }
}

@Composable
fun ContactRow(c: Contact, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(Blue, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { Text(c.displayName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
        Column(Modifier.padding(start = 14.dp).weight(1f)) { Text(c.displayName, fontWeight = FontWeight.SemiBold); Text("@${c.username}", color = Color.Gray) }
        Text(if (c.state == "online") "●" else "○", color = if (c.state == "online") Color(0xFF2DBE72) else Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(c: Contact, messages: List<Message>, vm: EbuddyViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Column { Text(c.displayName); Text(if (c.state == "online") "online" else "offline", style = MaterialTheme.typography.labelSmall, color = Color.Gray) } }, navigationIcon = { IconButton(onClick = { vm.select(null) }) { Text("‹") } }) }, bottomBar = { Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 4); Spacer(Modifier.width(8.dp)); Button(onClick = { vm.send(text); text = "" }, enabled = text.isNotBlank()) { Text("Send") } } }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(messages) { MessageBubble(it) } }
    }
}

@Composable
fun MessageBubble(m: Message) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.status in setOf("sent", "delivered", "read")) Arrangement.End else Arrangement.Start) {
        Surface(color = if (m.status == "read") Color(0xFFDCEAFF) else Color.White, shape = RoundedCornerShape(16.dp)) { Text(m.body, Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
    }
}
