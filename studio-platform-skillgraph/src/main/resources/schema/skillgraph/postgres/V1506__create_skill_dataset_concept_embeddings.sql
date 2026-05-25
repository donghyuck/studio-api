create table tb_skill_dataset_concept_embedding (
    embedding_id        varchar(180) primary key,

    concept_id          varchar(120) not null references tb_skill_dataset_concept(concept_id),
    dataset_id          varchar(80) not null,
    provider            varchar(30) not null,
    concept_type        varchar(50) not null,

    external_code       varchar(150),
    preferred_label     varchar(1000),

    embedding_provider  varchar(50) not null,
    embedding_model     varchar(150) not null,
    embedding_dim       integer not null,

    text_type           varchar(50) not null,
    source_text         text not null,
    source_hash         varchar(128) not null,

    embedding           vector(1024) not null,

    status              varchar(30) not null default 'COMPLETED',
    error_message       text,

    created_at          timestamp not null default current_timestamp,
    updated_at          timestamp not null default current_timestamp,

    constraint ux_skill_dataset_concept_embedding
        unique (concept_id, embedding_provider, embedding_model, text_type, source_hash)
);

create index ix_skill_concept_embedding_concept
    on tb_skill_dataset_concept_embedding(concept_id);

create index ix_skill_concept_embedding_dataset
    on tb_skill_dataset_concept_embedding(dataset_id);

create index ix_skill_concept_embedding_provider
    on tb_skill_dataset_concept_embedding(provider);

create index ix_skill_concept_embedding_type
    on tb_skill_dataset_concept_embedding(concept_type);

create index ix_skill_concept_embedding_model
    on tb_skill_dataset_concept_embedding(embedding_provider, embedding_model);

create index ix_skill_concept_embedding_text_type
    on tb_skill_dataset_concept_embedding(text_type);

create index ix_skill_concept_embedding_vector_hnsw
    on tb_skill_dataset_concept_embedding using hnsw (embedding vector_cosine_ops);
