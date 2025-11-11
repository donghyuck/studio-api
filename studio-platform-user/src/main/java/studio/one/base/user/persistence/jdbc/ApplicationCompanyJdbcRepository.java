package studio.one.base.user.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationCompany;
import studio.one.base.user.persistence.ApplicationCompanyRepository;

@Repository
public class ApplicationCompanyJdbcRepository extends BaseJdbcRepository implements ApplicationCompanyRepository {

    private static final String TABLE = "TB_APPLICATION_COMPANY";
    private static final String PROPERTY_TABLE = "TB_APPLICATION_COMPANY_PROPERTY";

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "companyId", "COMPANY_ID",
            "name", "NAME",
            "displayName", "DISPLAY_NAME",
            "domainName", "DOMAIN_NAME",
            "creationDate", "CREATION_DATE",
            "modifiedDate", "MODIFIED_DATE");

    private static final RowMapper<ApplicationCompany> COMPANY_ROW_MAPPER = (rs, rowNum) -> {
        ApplicationCompany company = new ApplicationCompany();
        company.setCompanyId(rs.getLong("COMPANY_ID"));
        company.setName(rs.getString("NAME"));
        company.setDisplayName(rs.getString("DISPLAY_NAME"));
        company.setDomainName(rs.getString("DOMAIN_NAME"));
        company.setDescription(rs.getString("DESCRIPTION"));
        Timestamp created = rs.getTimestamp("CREATION_DATE");
        Timestamp modified = rs.getTimestamp("MODIFIED_DATE");
        company.setCreationDate(created == null ? null : created.toInstant());
        company.setModifiedDate(modified == null ? null : modified.toInstant());
        company.setProperties(new HashMap<>());
        return company;
    };

    private final SimpleJdbcInsert insert;

    public ApplicationCompanyJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        super(namedTemplate);
        this.insert = new SimpleJdbcInsert(this.jdbcTemplate)
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("COMPANY_ID");
    }

    @Override
    public Page<ApplicationCompany> findAll(Pageable pageable) {
        String select = """
                select COMPANY_ID, NAME, DISPLAY_NAME, DOMAIN_NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_COMPANY
                """;
        String count = "select count(*) from TB_APPLICATION_COMPANY";
        Page<ApplicationCompany> page = queryPage(select, count, Map.of(), pageable, COMPANY_ROW_MAPPER, "COMPANY_ID", SORT_COLUMNS);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public List<ApplicationCompany> findAll() {
        String sql = """
                select COMPANY_ID, NAME, DISPLAY_NAME, DOMAIN_NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_COMPANY
                  order by COMPANY_ID
                """;
        List<ApplicationCompany> companies = namedTemplate.query(sql, COMPANY_ROW_MAPPER);
        loadProperties(companies);
        return companies;
    }

    @Override
    public Optional<ApplicationCompany> findById(Long companyId) {
        String sql = """
                select COMPANY_ID, NAME, DISPLAY_NAME, DOMAIN_NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_COMPANY
                 where COMPANY_ID = :companyId
                """;
        Optional<ApplicationCompany> result = queryOptional(sql, Map.of("companyId", companyId), COMPANY_ROW_MAPPER);
        result.ifPresent(this::loadProperties);
        return result;
    }

    @Override
    public Optional<ApplicationCompany> findByName(String name) {
        String sql = """
                select COMPANY_ID, NAME, DISPLAY_NAME, DOMAIN_NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_COMPANY
                 where lower(NAME) = lower(:name)
                """;
        Optional<ApplicationCompany> result = queryOptional(sql, Map.of("name", name), COMPANY_ROW_MAPPER);
        result.ifPresent(this::loadProperties);
        return result;
    }

    @Override
    public Optional<ApplicationCompany> findByDomainName(String domainName) {
        String sql = """
                select COMPANY_ID, NAME, DISPLAY_NAME, DOMAIN_NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_COMPANY
                 where lower(DOMAIN_NAME) = lower(:domainName)
                """;
        Optional<ApplicationCompany> result = queryOptional(sql, Map.of("domainName", domainName), COMPANY_ROW_MAPPER);
        result.ifPresent(this::loadProperties);
        return result;
    }

    @Override
    public boolean existsByName(String name) {
        String sql = "select exists(select 1 from TB_APPLICATION_COMPANY where lower(NAME) = lower(:name))";
        Boolean exists = namedTemplate.queryForObject(sql, Map.of("name", name), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Page<ApplicationCompany> search(String keyword, Pageable pageable) {
        Map<String, Object> params = Map.of("q", keyword == null ? "" : "%" + keyword.toLowerCase() + "%");
        String select = """
                select COMPANY_ID, NAME, DISPLAY_NAME, DOMAIN_NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_COMPANY
                 where (:q = '' or
                        lower(NAME) like :q or
                        lower(DISPLAY_NAME) like :q or
                        lower(DOMAIN_NAME) like :q)
                """;
        String count = """
                select count(*)
                  from TB_APPLICATION_COMPANY
                 where (:q = '' or
                        lower(NAME) like :q or
                        lower(DISPLAY_NAME) like :q or
                        lower(DOMAIN_NAME) like :q)
                """;
        Page<ApplicationCompany> page = queryPage(select, count, params, pageable, COMPANY_ROW_MAPPER, "COMPANY_ID", SORT_COLUMNS);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public ApplicationCompany save(ApplicationCompany company) {
        if (company.getCompanyId() == null) {
            return insert(company);
        }
        return update(company);
    }

    private ApplicationCompany insert(ApplicationCompany company) {
        Instant now = Instant.now();
        if (company.getCreationDate() == null) {
            company.setCreationDate(now);
        }
        if (company.getModifiedDate() == null) {
            company.setModifiedDate(company.getCreationDate());
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("NAME", company.getName())
                .addValue("DISPLAY_NAME", company.getDisplayName())
                .addValue("DOMAIN_NAME", company.getDomainName())
                .addValue("DESCRIPTION", company.getDescription())
                .addValue("CREATION_DATE", Timestamp.from(company.getCreationDate()))
                .addValue("MODIFIED_DATE", Timestamp.from(company.getModifiedDate()));
        Number key = insert.executeAndReturnKey(params);
        company.setCompanyId(key.longValue());
        replaceProperties(PROPERTY_TABLE, "COMPANY_ID", company.getCompanyId(), company.getProperties());
        return company;
    }

    private ApplicationCompany update(ApplicationCompany company) {
        if (company.getModifiedDate() == null) {
            company.setModifiedDate(Instant.now());
        }
        String sql = """
                update TB_APPLICATION_COMPANY
                   set NAME = :name,
                       DISPLAY_NAME = :displayName,
                       DOMAIN_NAME = :domainName,
                       DESCRIPTION = :description,
                       MODIFIED_DATE = :modifiedDate
                 where COMPANY_ID = :companyId
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("name", company.getName());
        params.put("displayName", company.getDisplayName());
        params.put("domainName", company.getDomainName());
        params.put("description", company.getDescription());
        params.put("modifiedDate", Timestamp.from(company.getModifiedDate()));
        params.put("companyId", company.getCompanyId());
        namedTemplate.update(sql, params);
        replaceProperties(PROPERTY_TABLE, "COMPANY_ID", company.getCompanyId(), company.getProperties());
        return company;
    }

    @Override
    public void delete(ApplicationCompany company) {
        if (company != null && company.getCompanyId() != null) {
            deleteById(company.getCompanyId());
        }
    }

    @Override
    public void deleteById(Long companyId) {
        namedTemplate.update("delete from TB_APPLICATION_COMPANY where COMPANY_ID = :companyId", Map.of("companyId", companyId));
    }

    private void loadProperties(ApplicationCompany company) {
        if (company == null || company.getCompanyId() == null) {
            return;
        }
        Map<Long, Map<String, String>> props = fetchProperties(PROPERTY_TABLE, "COMPANY_ID", List.of(company.getCompanyId()));
        company.setProperties(new HashMap<>(props.getOrDefault(company.getCompanyId(), Map.of())));
    }

    private void loadProperties(List<ApplicationCompany> companies) {
        List<Long> ids = companies.stream()
                .map(ApplicationCompany::getCompanyId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Map<String, String>> props = fetchProperties(PROPERTY_TABLE, "COMPANY_ID", ids);
        for (ApplicationCompany company : companies) {
            Map<String, String> map = props.get(company.getCompanyId());
            company.setProperties(map == null ? new HashMap<>() : new HashMap<>(map));
        }
    }
}
