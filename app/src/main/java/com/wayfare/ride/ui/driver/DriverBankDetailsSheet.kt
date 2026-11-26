package com.wayfare.ride.ui.driver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.DriverBankDetails
import com.wayfare.ride.ui.theme.LocalWayfareColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DriverBankDetailsSheet(
    initial: DriverBankDetails?,
    defaultAccountHolder: String,
    onSave: (bankName: String, holder: String, accountNumber: String, branchCode: String, accountType: String) -> Boolean,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    var bankName by remember(initial) { mutableStateOf(initial?.bankName.orEmpty()) }
    var accountHolder by remember(initial, defaultAccountHolder) {
        mutableStateOf(initial?.accountHolder ?: defaultAccountHolder)
    }
    var accountNumber by remember(initial) { mutableStateOf(initial?.accountNumber.orEmpty()) }
    var branchCode by remember(initial) { mutableStateOf(initial?.branchCode.orEmpty()) }
    var accountType by remember(initial) { mutableStateOf(initial?.accountType ?: "Cheque") }

    BackHandler { onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = colorScheme.primary)
                    Text(
                        text = "EFT payout bank details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close bank details")
                }
            }

            Text(
                text = "Used to settle weekly digital-fare payouts (card & wallet trips) to your South African bank account.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )

            Text(
                text = "Quick select bank",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DriverBankDetails.SOUTH_AFRICAN_BANKS.forEach { (bank, defaultBranch) ->
                    val selected = bankName.equals(bank, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            bankName = bank
                            branchCode = defaultBranch
                        },
                        label = { Text(bank) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = wayfareColors.brandHighlight,
                            selectedLabelColor = colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("bank_chip_${bank.lowercase().replace(" ", "_")}")
                    )
                }
            }

            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it },
                label = { Text("Bank name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("driver_bank_name_input")
            )

            OutlinedTextField(
                value = accountHolder,
                onValueChange = { accountHolder = it },
                label = { Text("Account holder full name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("driver_bank_holder_input")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { s -> accountNumber = s.filter { it.isDigit() }.take(16) },
                    label = { Text("Account number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.3f).testTag("driver_bank_account_input")
                )
                OutlinedTextField(
                    value = branchCode,
                    onValueChange = { s -> branchCode = s.filter { it.isDigit() }.take(8) },
                    label = { Text("Branch code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("driver_bank_branch_input")
                )
            }

            Text(
                text = "Account type",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("Cheque", "Savings", "Transmission").forEach { type ->
                    FilterChip(
                        selected = accountType == type,
                        onClick = { accountType = type },
                        label = { Text(type) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = wayfareColors.brandHighlight,
                            selectedLabelColor = colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f).testTag("bank_type_${type.lowercase()}")
                    )
                }
            }

            Button(
                onClick = {
                    val saved = onSave(bankName, accountHolder, accountNumber, branchCode, accountType)
                    if (saved) onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("driver_save_bank_btn")
            ) {
                Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save EFT payout details", fontWeight = FontWeight.Bold)
            }
        }
    }
}
