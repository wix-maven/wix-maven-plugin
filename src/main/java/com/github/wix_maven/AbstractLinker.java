package com.github.wix_maven;

/*
 * #%L
 * WiX Toolset (Windows Installer XML) Maven Plugin
 * %%
 * Copyright (C) 2013 - 2014 GregDomjan NetIQ
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

public abstract class AbstractLinker extends AbstractPackageable {

	public static <T extends Comparable<? super T>> List<T> asSortedList(
			Collection<T> c) {
			  List<T> list = new ArrayList<T>(c);
			  java.util.Collections.sort(list);
			  return list;
			}

	/**
	 * Properties catch all in case we missed some configuration. Passed directly to light or lit
	 */
	@Parameter
	private Properties linkProperties;

	/**
	 * The set of files/patterns to include. <br>
	 * If not set Defaults to "**\/arch\/*.wixlib", "**\/arch\/*.wixobj"
	 */
	@Parameter
	private Set<String> intIncludes = new HashSet<String>();
	
	/**
	 * A list of exclusion filters. See intIncludes.
	 */
	@Parameter
	private Set<String> intExcludes = new HashSet<String>();

	/**
	 * The directory to scan for localisation (.wxl) files.
	 * For Light the sub culture is added as a -b path
	 * For Light and Lit added as a -b source path
	 */
	@Parameter(property = "wix.wxlInputDirectory", defaultValue = "${project.basedir}/src/main/wix-locale", required=true)
	protected File wxlInputDirectory;

	/**
	 * The directory to scan for wix files.
	 * For each build type there is at least one wxs file required
	 */
	@Parameter(property = "wix.resourceDirectory", defaultValue = "${project.basedir}/src/main/wix-resource", required=true)
	protected File resourceDirectory;
	
	/**
	 * The set of files/patterns to include. <br>
	 * If not set Defaults to "**\/*.wxl"
	 * Starting from wxlInputDirectory 
	 * - For light it is applied to the culture specific folder inside, 
	 * - For lit it applies to all cultures. 
	 */
	@Parameter
	private Set<String> wxlIncludes = new HashSet<String>();
	
	/**
	 * A list of exclusion filters. See wxlIncludes
	 */
	@Parameter
	private Set<String> wxlExcludes = new HashSet<String>();

	/**
	 * Specify a base paths to locate all files. (-b option)
	 * By default each of the following folders will be added if it exists 
	 *  <li>wxsInputDirectory 
	 *  <li>narUnpackDirectory
	 *  <li>resourceDirectory
	 */
	@Parameter
	protected Set<String> fileSourceRoots = new HashSet<String>();
	
	/**
	 * A pool of executors running linkers.
	 */
	//private ExecutorService exService;
	
	public final Set<String> getIncludes() {
		if (intIncludes.isEmpty()) {
			intIncludes.add("**/*.wixlib");
			intIncludes.add("**/*.wixobj");
		}
		return intIncludes;
	}

	public final Set<String> getExcludes() {
		return intExcludes;
	}

	public final Set<String> getLocaleIncludes() {
		if (wxlIncludes.isEmpty()) {
			wxlIncludes.add("**/*.wxl");
		}
		return wxlIncludes;
	}

	public final Set<String> getLocaleExcludes() {
		return wxlExcludes;
	}
	
	protected void addOptions(Commandline cl, Set<String> allFileSourceRoots) throws MojoExecutionException {
// defensive assert(allFileSourceRoots != null)
		if( wxsInputDirectory.exists() )
			allFileSourceRoots.add(wxsInputDirectory.getAbsolutePath());

		if( resourceDirectory.exists() )
			allFileSourceRoots.add(resourceDirectory.getAbsolutePath());

		if( narUnpackDirectory.exists() )
			allFileSourceRoots.add(narUnpackDirectory.getAbsolutePath());

		if( unpackDirectory.exists() )
			allFileSourceRoots.add(unpackDirectory.getAbsolutePath());

		allFileSourceRoots.add(localRepository.getBasedir());
		
		for (Iterator<String> i = allFileSourceRoots.iterator(); i.hasNext();) {
			//  cannot contain a quote. Quotes are often accidentally introduced when trying to refer to a directory path with spaces in it, such as "C:\Out Directory" or "C:\Out Directory\".  The correct representation for that path is: "C:\Out Directory\\"
			cl.addArguments(new String[] { "-b", i.next() + "\\" });//.getPath()
		}
		 
		// undocumented command line used by votive?
		// add in arch/culture path segments, is this intermediate or output...
		// if( optionsFile!= null && optionsFile.exists() )
		// cl.addArguments( new String[] { "-contentsfile", "BindContentsFileListen" } );
		// if( optionsFile!= null && optionsFile.exists() )
		// cl.addArguments( new String[] { "-outputsfile", "BindOutputsFileListen" } );
		// if( optionsFile!= null && optionsFile.exists() )
		// cl.addArguments( new String[] { "-builtoutputsfile", "BindBuiltOutputsFileListen" } );

		// -wixprojectfile
		// C:\Data\dev\prj\sso\SecureLogin-8-0-0\SecureLogin\assemblies\MSI-SL-32\SecureLogin-32.wixproj
	}

	protected void addOtherOptions(Commandline cl) {
		if (linkProperties != null && !linkProperties.isEmpty()) {
			ArrayList<String> result = new ArrayList<String>();

			for (Enumeration<Object> keys = linkProperties.keys(); keys.hasMoreElements();) {
				String key = (String) keys.nextElement();
				if (key.startsWith("x--"))
					key = key.substring(2);
				result.add(key);
				String value = linkProperties.getProperty(key);
				if (null != value) {
					result.add(value);
				}
			}

			cl.addArguments(result.toArray(new String[0]));
		}
	}

	protected void link(Commandline cl) throws MojoExecutionException {
		if (verbose) {
			getLog().info(cl.toString());
		} else {
			getLog().debug(cl.toString());
		}
		
		try {


			// TODO: maybe should report or do something with return value.
			int returnValue = CommandLineUtils.executeCommandLine(cl, new StreamConsumer() {

				public void consumeLine(final String line) {
					if (line.contains(" : error ")) {
						getLog().error(line);
					} else if (line.contains(" : warning ")) {
						getLog().warn(line);
					} else if (verbose) {
						getLog().info(line);
					} else {
						getLog().debug(line);
					}
				}

			}, new StreamConsumer() {

				public void consumeLine(final String line) {
					getLog().error(line);
				}

			});

			if (returnValue != 0) {
				throw new MojoExecutionException("Problem executing linker, return code " + returnValue );
			}
		} catch (CommandLineException e) {
			throw new MojoExecutionException("Problem executing linker", e);
		} finally {
			getLog().info(cl.toString());
		}
	}

	public void execute() throws MojoExecutionException {

		if ( skip )
		{
			if( verbose )
				getLog().info( getClass().getName() + " skipped" );
			return;
		}

		if (outputDirectory != null)
			outputDirectory.mkdirs();

//		unpackFileBasedResources();
//		final int noOfTh = 4;
//		exService = Executors.newFixedThreadPool(noOfTh);
		multilink(toolDirectory);

//		if (!extendedUse)
//			cleanupFileBasedResources();
	}

	protected abstract void multilink(File toolDirectory) throws MojoExecutionException;
}