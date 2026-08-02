package studio.one.application.webknowledge.application;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class WebSitemapParser {

    private static final int MAX_LOCATIONS = 2_000;
    private static final int MAX_LOCATION_LENGTH = 2_048;

    public ParsedSitemap parse(byte[] xml) {
        if (xml == null || xml.length == 0) {
            return ParsedSitemap.empty();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            Element root = document.getDocumentElement();
            String rootName = root == null ? "" : root.getLocalName();
            boolean index = "sitemapindex".equalsIgnoreCase(rootName);
            boolean urlSet = "urlset".equalsIgnoreCase(rootName);
            if (!index && !urlSet) {
                throw new IllegalArgumentException("SITEMAP_FORMAT_INVALID");
            }
            NodeList locations = document.getElementsByTagNameNS("*", "loc");
            if (locations.getLength() > MAX_LOCATIONS) {
                throw new IllegalArgumentException("SITEMAP_LOCATION_LIMIT_EXCEEDED");
            }
            List<String> values = new ArrayList<>(locations.getLength());
            for (int position = 0; position < locations.getLength(); position++) {
                String value = locations.item(position).getTextContent();
                if (value == null || value.isBlank() || value.length() > MAX_LOCATION_LENGTH) {
                    continue;
                }
                values.add(value.trim());
            }
            return index
                    ? new ParsedSitemap(List.of(), List.copyOf(values))
                    : new ParsedSitemap(List.copyOf(values), List.of());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("SITEMAP_FORMAT_INVALID", ex);
        }
    }

    public record ParsedSitemap(List<String> pageLocations, List<String> sitemapLocations) {
        public ParsedSitemap {
            pageLocations = pageLocations == null ? List.of() : List.copyOf(pageLocations);
            sitemapLocations = sitemapLocations == null ? List.of() : List.copyOf(sitemapLocations);
        }

        static ParsedSitemap empty() {
            return new ParsedSitemap(List.of(), List.of());
        }
    }
}
