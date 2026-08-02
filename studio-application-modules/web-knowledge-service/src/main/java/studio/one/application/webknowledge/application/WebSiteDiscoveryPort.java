package studio.one.application.webknowledge.application;

import java.net.URI;
import java.util.List;

public interface WebSiteDiscoveryPort {

    List<String> links(byte[] html, URI pageUri);
}
