# bKash Sync Cloudflare Server - API Documentation

This documentation describes the API endpoints exposed by the bKash Sync Cloudflare Worker. All requests must include the proper authorization headers and use HTTPS.

---

## Authentication

All endpoints require authentication using a Bearer token in the `Authorization` header:

```http
Authorization: Bearer <YOUR_API_KEY>
```

Depending on the endpoint, the key must be either the **Admin API Key** (`ADMIN_API_KEY`) or a registered **User API Key**.

---

## Endpoint Summary

| Method | Path | Auth Type | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/verify` | Admin / User | Handshake API key to verify if it is valid and active. |
| `POST` | `/transactions` | Admin / User | Upload batched transactions from the device. |
| `GET` | `/user/transactions` | User | Retrieve synced transactions for the user's phone number. |
| `POST` | `/synctransaction` | Admin / User | Perform delta synchronization to identify missing records. |
| `POST` | `/admin/keys` | Admin | Issue, refresh, or revoke user API keys. |
| `GET` | `/admin/users` | Admin | List all registered API keys and user numbers. |
| `GET` | `/admin/transactions` | Admin | Retrieve all synced transactions across all users. |

---

## Client App Endpoints

### 1. Verify API Key (`POST /verify`)

Verifies whether the provided API key is valid (either matching the global admin key or a registered active user key).

* **URL:** `/verify`
* **Method:** `POST`
* **Headers:**
  * `Authorization: Bearer <API_KEY>`

#### cURL Example
```bash
curl -X POST https://bkash-sync-cloudflare-server.yourdomain.workers.dev/verify \
  -H "Authorization: Bearer YOUR_API_KEY"
```

#### Response (Success - `200 OK`)
```json
{
  "success": true
}
```

#### Response (Unauthorized - `401 Unauthorized`)
```json
{
  "success": false,
  "message": "Missing authorization token"
}
```

#### Response (Forbidden - `403 Forbidden`)
```json
{
  "success": false,
  "message": "Invalid API authorization key"
}
```

---

### 2. Upload Transactions (`POST /transactions`)

Receives a batch of transaction details parsed and uploaded by the device.

* **URL:** `/transactions`
* **Method:** `POST`
* **Headers:**
  * `Authorization: Bearer <API_KEY>`
  * `Content-Type: application/json`

#### Request Body Schema
| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `transactions` | `Array` | Yes | List of transaction objects. |
| `transactions[].trxId` | `String` | Yes | Unique transaction ID (e.g. `AL85K9J2P1`). |
| `transactions[].amount` | `String` | No | Transaction amount (default: `"0"`). |
| `transactions[].senderNumber` | `String` | No | Number sending the money (default: `"unknown"`). |
| `transactions[].balance` | `String` | No | Remaining account balance after transaction (default: `"0"`). |
| `transactions[].datetime` | `String` | No | Date and time string of the transaction. |
| `transactions[].userPhoneNumber` | `String` | No | Target owner's phone number. Defaults to the phone number associated with the API key. |
| `transactions[].simSlot` | `String` | No | SIM slot that received the SMS (default: `"SIM 1"`). |

#### cURL Example
```bash
curl -X POST https://bkash-sync-cloudflare-server.yourdomain.workers.dev/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "transactions": [
      {
        "trxId": "AL85K9J2P1",
        "amount": "500.00",
        "senderNumber": "01700000000",
        "balance": "1500.00",
        "datetime": "2026-06-19 12:00:00",
        "simSlot": "SIM 1"
      }
    ]
  }'
```

#### Response (Success - `200 OK`)
```json
{
  "success": true,
  "message": "Successfully stored and synchronized 1 transactions."
}
```

---

### 3. Retrieve User Transactions (`GET /user/transactions`)

Retrieves the list of synced transactions belonging to the user's own phone number. The API key must match the `phone_number` query parameter.

* **URL:** `/user/transactions`
* **Method:** `GET`
* **Headers:**
  * `Authorization: Bearer <USER_API_KEY>`
* **Query Parameters:**
  * `phone_number` (Required): The registered phone number of the API key owner.

#### cURL Example
```bash
curl -X GET "https://bkash-sync-cloudflare-server.yourdomain.workers.dev/user/transactions?phone_number=01800000000" \
  -H "Authorization: Bearer USER_API_KEY"
