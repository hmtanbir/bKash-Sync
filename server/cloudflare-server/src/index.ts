import { DatabaseService, TransactionRecord } from "./db";

export interface Env {
  DB: D1Database;
  ADMIN_API_KEY: string; // Declared globally as a Worker Secret
}

export default {
  async fetch(
    request: Request,
    env: Env,
    ctx: ExecutionContext,
  ): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method;

    // Helper: Build standard CORS & Clean headers response
    const jsonResponse = (data: object, status = 200) => {
      return new Response(JSON.stringify(data), {
        status,
        headers: {
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization",
        },
      });
    };

    // Handle preflight CORS request
    if (method === "OPTIONS") {
      return jsonResponse({ message: "OK" });
    }

    const authHeader = request.headers.get("Authorization");
    const extractedToken = authHeader?.startsWith("Bearer ")
      ? authHeader.substring(7).trim()
      : null;

    const dbService = new DatabaseService(env.DB);

    // ================= CLIENT APP INTERFACE ENDPOINTS =================

    /**
     * POST /verify
     * Used by the client App to handshake its API configuration credentials.
     * Accessible via matching Admin API key or an active registered user API key.
     */
    if (path === "/verify" && method === "POST") {
      if (!extractedToken) {
        return jsonResponse(
          { success: false, message: "Missing authorization token" },
          401,
        );
      }

      const isAdmin = extractedToken === env.ADMIN_API_KEY;
      const isValidUser = await dbService.isValidUserKey(extractedToken);

      if (isAdmin || isValidUser) {
        return jsonResponse({ success: true });
      }

      return jsonResponse(
        { success: false, message: "Invalid API authorization key" },
        403,
      );
    }

    /**
     * POST /transactions
     * Receives batched transaction details uploaded by the device.
     */
    if (path === "/transactions" && method === "POST") {
      if (!extractedToken) {
        return jsonResponse(
          { success: false, message: "Missing authorization" },
          401,
        );
      }

      const isAdmin = extractedToken === env.ADMIN_API_KEY;
      const keyPhone = await dbService.getPhoneNumberForKey(extractedToken);

      if (!isAdmin && !keyPhone) {
        return jsonResponse(
          { success: false, message: "Unauthorized credentials" },
          403,
        );
      }

      try {
        const body: { transactions?: any[] } = await request.json();
        if (!body.transactions || !Array.isArray(body.transactions)) {
          return jsonResponse(
            { success: false, message: "Invalid payload layout" },
            400,
          );
        }

        // Validate structure, bind phone identifier of key to record if not provided
        const normalizedList: TransactionRecord[] = body.transactions.map(
          (item) => ({
            trxId: item.trxId || "",
            amount: item.amount || "0",
            senderNumber: item.senderNumber || "unknown",
            balance: item.balance || "0",
            datetime: item.datetime || "",
            userPhoneNumber: item.userPhoneNumber || keyPhone || "ADMIN",
            simSlot: item.simSlot || "SIM 1",
          }),
        );

        await dbService.saveTransactions(normalizedList);

        return jsonResponse({
          success: true,
          message: `Successfully stored and synchronized ${normalizedList.length} transactions.`,
        });
      } catch (err: any) {
        return jsonResponse({ success: false, message: err.message }, 500);
      }
    }

    // ================= USER SELF-SERVICE ENDPOINT =================

    /**
     * GET /user/transactions?phone_number=XXXXXXXXX
     * A user with a valid API key can get their own transactions.
     * The phone_number query param must match the key's registered phone number.
     */
    if (path === "/user/transactions" && method === "GET") {
      if (!extractedToken) {
        return jsonResponse(
          { success: false, message: "Missing authorization" },
          401,
        );
      }

      const keyPhone = await dbService.getPhoneNumberForKey(extractedToken);
      if (!keyPhone) {
        return jsonResponse(
          { success: false, message: "Invalid or inactive API key" },
          403,
        );
      }

      const requestedPhone = url.searchParams.get("phone_number");
      if (!requestedPhone) {
        return jsonResponse(
          {
            success: false,
            message: "phone_number query parameter is required",
          },
          400,
        );
      }

      // Enforce: the user's API key phone number MUST match the requested phone number
      if (keyPhone !== requestedPhone) {
        return jsonResponse(
          {
            success: false,
            message: "Access denied. You can only view your own transactions.",
          },
          403,
        );
      }

      try {
        const txs = await dbService.getTransactionsForUser(requestedPhone);
        return jsonResponse({
          success: true,
          phoneNumber: requestedPhone,
          count: txs.length,
          transactions: txs,
        });
      } catch (err: any) {
        return jsonResponse({ success: false, message: err.message }, 500);
      }
    }

    // ================= SMART DELTA-SYNC ENDPOINT =================

    /**
     * POST /synctransaction
     * Receives local client transaction IDs, compares them to server D1 records,
     * and returns bidirectional synchronization targets (missingOnServer / missingOnClient).
     */
    if (path === "/synctransaction" && method === "POST") {
      if (!extractedToken) {
        return jsonResponse(
          { success: false, message: "Missing authorization" },
          401,
        );
      }

      const isAdmin = extractedToken === env.ADMIN_API_KEY;
      const keyPhone = await dbService.getPhoneNumberForKey(extractedToken);

      if (!isAdmin && !keyPhone) {
        return jsonResponse(
          { success: false, message: "Unauthorized credentials" },
          403,
        );
      }

      try {
        const body: { userPhoneNumber?: string; localTrxIds?: string[] } =
          await request.json();

        // Match target identification variables
        const targetPhone = body.userPhoneNumber || keyPhone || "ADMIN";
        const localTrxIds = body.localTrxIds || [];

        // 1. Fetch D1 transactions for this specific user
        const serverTransactions =
          await dbService.getTransactionsForUser(targetPhone);

        const serverTrxIdSet = new Set(
          serverTransactions.map((tx) => tx.trxId),
        );
        const localTrxIdSet = new Set(localTrxIds);

        // 2. Identify transaction IDs present locally but missing on the server (D1)
        const missingOnServer = localTrxIds.filter(
          (trxId) => trxId && !serverTrxIdSet.has(trxId),
        );

        // 3. Identify full transaction data present on the server but missing in the local database
        const missingOnClient = serverTransactions.filter(
          (tx) => tx.trxId && !localTrxIdSet.has(tx.trxId),
        );

        return jsonResponse({
          success: true,
          missingOnServer,
          missingOnClient,
        });
      } catch (err: any) {
        return jsonResponse({ success: false, message: err.message }, 500);
      }
    }

    // ================= CENTRAL ADMIN PANEL ENDPOINTS =================

    // All endpoints beneath require verification against the core ADMIN_API_KEY
    if (extractedToken !== env.ADMIN_API_KEY) {
      return jsonResponse(
        {
          success: false,
          message: "Access Denied. Admin configuration required.",
        },
        401,
      );
    }

    /**
     * POST /admin/keys
     * Admin uses this to issue or refresh keys.
     * Body: { phoneNumber: "018XXXXXXXX", key: "USER_CUSTOM_GENERATED_KEY" }
     */
    if (path === "/admin/keys" && method === "POST") {
      try {
        const body: {
          phoneNumber?: string;
          key?: string;
          action?: "issue" | "revoke";
        } = await request.json();

        if (!body.phoneNumber) {
          return jsonResponse(
            { success: false, message: "phoneNumber is required" },
            400,
          );
        }

        if (body.action === "revoke") {
          await dbService.revokeKey(body.phoneNumber);
          return jsonResponse({
            success: true,
            message: `Key revoked for ${body.phoneNumber}`,
          });
        }

        if (!body.key) {
          return jsonResponse(
            {
              success: false,
              message: "key is required to register a brand new device profile",
            },
            400,
          );
        }

        await dbService.issueUserKey(body.phoneNumber, body.key);
        return jsonResponse({
          success: true,
          message: `Device active. User key has been generated and restricted to target: ${body.phoneNumber}`,
        });
      } catch (err: any) {
        return jsonResponse({ success: false, message: err.message }, 500);
      }
    }

    /**
     * GET /admin/users
     * Admin uses this to review all active users and their registered numbers.
     */
    if (path === "/admin/users" && method === "GET") {
      try {
        const keys = await dbService.getAllKeys();
        return jsonResponse({ success: true, users: keys });
      } catch (err: any) {
        return jsonResponse({ success: false, message: err.message }, 500);
      }
    }

    /**
     * GET /admin/transactions
     * Admin uses this to pull all synced bKash transactions.
     */
    if (path === "/admin/transactions" && method === "GET") {
      try {
        const txs = await dbService.getAllTransactions(200);
        return jsonResponse({ success: true, transactions: txs });
      } catch (err: any) {
        return jsonResponse({ success: false, message: err.message }, 500);
      }
    }

    // Default 404 Route
    return jsonResponse(
      { success: false, message: "Endpoint Resource Not Found" },
      404,
    );
  },
};
