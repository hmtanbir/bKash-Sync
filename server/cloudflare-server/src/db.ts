export interface KeyRecord {
  id?: number;
  key_string: string;
  phone_number: string;
  status: string;
  created_at?: string;
}

export interface TransactionRecord {
  trxId: string;
  amount: string;
  senderNumber: string;
  balance: string;
  datetime: string;
  userPhoneNumber: string;
  simSlot?: string;
}

export class DatabaseService {
  private db: D1Database;

  constructor(db: D1Database) {
    this.db = db;
  }

  // Check if a user's API Key exists and is active
  async isValidUserKey(key: string): Promise<boolean> {
    const result = await this.db
      .prepare("SELECT status FROM api_keys WHERE key_string = ? LIMIT 1")
      .bind(key)
      .first<{ status: string }>();
    return result?.status === "active";
  }

  // Match key to its associated owner number
  async getPhoneNumberForKey(key: string): Promise<string | null> {
    const result = await this.db
      .prepare("SELECT phone_number FROM api_keys WHERE key_string = ? LIMIT 1")
      .bind(key)
      .first<{ phone_number: string }>();
    return result ? result.phone_number : null;
  }

  // Create or swap an API Key for a phone number
  async issueUserKey(phoneNumber: string, newKey: string): Promise<void> {
    // Delete existing keys for the number to maintain 1-to-1 mapping, then insert
    await this.db.batch([
      this.db
        .prepare("DELETE FROM api_keys WHERE phone_number = ?")
        .bind(phoneNumber),
      this.db
        .prepare(
          "INSERT INTO api_keys (key_string, phone_number, status) VALUES (?, ?, 'active')",
        )
        .bind(newKey, phoneNumber),
    ]);
  }

  // Delete/Revoke a key
  async revokeKey(phoneNumber: string): Promise<void> {
    await this.db
      .prepare("DELETE FROM api_keys WHERE phone_number = ?")
      .bind(phoneNumber);
  }

  // Get all active users & credentials
  async getAllKeys(): Promise<KeyRecord[]> {
    const { results } = await this.db
      .prepare("SELECT * FROM api_keys ORDER BY created_at DESC")
      .all<KeyRecord>();
    return results;
  }

  // Write uploaded transactions (using IGNORE if already exists to prevent duplication failures)
  async saveTransactions(txs: TransactionRecord[]): Promise<void> {
    if (txs.length === 0) return;
    const statements = txs.map((tx) => {
      return this.db
        .prepare(
          `INSERT OR IGNORE INTO transactions (trx_id, amount, sender_number, balance, datetime, user_phone_number, sim_slot) 
           VALUES (?, ?, ?, ?, ?, ?, ?)`,
        )
        .bind(
          tx.trxId,
          tx.amount,
          tx.senderNumber,
          tx.balance,
          tx.datetime,
          tx.userPhoneNumber,
          tx.simSlot || "SIM 1",
        );
    });
    await this.db.batch(statements);
  }

  // Fetch all transactions safely for the admin dashboard
  async getAllTransactions(limit = 100): Promise<any[]> {
    const { results } = await this.db
      .prepare("SELECT * FROM transactions ORDER BY datetime DESC LIMIT ?")
      .bind(limit)
      .all();
    return results;
  }

  // Fetch all transactions registered for a specific user phone number (For Delta-Sync)
  async getTransactionsForUser(
    userPhoneNumber: string,
  ): Promise<TransactionRecord[]> {
    const { results } = await this.db
      .prepare(
        `SELECT trx_id, amount, sender_number, balance, datetime, user_phone_number, sim_slot 
         FROM transactions 
         WHERE user_phone_number = ?`,
      )
      .bind(userPhoneNumber)
      .all<any>();

    return results.map((row) => ({
      trxId: row.trx_id,
      amount: row.amount || "0",
      senderNumber: row.sender_number || "unknown",
      balance: row.balance || "0",
      datetime: row.datetime || "",
      userPhoneNumber: row.user_phone_number,
      simSlot: row.sim_slot || "SIM 1",
    }));
  }
}
