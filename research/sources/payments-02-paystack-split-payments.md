Source: https://paystack.com/docs/payments/split-payments
Title: Split Payments | Paystack Developer Documentation
Fetched: 2026-10-18T15:41:47.412Z

Implementing split payments involves:

1. [Create a subaccount](https://paystack.com/docs/payments/split-payments/#create-a-subaccount)
2. [Initialize a split payment](https://paystack.com/docs/payments/split-payments/#initialize-a-split-payment)

## [create a subaccount permalink](https://paystack.com/docs/payments/split-payments/\#create-a-subaccount) Create a subaccount

Subaccounts can be created via the [Paystack Dashboard](https://dashboard.paystack.com/#/subaccounts) or using the [create subaccountAPI](https://paystack.com/docs/api/subaccount#create) endpoint. When a subaccount is created, the `subaccount_code` is returned.

cURLNodePHP

Show Response

```
1curl https://api.paystack.co/subaccount
2-H "Authorization: Bearer YOUR_SECRET_KEY"
3-H "Content-Type: application/json"
4-d '{ "business_name": "Oasis",
5      "bank_code": "058",
6      "account_number": "0123456047",
7      "percentage_charge": 30
8    }'
9-X POST
```

```
1{
2  "status": true,
3  "message": "Subaccount created",
4  "data": {
5    "business_name": "Oasis",
6    "account_number": "0123456047",
7    "percentage_charge": 30,
8    "settlement_bank": "Guaranty Trust Bank",
9    "currency": "NGN",
10    "bank": 9,
11    "integration": 463433,
12    "domain": "test",
13    "account_name": "LARRY JAMES  O",
14    "product": "collection",
15    "managed_by_integration": 463433,
16    "subaccount_code": "ACCT_6uujpqtzmnufzkw",
17    "is_verified": false,
18    "settlement_schedule": "AUTO",
19    "active": true,
20    "migrate": false,
21    "id": 1151727,
22    "createdAt": "2024-08-26T09:24:28.723Z",
23    "updatedAt": "2024-08-26T09:24:28.723Z"
24  }
25}
```

##### Verify Account Number

Please endeavour to verify that the bank account details matches what you intended. Paystack won't be liable for payouts to the wrong bank account.

## [initialize a split payment permalink](https://paystack.com/docs/payments/split-payments/\#initialize-a-split-payment) Initialize a split payment

Split payments can be initialized by using the [Initialize TransactionAPI](https://paystack.com/docs/api/transaction#initialize) endpoint and passing the parameter `subaccount: "ACCT_xxxxxxxxxx"`.

cURLNodePHP

Show Response

```
1curl https://api.paystack.co/transaction/initialize
2-H "Authorization: Bearer YOUR_SECRET_KEY"
3-H "Content-Type: application/json"
4-d '{ "email": "customer@email.com",
5      "amount": "20000",
6      "subaccount": "ACCT_xxxxxxxxx"
7    }'
8-X POST
```

```
1{
2  "status": true,
3  "message": "Authorization URL created",
4  "data": {
5    "authorization_url": "https://checkout.paystack.com/nkdks46nymizns7",
6    "access_code": "nkdks46nymizns7",
7    "reference": "nms6uvr1pl"
8  }
9}
```

Split payments can be used in the following scenario:

- Shared payment between service provider and platform provider
- Split profit between different vendors
- Separate school fees in different account for example Tuition, Accomodation, Excursion

## [flat fee permalink](https://paystack.com/docs/payments/split-payments/\#flat-fee) Flat fee

By default, payments are split by percentage. For example, if a subaccount was created with `percentage_charge: 20`, 20% goes to the main account and the rest goes to the subaccount.

However, you can override this default and specify a flat fee that goes into your main account. To do this, pass the `transaction_charge` key when initializing a transaction.

In the snippet below, the main account gets a flat fee of `10000` and the subaccount gets the rest:

cURLNodePHP

Show Response

```
1curl https://api.paystack.co/transaction/initialize
2-H "Authorization: Bearer YOUR_SECRET_KEY"
3-H "Content-Type: application/json"
4-d '{ "email": "customer@email.com",
5      "amount": "20000",
6      "subaccount": "ACCT_xxxxxxxxx",
7      "transaction_charge": 10000
8    }'
9-X POST
```

```
1{
2  "status": true,
3  "message": "Authorization URL created",
4  "data": {
5    "authorization_url": "https://checkout.paystack.com/nkdks46nymizns7",
6    "access_code": "nkdks46nymizns7",
7    "reference": "nms6uvr1pl"
8  }
9}
```

## [bearer of transaction fee permalink](https://paystack.com/docs/payments/split-payments/\#bearer-of-transaction-fee) Bearer of transaction fee

By default, the Paystack charges are borne by the main account. To change this to a subaccount, pass the param `bearer: "subaccount"` while initializing a transaction.

cURLNodePHP

Show Response

```
1curl https://api.paystack.co/transaction/initialize
2-H "Authorization: Bearer YOUR_SECRET_KEY"
3-H "Content-Type: application/json"
4-d '{ "email": "customer@email.com",
5      "amount": "20000",
6      "subaccount": "ACCT_xxxxxxxxx",
7      "bearer": "subaccount"
8    }'
9-X POST
```

```
1{
2  "status": true,
3  "message": "Authorization URL created",
4  "data": {
5    "authorization_url": "https://checkout.paystack.com/nkdks46nymizns7",
6    "access_code": "nkdks46nymizns7",
7    "reference": "nms6uvr1pl"
8  }
9}
```

##### Invalid Split Configuration

When specifying a bearer, you need to ensure that the amount that goes into the subaccount is sufficient to bear the transaction fee. If the amount is insufficient, you'll get a `400 Bad Request` error.