```

#### Response (Success - `200 OK`)
```json
{
  "success": true,
  "phoneNumber": "01800000000",
  "count": 1,
  "transactions": [
    {
      "trxId": "AL85K9J2P1",
      "amount": "500.00",
      "senderNumber": "01700000000",
      "balance": "1500.00",
      "datetime": "2026-06-19 12:00:00",
      "userPhoneNumber": "01800000000",
      "simSlot": "SIM 1"
    }
  ]
}
```

---

### 4. Smart Delta-Sync (`POST /synctransaction`)

Compares client-side transaction IDs against server records and returns what is missing on both ends (bidirectional sync targets).

* **URL:** `/synctransaction`
* **Method:** `POST`
* **Headers:**
  * `Authorization: Bearer <API_KEY>`
  * `Content-Type: application/json`

#### Request Body Schema
| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `userPhoneNumber` | `String` | No | Target phone number. Defaults to the phone number associated with the API key. |
| `localTrxIds` | `Array<String>` | No | List of transaction IDs stored locally on the client. |

#### cURL Example
```bash
curl -X POST https://bkash-sync-cloudflare-server.yourdomain.workers.dev/synctransaction \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "userPhoneNumber": "01800000000",
    "localTrxIds": ["AL85K9J2P1", "LOCAL_ONLY_TRX_999"]
  }'
```

#### Response (Success - `200 OK`)
```json
{
  "success": true,
  "missingOnServer": [
    "LOCAL_ONLY_TRX_999"
  ],
  "missingOnClient": [
    {
      "trxId": "SERVER_ONLY_TRX_888",
      "amount": "250.00",
      "senderNumber": "01900000000",
      "balance": "1750.00",
      "datetime": "2026-06-19 11:30:00",
      "userPhoneNumber": "01800000000",
      "simSlot": "SIM 1"
    }
  ]
}
```

---

## Admin Endpoints

All admin endpoints require the **Admin API Key** in the authorization header.

### 5. Issue/Revoke User Key (`POST /admin/keys`)

Registers, updates, or revokes a client device profile/API key mapping.

* **URL:** `/admin/keys`
* **Method:** `POST`
* **Headers:**
  * `Authorization: Bearer <ADMIN_API_KEY>`
  * `Content-Type: application/json`

#### Request Body Schema (Issue/Refresh)
```json
{
  "phoneNumber": "01800000000",
  "key": "A_SECURE_GENERATED_API_KEY",
  "action": "issue" 
}
```

#### Request Body Schema (Revoke)
```json
{
  "phoneNumber": "01800000000",
  "action": "revoke"
}
```

#### cURL Example (Issue Key)
```bash
curl -X POST https://bkash-sync-cloudflare-server.yourdomain.workers.dev/admin/keys \
  -H "Authorization: Bearer ADMIN_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "phoneNumber": "01800000000",
    "key": "A_SECURE_GENERATED_API_KEY"
  }'
```

#### Response (Success - Issue - `200 OK`)
```json
{
  "success": true,
  "message": "Device active. User key has been generated and restricted to target: 01800000000"
}
```

#### cURL Example (Revoke Key)
```bash
curl -X POST https://bkash-sync-cloudflare-server.yourdomain.workers.dev/admin/keys \
  -H "Authorization: Bearer ADMIN_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "phoneNumber": "01800000000",
    "action": "revoke"
  }'
```

#### Response (Success - Revoke - `200 OK`)
```json
{
  "success": true,
  "message": "Key revoked for 01800000000"
}
```

---

### 6. List Registered Keys (`GET /admin/users`)

Lists all registered API keys and their status.

* **URL:** `/admin/users`
* **Method:** `GET`
* **Headers:**
  * `Authorization: Bearer <ADMIN_API_KEY>`

#### cURL Example
```bash
curl -X GET https://bkash-sync-cloudflare-server.yourdomain.workers.dev/admin/users \
  -H "Authorization: Bearer ADMIN_API_KEY"
```

#### Response (Success - `200 OK`)
```json
{
  "success": true,
  "users": [
    {
      "id": 1,
      "key_string": "A_SECURE_GENERATED_API_KEY",
      "phone_number": "01800000000",
      "status": "active",
      "created_at": "2026-06-19 11:00:00"
    }
  ]
}
```

---

### 7. Retrieve All Transactions (`GET /admin/transactions`)

Retrieves the latest 200 transactions stored on the server across all device accounts.

* **URL:** `/admin/transactions`
* **Method:** `GET`
* **Headers:**
  * `Authorization: Bearer <ADMIN_API_KEY>`

#### cURL Example
```bash
curl -X GET https://bkash-sync-cloudflare-server.yourdomain.workers.dev/admin/transactions \
  -H "Authorization: Bearer ADMIN_API_KEY"
```

#### Response (Success - `200 OK`)
```json
{
  "success": true,
  "transactions": [
    {
      "trx_id": "AL85K9J2P1",
      "amount": "500.00",
      "sender_number": "01700000000",
      "balance": "1500.00",
      "datetime": "2026-06-19 12:00:00",
      "user_phone_number": "01800000000",
      "sim_slot": "SIM 1",
      "synced_at": "2026-06-19 12:05:00"
    }
  ]
}
```
