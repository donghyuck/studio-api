
/**
 *    Copyright 2015-2016 donghyuck
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package studio.one.platform.data.sqlquery.builder.xml;

import java.io.InputStream;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.data.sqlquery.builder.AbstractBuilder;
import studio.one.platform.data.sqlquery.builder.BuilderException;
import studio.one.platform.data.sqlquery.builder.SqlQueryBuilderAssistant;
import studio.one.platform.data.sqlquery.factory.Configuration;
import studio.one.platform.data.sqlquery.parser.XNode;
import studio.one.platform.data.sqlquery.parser.XPathParser;

/**
 * Parses an XML file containing SQL set definitions.
 * 
 * @changes
 * 2024-03-20 Remove this unused "sqlFragments" private field.
 */
@Slf4j
public class XmlSqlSetBuilder extends AbstractBuilder {

    private final XPathParser parser;
    private final String resource;
    private final SqlQueryBuilderAssistant builderAssistant;

    public XmlSqlSetBuilder(InputStream inputStream, Configuration configuration, String resource) {
        this(new XPathParser(inputStream, false, new Properties(), null), configuration, resource);
    }

    public XmlSqlSetBuilder(InputStream inputStream, Configuration configuration, String resource, String namespace) {
        this(inputStream, configuration, resource);
        // Set the current namespace if provided
        Optional.ofNullable(namespace)
        .ifPresent(this.builderAssistant::setCurrentNamespace);
    }

    private XmlSqlSetBuilder(XPathParser parser, Configuration configuration, String resource) {
        super(configuration);
        this.builderAssistant = new SqlQueryBuilderAssistant(configuration, resource);
        this.parser = Objects.requireNonNull(parser, "XPathParser must not be null");
        this.resource = Objects.requireNonNull(resource, "Resource name must not be null");
    }

    public void parse() {
        try {
            if (!configuration.isResourceLoaded(resource)) {
                configuration.addLoadedResource(resource);
            }
            XNode context = parser.evalNode("/sqlset");
            if (context != null) {
                configurationElement(context);
                List<XNode> statements = new ArrayList<>();
                statements.addAll(context.evalNodes("/sqlset/sql-query"));
                statements.addAll(context.evalNodes("/sqlset/sql"));
                buildStatement(statements);
                buildRowMapper(context.evalNodes("/sqlset/row-mapper"));
            } else {
                log.warn("No <sqlset> root element found in resource: {}", resource);
            }
        } catch (Exception e) {
            log.error("Error parsing SQL set: {}", resource, e);
            throw new BuilderException("Error parsing SQL set: " + resource, e);
        }
    }

    private void buildRowMapper(List<XNode> list) {
        for (XNode context : list) {
            final XmlRowMapperBuilder mapperParser = new XmlRowMapperBuilder(configuration, builderAssistant, context);
            try {
                mapperParser.parseRowMapperNode();
            } catch (Exception e) {
                log.error("Failed to parse row mapper: {}", context, e);
            }
        }
    }

    private void buildStatement(List<XNode> list) {
        for (XNode context : list) {
            final XmlStatementBuilder statementParser = new XmlStatementBuilder(configuration, builderAssistant, context);
            try {
                statementParser.parseStatementNode();
            } catch (Exception e) {
                log.error("Failed to parse SQL statement: {}", context, e);
            }
        }
    }

    private void configurationElement(XNode context) {

        String name = context.getStringAttribute("name", context.evalString("name"));
        String namespace = context.getStringAttribute("namespace", context.evalString("namespace"));
        String description = context.getStringAttribute("description", context.evalString("description"));
        String version = context.getStringAttribute("version", context.evalString("version"));

        log.debug("Parsing SQL set: name={}, namespace={}, version={}, description={}", name, namespace, version, description);

        namespace = (namespace == null || namespace.trim().isEmpty()) ? name : namespace;

        if (StringUtils.isEmpty(namespace)) {
            throw new BuilderException("Mapper's namespace cannot be empty");
        }

        builderAssistant.setCurrentNamespace(namespace);
        log.debug("BUILD SQL Name={}, Namespace={}, Version={}, Description={}", name, namespace, version, description);
    }
}
