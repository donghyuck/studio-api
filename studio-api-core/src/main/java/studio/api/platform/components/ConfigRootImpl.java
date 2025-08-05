package studio.api.platform.components;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.Resource;

import lombok.extern.slf4j.Slf4j;
import studio.echoes.platform.service.ConfigRoot;

@Slf4j
public class ConfigRootImpl implements ConfigRoot {

	private Resource rootResource;

	public ConfigRootImpl(Resource rootResource) {
		this.rootResource = rootResource;
	}

	public File getFile(String name) {
		try {
			return new File( getRootResource().getFile() , FilenameUtils.getName(name) ); 
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	public Optional<File> getFile(){ 
		File file ;
		try { 
			file = getRootResource().getFile() ;
		} catch (IOException e) {
			file = null;
		}
		return Optional.ofNullable( file ); 
	}

	private Resource getRootResource() {
		return rootResource;
	}

	public URI getRootURI() {
		try {
			return rootResource.getURI();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}
}

