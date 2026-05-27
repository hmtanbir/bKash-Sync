-- Table to manage generated user API keys mapped to device phone numbers
CREATE TABLE IF NOT EXISTS api_keys (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    key_string TEXT UNIQUE NOT NULL,
    phone_number TEXT UNIQUE NOT NULL,
    status TEXT NOT NULL DEFAULT 'active', -- 'active' or 'paused'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table to store synced bKash SMS transactions uploaded by the app
CREATE TABLE IF NOT EXISTS transactions (
    trx_id TEXT PRIMARY KEY,
    amount TEXT NOT NULL,
    sender_number TEXT NOT NULL,
    balance TEXT NOT NULL,
    datetime TEXT NOT NULL,
    user_phone_number TEXT NOT NULL,
    sim_slot TEXT DEFAULT 'SIM 1',          -- Identifies receiving SIM card (SIM 1, SIM 2, etc.)
    synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index user queries for scale
CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions(user_phone_number);