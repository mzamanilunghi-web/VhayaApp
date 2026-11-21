package com.wayfare.ride.ui.auth

import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.wayfare.ride.BuildConfig
import com.wayfare.ride.R
import com.wayfare.ride.data.friendlyApiErrorMessage
import com.wayfare.ride.domain.AuthState
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.findActivity
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import kotlinx.coroutines.launch

private enum class AuthFormMode {
    SIGN_IN,
    CREATE_ACCOUNT,
    RESET_PASSWORD
}

@Composable
fun VhayaAuthScreen(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    val pending = uiState.authState as? AuthState.EmailVerificationPending
    if (pending != null) {
        EmailVerificationScreen(
            session = pending.session,
            busy = uiState.authBusy,
            resendCooldownSeconds = uiState.verificationResendCooldownSeconds,
            notice = uiState.authNotice,
            error = uiState.authError,
            themeMode = uiState.themeMode,
            onCycleTheme = { viewModel.setThemeMode(uiState.themeMode.next()) },
            onCheckVerified = { viewModel.checkEmailVerified() },
            onResend = { viewModel.resendVerificationEmail() },
            onSignOut = { viewModel.signOut() },
            modifier = modifier
        )
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AuthFormMode.SIGN_IN) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    var googleSignInBusy by remember { mutableStateOf(false) }
    var showPrivacySummaryDialog by remember { mutableStateOf(false) }

    val passwordEvaluation = remember(password) { LaunchPolicy.evaluatePasswordPolicy(password) }
    val passwordsMatch = confirmPassword.isNotEmpty() && password == confirmPassword
    val anyAuthBusy = uiState.authBusy || googleSignInBusy

    fun switchMode(next: AuthFormMode) {
        mode = next
        passwordVisible = false
        confirmPasswordVisible = false
        passwordFocused = false
        viewModel.clearAuthMessages()
    }

    fun launchGoogleSignIn() {
        focusManager.clearFocus()
        viewModel.clearAuthMessages()
        val activity = context.findActivity()
        if (activity == null) {
            viewModel.showAuthInlineError("Unable to open Google Sign-In from this window.")
            return
        }
        val credentialContext = ContextWrapper(activity)
        googleSignInBusy = true
        coroutineScope.launch {
            val serverClientId = viewModel.resolveGoogleWebClientId().getOrElse { error ->
                googleSignInBusy = false
                viewModel.showAuthInlineError(
                    friendlyApiErrorMessage(
                        error,
                        "Google Sign-In isn't available right now. Use email below."
                    )
                )
                return@launch
            }
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            try {
                val response = CredentialManager.create(credentialContext)
                    .getCredential(credentialContext, request)
                val googleCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
                googleSignInBusy = false
                viewModel.signInWithGoogle(googleCredential.idToken)
            } catch (_: GetCredentialCancellationException) {
                googleSignInBusy = false
            } catch (_: NoCredentialException) {
                googleSignInBusy = false
                viewModel.showAuthInlineError(
                    "No Google account is available on this device. Add one in Settings or use email below."
                )
            } catch (_: GoogleIdTokenParsingException) {
                googleSignInBusy = false
                viewModel.showAuthInlineError("Could not read Google sign-in token. Please try again.")
            } catch (e: GetCredentialException) {
                googleSignInBusy = false
                viewModel.showAuthInlineError(
                    e.errorMessage?.toString()?.takeIf { it.isNotBlank() }
                        ?: "Google Sign-In couldn't complete. Please try again or use email."
                )
            }
        }
    }

    fun submitCurrentForm() {
        focusManager.clearFocus()
        when (mode) {
            AuthFormMode.SIGN_IN -> viewModel.signIn(email, password)
            AuthFormMode.CREATE_ACCOUNT -> {
                if (confirmPassword != password) {
                    viewModel.showAuthInlineError("Passwords do not match. Re-enter your confirmation password.")
                    return
                }
                viewModel.signUp(name, email, password)
            }
            AuthFormMode.RESET_PASSWORD -> viewModel.sendPasswordReset(email)
        }
    }

    if (mode != AuthFormMode.SIGN_IN) {
        BackHandler { switchMode(AuthFormMode.SIGN_IN) }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colorScheme.surface,
        unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.25f),
        unfocusedBorderColor = colorScheme.outline,
        focusedBorderColor = colorScheme.primary
    )
    val fieldShape = RoundedCornerShape(16.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        val viewportHeight = maxHeight

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
            ) {
                // Branded Hero Artwork Card
                val isDarkHero = wayfareColors.isDark
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surface,
                    border = if (isDarkHero) null else BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 176.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Crossfade(
                            targetState = isDarkHero,
                            animationSpec = tween(durationMillis = 260),
                            modifier = Modifier.matchParentSize(),
                            label = "authHeroArtwork"
                        ) { dark ->
                            Image(
                                painter = painterResource(
                                    id = if (dark) R.drawable.img_vhaya_auth_hero else R.drawable.img_vhaya_auth_hero_light
                                ),
                                contentDescription = "Vhaya city mobility route artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (isDarkHero) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                wayfareColors.walletCardEnd.copy(alpha = 0.50f),
                                                wayfareColors.walletCardEnd.copy(alpha = 0.86f)
                                            )
                                        )
                                    )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to colorScheme.surface.copy(alpha = 0.56f),
                                                0.38f to colorScheme.surface.copy(alpha = 0.16f),
                                                0.72f to colorScheme.surface.copy(alpha = 0.62f),
                                                1.0f to colorScheme.surface.copy(alpha = 0.90f)
                                            )
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colorStops = arrayOf(
                                                0.0f to colorScheme.surface.copy(alpha = 0.52f),
                                                0.68f to Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 176.dp)
                                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isDarkHero) {
                                            Color.White.copy(alpha = 0.14f)
                                        } else {
                                            colorScheme.surface.copy(alpha = 0.92f)
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (isDarkHero) Color.White.copy(alpha = 0.28f) else colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_vhaya_launcher),
                                            contentDescription = "Vhaya logo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(14.dp))
                                        )
                                    }
                                    Text(
                                        text = "Vhaya",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkHero) Color.White else colorScheme.onSurface
                                    )
                                }

                                AuthThemeCyclePill(
                                    themeMode = uiState.themeMode,
                                    onCycleTheme = { viewModel.setThemeMode(uiState.themeMode.next()) }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusChip(
                                    label = "Offer your fare · Pay by card, wallet, or cash",
                                    containerColor = if (isDarkHero) {
                                        wayfareColors.positive.copy(alpha = 0.24f)
                                    } else {
                                        wayfareColors.emeraldContainer.copy(alpha = 0.94f)
                                    },
                                    contentColor = if (isDarkHero) Color.White else wayfareColors.positive
                                )
                                Text(
                                    text = "Rides moved your way.",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkHero) Color.White else colorScheme.onSurface
                                )
                                Text(
                                    text = "Upfront offers, verified drivers, and live tracking.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDarkHero) {
                                        Color.White.copy(alpha = 0.86f)
                                    } else {
                                        colorScheme.onSurface.copy(alpha = 0.78f)
                                    }
                                )
                            }
                        }
                    }
                }

                // Floating Auth Card overlapping hero slightly for editorial depth
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-14).dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                        if (mode != AuthFormMode.RESET_PASSWORD) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    AuthTabPill(
                                        label = "Sign in",
                                        selected = mode == AuthFormMode.SIGN_IN,
                                        onClick = { switchMode(AuthFormMode.SIGN_IN) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("auth_tab_sign_in")
                                    )
                                    AuthTabPill(
                                        label = "Create account",
                                        selected = mode == AuthFormMode.CREATE_ACCOUNT,
                                        onClick = { switchMode(AuthFormMode.CREATE_ACCOUNT) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("auth_tab_create_account")
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        Text(
                            text = when (mode) {
                                AuthFormMode.SIGN_IN -> "Welcome back"
                                AuthFormMode.CREATE_ACCOUNT -> "Set up your account"
                                AuthFormMode.RESET_PASSWORD -> "Reset your password"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = when (mode) {
                                AuthFormMode.SIGN_IN -> "Sign in with your Vhaya email and password."
                                AuthFormMode.CREATE_ACCOUNT -> "Create a verified rider or driver profile."
                                AuthFormMode.RESET_PASSWORD -> "Enter your account email and we'll send reset instructions."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(14.dp))

                        if (mode != AuthFormMode.RESET_PASSWORD) {
                            OutlinedButton(
                                onClick = ::launchGoogleSignIn,
                                enabled = !anyAuthBusy,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, colorScheme.outline),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .tactilePressScale()
                                    .testTag("auth_google_sign_in_btn")
                            ) {
                                if (googleSignInBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = colorScheme.primary
                                    )
                                } else {
                                    GoogleGLogo(modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Continue with Google",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp)
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = colorScheme.outlineVariant
                                )
                                Text(
                                    text = "or use email",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = colorScheme.outlineVariant
                                )
                            }
                        } else {
                            Spacer(Modifier.height(4.dp))
                        }

                        AnimatedContent(
                            targetState = mode,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "authFormFields"
                        ) { currentMode ->
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (currentMode == AuthFormMode.CREATE_ACCOUNT) {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = {
                                            name = it
                                            if (uiState.authError != null) viewModel.clearAuthMessages()
                                        },
                                        label = { Text("Full name") },
                                        placeholder = { Text("Your full name") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Person, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Words,
                                            imeAction = ImeAction.Next
                                        ),
                                        colors = fieldColors,
                                        shape = fieldShape,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("auth_name_input")
                                    )
                                }

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = {
                                        email = it
                                        if (uiState.authError != null) viewModel.clearAuthMessages()
                                    },
                                    label = { Text("Email address") },
                                    placeholder = { Text("you@example.com") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Email, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = if (currentMode == AuthFormMode.RESET_PASSWORD) ImeAction.Done else ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (currentMode == AuthFormMode.RESET_PASSWORD && !uiState.authBusy) {
                                                submitCurrentForm()
                                            }
                                        }
                                    ),
                                    colors = fieldColors,
                                    shape = fieldShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_email_input")
                                )

                                if (currentMode != AuthFormMode.RESET_PASSWORD) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = {
                                                password = it
                                                if (uiState.authError != null) viewModel.clearAuthMessages()
                                            },
                                            label = { Text("Password") },
                                            placeholder = {
                                                Text(
                                                    if (currentMode == AuthFormMode.SIGN_IN) "Enter your password"
                                                    else "Create a strong password"
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Lock, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                            },
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = { passwordVisible = !passwordVisible },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .testTag("auth_toggle_password_visibility")
                                                ) {
                                                    Icon(
                                                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                                    )
                                                }
                                            },
                                            singleLine = true,
                                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Password,
                                                imeAction = if (currentMode == AuthFormMode.CREATE_ACCOUNT) ImeAction.Next else ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    if (currentMode == AuthFormMode.SIGN_IN && !uiState.authBusy) {
                                                        submitCurrentForm()
                                                    }
                                                }
                                            ),
                                            colors = fieldColors,
                                            shape = fieldShape,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { passwordFocused = it.isFocused }
                                                .testTag("auth_password_input")
                                        )

                                        if (currentMode == AuthFormMode.CREATE_ACCOUNT) {
                                            AnimatedVisibility(
                                                visible = passwordFocused || password.isNotEmpty()
                                            ) {
                                                PasswordPolicyChecklistCard(
                                                    evaluation = passwordEvaluation,
                                                    passwordEmpty = password.isEmpty()
                                                )
                                            }

                                            OutlinedTextField(
                                                value = confirmPassword,
                                                onValueChange = {
                                                    confirmPassword = it
                                                    if (uiState.authError != null) viewModel.clearAuthMessages()
                                                },
                                                label = { Text("Confirm password") },
                                                placeholder = { Text("Re-enter password") },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                                },
                                                trailingIcon = {
                                                    IconButton(
                                                        onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                                                        modifier = Modifier.size(48.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                            contentDescription = if (confirmPasswordVisible) "Hide confirm password" else "Show confirm password"
                                                        )
                                                    }
                                                },
                                                isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                                                supportingText = if (confirmPassword.isNotEmpty()) {
                                                    {
                                                        Text(
                                                            text = if (passwordsMatch) "Passwords match" else "Passwords do not match yet",
                                                            color = if (passwordsMatch) wayfareColors.positive else colorScheme.error
                                                        )
                                                    }
                                                } else null,
                                                singleLine = true,
                                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Password,
                                                    imeAction = ImeAction.Done
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onDone = { if (!uiState.authBusy) submitCurrentForm() }
                                                ),
                                                colors = fieldColors,
                                                shape = fieldShape,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("auth_confirm_password_input")
                                            )
                                        }

                                        if (currentMode == AuthFormMode.SIGN_IN) {
                                            Row(
                                                horizontalArrangement = Arrangement.End,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                TextButton(
                                                    onClick = { switchMode(AuthFormMode.RESET_PASSWORD) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("auth_forgot_password_btn")
                                                ) {
                                                    Text(
                                                        text = "Forgot password?",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = uiState.authError != null) {
                            uiState.authError?.let { err ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colorScheme.errorContainer,
                                    border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ErrorOutline,
                                            contentDescription = null,
                                            tint = colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = err,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onErrorContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = uiState.authNotice != null) {
                            uiState.authNotice?.let { msg ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = wayfareColors.emeraldContainer,
                                    border = BorderStroke(1.dp, wayfareColors.positive.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = wayfareColors.positive,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        val createReady = mode != AuthFormMode.CREATE_ACCOUNT ||
                            (name.isNotBlank() && email.contains("@") && passwordEvaluation.isValid && passwordsMatch)

                        Button(
                            onClick = ::submitCurrentForm,
                            enabled = !anyAuthBusy,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (mode == AuthFormMode.CREATE_ACCOUNT && createReady) {
                                    wayfareColors.positive
                                } else {
                                    colorScheme.primary
                                },
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .tactilePressScale()
                                .testTag("auth_submit_btn")
                        ) {
                            if (uiState.authBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = when (mode) {
                                        AuthFormMode.SIGN_IN -> "Sign in"
                                        AuthFormMode.CREATE_ACCOUNT -> "Create account"
                                        AuthFormMode.RESET_PASSWORD -> "Send reset email"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (mode == AuthFormMode.CREATE_ACCOUNT) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "We'll send a verification link to confirm your email.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (mode == AuthFormMode.RESET_PASSWORD) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(
                                    onClick = { switchMode(AuthFormMode.SIGN_IN) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("auth_mode_sign_in")
                                ) {
                                    Text(
                                        text = "Back to sign in",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    val hostedUrl = LaunchPolicy.resolvePrivacyPolicyUrl(
                                        uiState.apiBaseUrl,
                                        isDebugBuild = BuildConfig.DEBUG
                                    )
                                    if (hostedUrl != null) {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hostedUrl)))
                                        }.onFailure { showPrivacySummaryDialog = true }
                                    } else {
                                        showPrivacySummaryDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("auth_privacy_policy_btn")
                            ) {
                                Text(
                                    text = "Privacy & POPIA Data Policy",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPrivacySummaryDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacySummaryDialog = false },
            title = { Text("Privacy & POPIA Notice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("By creating a Vhaya account, your name and email authenticate your rider or driver profile over HTTPS.")
                    Text("Foreground GPS is requested only after sign-in for route pickup matching and active trip safety.")
                    Text("You can export your data (JSON) or permanently delete your account anytime in Account → Privacy & POPIA.")
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacySummaryDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
private fun AuthTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GoogleGLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.2f
        val inset = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -35f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 210f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        val midY = size.height / 2f
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.52f, midY),
            end = Offset(size.width - inset, midY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
    }
}
