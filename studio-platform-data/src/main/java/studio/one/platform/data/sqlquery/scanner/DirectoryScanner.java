/**
 *    Copyright 2015-2017 donghyuck
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
package studio.one.platform.data.sqlquery.scanner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import studio.one.platform.data.sqlquery.builder.BuilderException;
import studio.one.platform.data.sqlquery.builder.xml.XmlSqlSetBuilder;
import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.exception.ConfigurationError;
import studio.one.platform.service.Repository;

@Slf4j
public class DirectoryScanner {
	
	protected static class DirectoryListener implements FileAlterationListener {

		private SqlQueryFactory sqlQueryFactory;
		
		public DirectoryListener(SqlQueryFactory sqlQueryFactory) {
			this.sqlQueryFactory = sqlQueryFactory;
		}

		protected void buildFromFile(File file) throws BuilderException {
			try {
				XmlSqlSetBuilder builder = new XmlSqlSetBuilder(new FileInputStream(file), sqlQueryFactory.getConfiguration(), file.toURI().toString(), null);
				builder.parse();
			} catch (IOException e) {
				//throw new BuilderException(LogLocalizer.format("003050", file.getPath()), e);
			}
		}

		public void onDirectoryChange(File directory) {		
			throw new UnsupportedOperationException();	
		}

		public void onDirectoryCreate(File directory) {		
			throw new UnsupportedOperationException();	
		}

		public void onDirectoryDelete(File directory) {
			throw new UnsupportedOperationException();	
		}

		public void onFileChange(File file) {
			if(log.isDebugEnabled())
				log.debug("change {}", file.toURI());
			buildFromFile(file);
		}

		public void onFileCreate(File file) {
			if(log.isDebugEnabled())
				log.debug("new {}", file.toURI());
			buildFromFile(file);
		}

		public void onFileDelete(File file) {	
			if(log.isDebugEnabled())
				log.debug("remove {}", file.toURI());

			if (sqlQueryFactory.getConfiguration().isResourceLoaded(file.toURI().toString())) {
				sqlQueryFactory.getConfiguration().removeLoadedResource(file.toURI().toString());
			}
		}		
		

		public void onStart(FileAlterationObserver observer) {			
			if(log.isDebugEnabled())
				log.debug("start {}", observer.getDirectory().toURI()); 
		}				
		
		public void onStop(FileAlterationObserver observer) {	
			if(log.isDebugEnabled())
				log.debug("stop {}", observer.getDirectory().toURI()); 
		}		
		
	}
	
	private static final int DEFAULT_POOL_INTERVAL_MILLIS = 10000;
	
	private int pollIntervalMillis = DEFAULT_POOL_INTERVAL_MILLIS;
	
	private SqlQueryFactory sqlQueryFactory;
	
	private FileAlterationMonitor monitor; 
	
	private String directory;

	private Repository repository;

	public DirectoryScanner(SqlQueryFactory sqlQueryFactory, Repository repository) {
		this.sqlQueryFactory = sqlQueryFactory;
		this.repository = repository;
	}
	
	protected void buildFromDirectory(File file) throws BuilderException {
		
		if( !file.isDirectory() ) {
			//log.warn( LogLocalizer.format("003051", file.getPath()));
			return ;
		}
		
		for( File f : FileUtils.listFiles(file, FileFilterUtils.suffixFileFilter(sqlQueryFactory.getConfiguration().getSuffix()), FileFilterUtils.trueFileFilter())){			
			if( !sqlQueryFactory.getConfiguration().isResourceLoaded(f.toURI().toString())){
				//log.debug(LogLocalizer.format("003052", file.toURI()));
				try {					
					XmlSqlSetBuilder builder = new XmlSqlSetBuilder(new FileInputStream(f), sqlQueryFactory.getConfiguration(), f.toURI().toString(), null);
					builder.parse();
				} catch (IOException e) {
					//throw new BuilderException(LogLocalizer.format("003050", file.getPath()), e);
				}	
			}
		}
	}

	public void destroy() throws Exception {	
		
		if( monitor != null)
		{
			//log.debug(LogLocalizer.getMessage("003053"));
			monitor.stop();
			//log.debug(LogLocalizer.getMessage("003054"));
		}
	}

	public String getDirectory() {
		return directory;
	}

	public int getPollIntervalMillis() {
		return pollIntervalMillis;
	}

	public SqlQueryFactory getSqlQueryFactory() {
		return sqlQueryFactory;
	}

	public void initialize() {				
				
		File directoryFile = null ;		
		try{
		directoryFile = repository.getFile("sql");
		}catch(IOException e){
			log.error(e.getMessage(), e);
		}

		//log.debug(LogLocalizer.format("003056", directoryFile));
		if( directoryFile != null)
			try {
				buildFromDirectory(directoryFile);
				start(directoryFile);
			} catch (Exception e) {
				throw new ConfigurationError(e.getMessage(), e);
			}				
	}
	
	public void setDirectory(String directory) {
		this.directory = directory;
	}
	
	public void setPollIntervalMillis(int pollIntervalMillis) {
		this.pollIntervalMillis = pollIntervalMillis;
	}
		
	public void setSqlQueryFactory(SqlQueryFactory sqlQueryFactory) {
		this.sqlQueryFactory = sqlQueryFactory;
	}
	
	public void start(File file ) throws Exception {
		//log.debug(LogLocalizer.format("003057", file.getAbsolutePath() ));
		if( monitor == null)
		{			
			monitor = new FileAlterationMonitor(pollIntervalMillis);	
			FileAlterationObserver observer = new FileAlterationObserver(file, FileFilterUtils.suffixFileFilter(sqlQueryFactory.getConfiguration().getSuffix()));
			observer.addListener(new DirectoryListener(sqlQueryFactory));
			monitor.addObserver(observer);			
		}
		monitor.start();
		//log.debug(LogLocalizer.format("003058", file.getAbsolutePath()) );
	}
	
}
