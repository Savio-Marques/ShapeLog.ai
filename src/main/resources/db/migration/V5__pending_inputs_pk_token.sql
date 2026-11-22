DELETE FROM pending_inputs;
ALTER TABLE pending_inputs DROP CONSTRAINT IF EXISTS pending_inputs_pkey;
ALTER TABLE pending_inputs ADD PRIMARY KEY (token);
CREATE INDEX IF NOT EXISTS idx_pending_inputs_chat_id ON pending_inputs (chat_id);
