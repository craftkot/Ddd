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

-- AES-GCM ciphertext split into small D1 rows. Seven-day expiration limits
-- storage usage on Workers Free (500 MB maximum per D1 database).
CREATE TABLE IF NOT EXISTS media_parts (
  topic TEXT NOT NULL,
  file_id TEXT NOT NULL,
  part_index INTEGER NOT NULL,
  payload TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  PRIMARY KEY (topic, file_id, part_index)
);
CREATE INDEX IF NOT EXISTS idx_media_parts_expiry ON media_parts(expires_at);

CREATE TABLE IF NOT EXISTS media_files (
  topic TEXT NOT NULL,
  file_id TEXT NOT NULL,
  chunk_count INTEGER NOT NULL,
  encoded_bytes INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  PRIMARY KEY (topic, file_id)
);
CREATE INDEX IF NOT EXISTS idx_media_files_expiry ON media_files(expires_at);

-- Daily cap on expensive Workers AI transcription requests.
CREATE TABLE IF NOT EXISTS ai_usage (
  day TEXT PRIMARY KEY,
  requests INTEGER NOT NULL DEFAULT 0
);
