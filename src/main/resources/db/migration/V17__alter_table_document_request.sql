ALTER TABLE document_request
    ADD COLUMN claimed_at TIMESTAMPTZ,
ADD COLUMN attempts INT NOT NULL DEFAULT 0, -- TODO(ACR-1101): Not yet used
ADD COLUMN last_error TEXT, -- TODO(ACR-1101): Not yet used
ADD COLUMN concurrency_version BIGINT NOT NULL DEFAULT 0;

-- For claiming eligible documents to generate
CREATE INDEX idx_document_request_status_created_at
    ON document_request (status, created_at);
