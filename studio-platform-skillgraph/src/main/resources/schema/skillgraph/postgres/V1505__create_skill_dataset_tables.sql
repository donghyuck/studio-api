------------------------------------------------------------
-- Skill Dataset
------------------------------------------------------------

create table tb_skill_dataset (
    dataset_id       varchar(80) primary key,
    provider         varchar(30) not null,
    dataset_name     varchar(200) not null,
    version          varchar(50),
    language         varchar(10),
    source_location  varchar(1000),
    imported_at      timestamp not null default current_timestamp
);

create index ix_tb_skill_dataset_provider
    on tb_skill_dataset(provider);


------------------------------------------------------------
-- Skill Dataset Concept
------------------------------------------------------------

create table tb_skill_dataset_concept (
    concept_id        varchar(120) primary key,
    dataset_id        varchar(80) not null references tb_skill_dataset(dataset_id),

    provider          varchar(30) not null,
    concept_type      varchar(50) not null,

    external_code     varchar(150),
    parent_code       varchar(150),

    preferred_label   varchar(1000) not null,
    description       text,
    level_value       varchar(20),
    category_path     varchar(2000),
    normalized_label  varchar(1000),
    raw_json          jsonb,

    created_at        timestamp not null default current_timestamp,
    updated_at        timestamp not null default current_timestamp
);

create index ix_tb_skill_dataset_concept_dataset
    on tb_skill_dataset_concept(dataset_id);

create index ix_tb_skill_dataset_concept_provider
    on tb_skill_dataset_concept(provider);

create index ix_tb_skill_dataset_concept_type
    on tb_skill_dataset_concept(concept_type);

create index ix_tb_skill_dataset_concept_external
    on tb_skill_dataset_concept(external_code);

create index ix_tb_skill_dataset_concept_parent
    on tb_skill_dataset_concept(parent_code);

create index ix_tb_skill_dataset_concept_normalized
    on tb_skill_dataset_concept(normalized_label);

create index ix_tb_skill_dataset_concept_raw_json
    on tb_skill_dataset_concept using gin(raw_json);


------------------------------------------------------------
-- Skill Dataset Relation
------------------------------------------------------------

create table tb_skill_dataset_relation (
    relation_id        varchar(140) primary key,
    dataset_id         varchar(80) not null references tb_skill_dataset(dataset_id),

    provider           varchar(30) not null,

    source_concept_id  varchar(120) not null references tb_skill_dataset_concept(concept_id),
    target_concept_id  varchar(120) not null references tb_skill_dataset_concept(concept_id),

    relation_type      varchar(50) not null,
    confidence         numeric(5,4),
    raw_json           jsonb,

    created_at         timestamp not null default current_timestamp
);

create index ix_tb_skill_dataset_relation_source
    on tb_skill_dataset_relation(source_concept_id);

create index ix_tb_skill_dataset_relation_target
    on tb_skill_dataset_relation(target_concept_id);

create index ix_tb_skill_dataset_relation_type
    on tb_skill_dataset_relation(relation_type);

create index ix_tb_skill_dataset_relation_dataset
    on tb_skill_dataset_relation(dataset_id);

create index ix_tb_skill_dataset_relation_raw_json
    on tb_skill_dataset_relation using gin(raw_json);


------------------------------------------------------------
-- Skill Dataset Import Job
------------------------------------------------------------

create table tb_skill_dataset_import_job (
    job_id             varchar(120) primary key,

    provider           varchar(30) not null,
    dataset_id         varchar(80) not null,
    dataset_name       varchar(200),

    version            varchar(50),
    language           varchar(10),
    source_location    varchar(1000) not null,

    status             varchar(30) not null,

    total_rows         bigint not null default 0,
    processed_rows     bigint not null default 0,
    created_concepts   bigint not null default 0,
    created_relations  bigint not null default 0,
    failed_rows        bigint not null default 0,

    error_message      text,

    created_at         timestamp not null default current_timestamp,
    started_at         timestamp,
    completed_at       timestamp,
    updated_at         timestamp not null default current_timestamp
);

create index ix_tb_skill_import_status
    on tb_skill_dataset_import_job(status);

create index ix_tb_skill_import_created
    on tb_skill_dataset_import_job(created_at);


------------------------------------------------------------
-- Internal Skill ↔ Reference Dataset Mapping
------------------------------------------------------------

create table tb_skill_dataset_mapping (
    mapping_id        varchar(140) primary key,

    skill_id          varchar(120) not null,
    concept_id        varchar(120) not null references tb_skill_dataset_concept(concept_id),

    mapping_type      varchar(50) not null,
    similarity_score  numeric(6,5),
    confidence        numeric(5,4),

    created_at        timestamp not null default current_timestamp,
    updated_at        timestamp not null default current_timestamp,

    constraint ux_tb_skill_dataset_mapping
        unique (skill_id, concept_id, mapping_type)
);

create index ix_tb_skill_dataset_mapping_skill
    on tb_skill_dataset_mapping(skill_id);

create index ix_tb_skill_dataset_mapping_concept
    on tb_skill_dataset_mapping(concept_id);