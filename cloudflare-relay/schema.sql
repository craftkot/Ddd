-- Only opaque AES-GCM ciphertext is stored. No plaintext chat content.
CREATE TABLE IF NOT EXISTS chat_events (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  topic TEXT NOT NULL,
  payload TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chat_topic_seq
  ON chat_events(topic, seq);
CREATE INDEX IF NOT EXISTS idx_chat_topic_created
  ON chat_events(topic, created_at);
CREATE INDEX IF NOT EXISTS idx_chat_expiry
  ON chat_events(expires_at);
