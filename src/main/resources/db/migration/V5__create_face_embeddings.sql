-- Biometric templates produced by the independent InsightFace service.
-- Source face images are not stored.
CREATE TABLE IF NOT EXISTS face_embeddings (
    student_id UUID PRIMARY KEY
        REFERENCES students (id) ON DELETE CASCADE,
    embedding_id VARCHAR(128) NOT NULL UNIQUE,
    embedding BYTEA NOT NULL,
    dimension SMALLINT NOT NULL DEFAULT 512,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT face_embeddings_dimension_check
        CHECK (dimension = 512),
    CONSTRAINT face_embeddings_vector_size_check
        CHECK (OCTET_LENGTH(embedding) = dimension * 4)
);

COMMENT ON TABLE face_embeddings IS
    'InsightFace biometric templates; source face images are not stored.';
COMMENT ON COLUMN face_embeddings.embedding IS
    'L2-normalized 512-dimensional InsightFace embedding as float32 bytes.';
