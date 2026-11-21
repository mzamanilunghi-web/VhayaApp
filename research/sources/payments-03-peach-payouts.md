Source: https://developer.peachpayments.com/docs/peach-payouts
Title: Payouts
Fetched: 2026-10-18T15:41:50.833Z

For AI agents: the docs index is at https://developer.peachpayments.com/llms.txt. Append .md to any documentation page URL to get its markdown version.

# Overview   [Skip link to Overview](https://developer.peachpayments.com/docs/peach-payouts\#overview)

The Payouts API enables you to:

- Execute instant payouts at any time of the day: Unlike traditional payout methods that can take hours or days, the API processes transactions in real-time, offering immediate access to funds for recipients.
- Improve operational efficiency: By automating the payout process, the Payouts API minimises manual intervention, reducing operational overhead and the potential for errors.
- Track payouts in real-time: You can track the status of your payouts in real-time, enhancing transparency and accountability.
- Integrate seamlessly: A standardised, developer-friendly API reduces the complexity and time required for integration.
- Scale effortlessly: The API can handle high volumes of transactions, making it suitable for your growing payout needs.
- Verify bank accounts before executing payouts: Use [bank account verification (BANV)](https://developer.peachpayments.com/docs/payouts-api-1#create-bank-account-verification-request-flow) between 03:00 and 23:00 to confirm recipient details before sending funds, reducing the risk of paying the wrong account. BANV supports the following banks:

  - Absa
  - African Bank
  - African Bank Business
  - Bidvest Bank Alliances
  - Capitec Bank
  - Discovery Bank
  - FNB (First National Bank)
  - Grindrod Bank (African Bank)
  - Investec Bank Limited
  - Nedbank
  - Rand Merchant Bank (RMB) - Use the FNB details and universal branch code to verify RMB accounts
  - Standard Bank
  - TymeBank

> 🚧
>
> See the [known limitations](https://developer.peachpayments.com/docs/peach-payouts#known-limitations) section for details on real-time clearing (RTC) processing limits.

> ❗
>
> You cannot reverse payouts, so Peach Payments strongly recommends executing a [bank account verification (BANV)](https://developer.peachpayments.com/docs/payouts-api-1#create-bank-account-verification-request-flow) on the first payout to an account, especially for high risk or high value payouts. This ensures that you don't pay the wrong bank account.

# Supported banks   [Skip link to Supported banks](https://developer.peachpayments.com/docs/peach-payouts\#supported-banks)

Peach Payments supports payouts to the following banks:

> 🚧
>
> Always use the **universal branch code** from the table below when creating a payout, regardless of the customer's specific branch.

| Bank | Universal branch code | Notes |
| --- | --- | --- |
| Absa | 632005 |  |
| Access Bank | 410506 |  |
| African Bank | 430000 |  |
| African Bank Business | 584000 |  |
| Albaraka Bank | 800000 |  |
| Bank Zero - Mukuru | 435000 |  |
| Bank Zero Mutual Bank | 888000 |  |
| Bidvest Bank | 462005 |  |
| Bidvest Bank Alliances | 683000 |  |
| Capitec Bank (excluding Live Better accounts) | 470010 |  |
| Capitec Business | 450105 |  |
| Discovery Bank | 679000 |  |
| Finbond EPE | 589000 |  |
| Finbond Mutual Bank | 589000 |  |
| FNB (First National Bank) | 250655 |  |
| HBZ Bank Limited | 570105 |  |
| Investec Bank Limited | 580105 |  |
| Nedbank | 198765 |  |
| Mercantile Bank (see notes) | 450105 | Use the Capitec Business details and universal branch code to pay out to Mercantile Bank accounts. |
| Mukuru (see notes) | 410506 | Use the Access Bank details and universal branch code to pay out to Mukuru accounts. |
| Old Mutual Bank | 462105 |  |
| Rand Merchant Bank (RMB) (see notes) | 250655 | Use the FNB details and universal branch code to pay out to RMB accounts. |
| Sasfin Bank (see notes) | 683000 | Use the Bidvest Bank Alliances details and universal branch code to pay out to Sasfin Bank accounts. |
| Standard Bank | 051001 |  |
| Standard Chartered Bank SA | 730020 |  |
| TymeBank | 678910 |  |
| Ubank Ltd | 431010 |  |
| YWBN Mutual Bank | 353000 |  |

# Process overview   [Skip link to Process overview](https://developer.peachpayments.com/docs/peach-payouts\#process-overview)

> 📘
>
> Before executing any payouts, you should [send Peach Payments](https://support.peachpayments.com/support/tickets/new?ticket_form=log_a_support_ticket) a source of funds document for compliance reasons.

Using the Payouts API, South African businesses can make instant EFT payments to suppliers, employees, partners, or customers.

> 🚧
>
> - Once a payout is successfully submitted, you cannot recall it.
> - Refunds are not supported.
> - Failed payouts incur fees.

1. To execute instant payouts, you must have [enough funds](https://developer.peachpayments.com/docs/payouts-dashboard#view-available-balance) in your Peach Payments account to cover the payout request. Deposit funds into the Peach Payments account using your [unique reference](https://developer.peachpayments.com/docs/payouts-dashboard#view-deposit-reference), keeping in mind the [known limitations](https://developer.peachpayments.com/docs/peach-payouts#known-limitations).



> 📘
>
> Peach Payments processes deposits every two hours, with the processing covering the first two of the last three hours:
>
> | Deposits included from | Deposits included to | Processing time |
> | --- | --- | --- |
> | 00:00 | 02:00 | 03:00 |
> | 02:00 | 04:00 | 05:00 |
> | 04:00 | 06:00 | 07:00 |
> | 06:00 | 08:00 | 09:00 |
> | 08:00 | 10:00 | 11:00 |
> | 10:00 | 12:00 | 13:00 |
> | 12:00 | 14:00 | 15:00 |
> | 14:00 | 16:00 | 17:00 |
> | 16:00 | 18:00 | 19:00 |
> | 18:00 | 20:00 | 21:00 |
> | 20:00 | 22:00 | 23:00 |
> | 22:00 | 00:00 | 01:00 |
>
> If you deposit funds using:
>
>    - An RTC payment, your deposit could reflect in your account anywhere between one hour and one minute and three hours later. To ensure the shortest time between deposit and the funds reflecting in your account, deposit your funds before the times in the **Deposits included to** column. See the [known limitations](https://developer.peachpayments.com/docs/peach-payouts#known-limitations) section for details on RTC processing limits.
>    - A normal EFT, your deposit might take up to two business days to reflect in your account, depending on cut-off times.





> 🚧
>
>    - You cannot use PayShap to deposit funds into the Peach Payments account. Use RTC (recommended) or EFT.
>    - You cannot deposit funds into the Peach Payments Payouts account from a TymeBank Everyday account.

2. [Verify the bank accounts that you are going to pay](https://developer.peachpayments.com/docs/payouts-api-1#create-bank-account-verification-request-flow), keeping in mind the [known limitations](https://developer.peachpayments.com/docs/peach-payouts#known-limitations).
3. [Create the payout request](https://developer.peachpayments.com/reference/createpayoutrequest).
4. Check the reasons for any failed payouts, fix their details, and try again.

> 📘
>
> The value of any failed payouts returns to your float balance for you to use on future payouts.

## Duplicate checking   [Skip link to Duplicate checking](https://developer.peachpayments.com/docs/peach-payouts\#duplicate-checking)

Peach Payments recommends that all [create payout requests](https://developer.peachpayments.com/reference/createpayoutrequest) include a unique `payoutId`. While a unique `payoutId` is not yet mandatory, it will become mandatory soon and the API will reject any duplicate requests that arrive with the same `payoutId`.

# Known limitations   [Skip link to Known limitations](https://developer.peachpayments.com/docs/peach-payouts\#known-limitations)

- The minimum payout amount is R10.
- In South Africa, RTC operates with specific "dark hours" or processing windows that limit the value of transactions that you can process during those times:
  - Business days (Monday to Friday): Day time (00h00:01 - 16h00), you can process RTC transactions with a limit of R5,000,000.
  - Overnight (16h00:01 - 00h00) and during weekends or public holidays: The limit is R250,000.
- You cannot pay out to:
  - Capitec Live Better accounts.
  - TymeBank SME account numbers that begin with `53`.
- You cannot deposit funds into the Peach Payments Payouts account:
  - Using PayShap. Use RTC (recommended) or EFT instead.
  - From a TymeBank Everyday account.
- Bank account verification operates between 03:00 and 23:00.
- Sandbox testing might fail intermittently outside of South African office hours.

# Quick links   [Skip link to Quick links](https://developer.peachpayments.com/docs/peach-payouts\#quick-links)

[API playground\\
\\
Detailed reference to the Payouts API endpoints and sample calls.](https://developer.peachpayments.com/reference/createpayoutrequest)

[Postman collection\\
\\
Use the sample Payouts API calls in Postman. See the collection overview for more information.](https://god.gw.postman.com/run-collection/13324425-25bed04a-496f-4de1-9e73-d154fb091b3f?action=collection%2Ffork&source=rip_markdown&collection-url=entityId%3D13324425-25bed04a-496f-4de1-9e73-d154fb091b3f%26entityType%3Dcollection%26workspaceId%3D395353be-fe74-499e-9ef1-fc3433748710#?env%5BPayouts-Public%5D=W3sia2V5IjoiYmFzZVVybCIsInZhbHVlIjoiaHR0cHM6Ly9zYW5kYm94LXBheW91dHMucGVhY2hwYXltZW50cy5jb20iLCJlbmFibGVkIjp0cnVlLCJ0eXBlIjoiZGVmYXVsdCIsInNlc3Npb25WYWx1ZSI6Imh0dHBzOi8vc2FuZGJveC1wYXlvdXRzLnBlYWNocGF5bWVudHMuY29tIiwiY29tcGxldGVTZXNzaW9uVmFsdWUiOiJodHRwczovL3NhbmRib3gtcGF5b3V0cy5wZWFjaHBheW1lbnRzLmNvbSIsInNlc3Npb25JbmRleCI6MH0seyJrZXkiOiJhdXRoVXJsIiwidmFsdWUiOiJodHRwczovL3NhbmRib3gtZGFzaGJvYXJkLnBlYWNocGF5bWVudHMuY29tIiwiZW5hYmxlZCI6dHJ1ZSwidHlwZSI6ImRlZmF1bHQiLCJzZXNzaW9uVmFsdWUiOiJodHRwczovL3NhbmRib3gtZGFzaGJvYXJkLnBlYWNocGF5bWVudHMuY29tIiwiY29tcGxldGVTZXNzaW9uVmFsdWUiOiJodHRwczovL3NhbmRib3gtZGFzaGJvYXJkLnBlYWNocGF5bWVudHMuY29tIiwic2Vzc2lvbkluZGV4IjoxfSx7ImtleSI6Im1lcmNoYW50SWQiLCJ2YWx1ZSI6IiIsImVuYWJsZWQiOnRydWUsInNlc3Npb25WYWx1ZSI6IiIsImNvbXBsZXRlU2Vzc2lvblZhbHVlIjoiIiwic2Vzc2lvbkluZGV4IjoyfSx7ImtleSI6ImNsaWVudElkIiwidmFsdWUiOiIiLCJlbmFibGVkIjp0cnVlLCJ0eXBlIjoic2VjcmV0Iiwic2Vzc2lvblZhbHVlIjoiIiwiY29tcGxldGVTZXNzaW9uVmFsdWUiOiIiLCJzZXNzaW9uSW5kZXgiOjN9LHsia2V5IjoiY2xpZW50U2VjcmV0IiwidmFsdWUiOiIiLCJlbmFibGVkIjp0cnVlLCJ0eXBlIjoic2VjcmV0Iiwic2Vzc2lvblZhbHVlIjoiIiwiY29tcGxldGVTZXNzaW9uVmFsdWUiOiIiLCJzZXNzaW9uSW5kZXgiOjR9LHsia2V5IjoiYWNjZXNzVG9rZW4iLCJ2YWx1ZSI6IiIsImVuYWJsZWQiOnRydWUsInR5cGUiOiJzZWNyZXQiLCJzZXNzaW9uVmFsdWUiOiIiLCJjb21wbGV0ZVNlc3Npb25WYWx1ZSI6IiIsInNlc3Npb25JbmRleCI6NX1d) [![Run in Postman](https://run.pstmn.io/button.svg)](https://god.gw.postman.com/run-collection/13324425-25bed04a-496f-4de1-9e73-d154fb091b3f?action=collection%2Ffork&source=rip_markdown&collection-url=entityId%3D13324425-25bed04a-496f-4de1-9e73-d154fb091b3f%26entityType%3Dcollection%26workspaceId%3D395353be-fe74-499e-9ef1-fc3433748710#?env%5BPayouts-Public%5D=W3sia2V5IjoiYmFzZVVybCIsInZhbHVlIjoiaHR0cHM6Ly9zYW5kYm94LXBheW91dHMucGVhY2hwYXltZW50cy5jb20iLCJlbmFibGVkIjp0cnVlLCJ0eXBlIjoiZGVmYXVsdCIsInNlc3Npb25WYWx1ZSI6Imh0dHBzOi8vc2FuZGJveC1wYXlvdXRzLnBlYWNocGF5bWVudHMuY29tIiwiY29tcGxldGVTZXNzaW9uVmFsdWUiOiJodHRwczovL3NhbmRib3gtcGF5b3V0cy5wZWFjaHBheW1lbnRzLmNvbSIsInNlc3Npb25JbmRleCI6MH0seyJrZXkiOiJhdXRoVXJsIiwidmFsdWUiOiJodHRwczovL3NhbmRib3gtZGFzaGJvYXJkLnBlYWNocGF5bWVudHMuY29tIiwiZW5hYmxlZCI6dHJ1ZSwidHlwZSI6ImRlZmF1bHQiLCJzZXNzaW9uVmFsdWUiOiJodHRwczovL3NhbmRib3gtZGFzaGJvYXJkLnBlYWNocGF5bWVudHMuY29tIiwiY29tcGxldGVTZXNzaW9uVmFsdWUiOiJodHRwczovL3NhbmRib3gtZGFzaGJvYXJkLnBlYWNocGF5bWVudHMuY29tIiwic2Vzc2lvbkluZGV4IjoxfSx7ImtleSI6Im1lcmNoYW50SWQiLCJ2YWx1ZSI6IiIsImVuYWJsZWQiOnRydWUsInNlc3Npb25WYWx1ZSI6IiIsImNvbXBsZXRlU2Vzc2lvblZhbHVlIjoiIiwic2Vzc2lvbkluZGV4IjoyfSx7ImtleSI6ImNsaWVudElkIiwidmFsdWUiOiIiLCJlbmFibGVkIjp0cnVlLCJ0eXBlIjoic2VjcmV0Iiwic2Vzc2lvblZhbHVlIjoiIiwiY29tcGxldGVTZXNzaW9uVmFsdWUiOiIiLCJzZXNzaW9uSW5kZXgiOjN9LHsia2V5IjoiY2xpZW50U2VjcmV0IiwidmFsdWUiOiIiLCJlbmFibGVkIjp0cnVlLCJ0eXBlIjoic2VjcmV0Iiwic2Vzc2lvblZhbHVlIjoiIiwiY29tcGxldGVTZXNzaW9uVmFsdWUiOiIiLCJzZXNzaW9uSW5kZXgiOjR9LHsia2V5IjoiYWNjZXNzVG9rZW4iLCJ2YWx1ZSI6IiIsImVuYWJsZWQiOnRydWUsInR5cGUiOiJzZWNyZXQiLCJzZXNzaW9uVmFsdWUiOiIiLCJjb21wbGV0ZVNlc3Npb25WYWx1ZSI6IiIsInNlc3Npb25JbmRleCI6NX1d)

Updatedabout 1 month ago

* * *

Did this page help you?

Yes

No

Copy Page