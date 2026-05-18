DROP INDEX IF EXISTS idx_skill_dictionary_embedding;
DROP INDEX IF EXISTS idx_skill_candidate_embedding;

ALTER TABLE tb_skill_dictionary
    ALTER COLUMN embedding TYPE VECTOR;

ALTER TABLE tb_skill_candidate
    ALTER COLUMN embedding TYPE VECTOR;
