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
import java.io.IOException;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/***
 * Generates WiX authoring from various input formats.
 * 
 * Every time heat is run it regenerates the output file and any changes are lost.
 * heat.exe [-?] harvestType &lt;harvester arguments&gt; -out sourceFile.wxs
 */
@Mojo( name = "harvest", defaultPhase=LifecyclePhase.GENERATE_SOURCES )
public class HarvestMojo extends AbstractPackageable {
// Heat seems a bit dirty, requires the following to be installed side by side with Heat.exe
// 
//	<dependency>
//		<groupId>${wix.groupId}</groupId>
//		<artifactId>wix-toolset</artifactId>
//		<version>${wix.version}</version>
//		<classifier>WixIISExtension</classifier>
//		<scope>provided</scope>
//		<type>wixext</type>
//	</dependency>
//	<dependency>
//		<groupId>${wix.groupId}</groupId>
//		<artifactId>wix-toolset</artifactId>
//		<version>${wix.version}</version>
//		<classifier>WixUtilExtension</classifier>
//		<scope>provided</scope>
//		<type>wixext</type>
//	</dependency>
//	<dependency>
//		<groupId>${wix.groupId}</groupId>
//		<artifactId>wix-toolset</artifactId>
//		<version>${wix.version}</version>
//		<classifier>WixVSExtension</classifier>
//		<scope>provided</scope>
//		<type>wixext</type>
//	</dependency>
	
	/**
	 * Heat supports the harvesting types:
	 * 
	 * <table summary="">
	 *   <tr><th>Harvest Type</th><th>Meaning</th></tr>
	 *   <tr><td>dir</td><td>Harvest a directory.</td></tr>
	 *   <tr><td>file</td><td>Harvest a file.</td></tr>
	 *   <tr><td>project</td><td>Harvest outputs of a Visual Studio project.</td></tr>
	 *   <tr><td>website</td><td>Harvest an IIS web site.</td></tr>
	 *   <tr><td>perf</td><td>Harvest performance counters from a category.</td></tr>
	 *   <tr><td>reg</td><td>Harvest registy information from a reg file.</td></tr>
	 * </table>
	 */
	@Parameter
	String havestType;
	public final String HT_DIR="dir";
	public final String HT_FILE="file";
	public final String HT_PROJECT="project";
	public final String HT_WEBSITE="website";
	public final String HT_PERFORMANCE="perf";
	public final String HT_REGISTRY="reg";
	
	// -generate Specify what elements to generate, one of: components, container, payloadgroup, layout (default is components)

	public void multiHeat(File heatTool) throws MojoExecutionException, MojoFailureException {

		defaultLocale();

		for (String arch : getPlatforms()) {
// TODO: validate all?			for (String culture : culturespecs()) {
			String culture = baseCulturespec();
			{
				File archOutputFile = getOutput(arch, culture, outputExtension());

				getLog().info(" -- Heat harvesting : " + archOutputFile.getPath());
				
				Commandline cl = new Commandline();

				cl.setExecutable(heatTool.getAbsolutePath());
				cl.setWorkingDirectory(relativeBase);
				addToolsetGeneralOptions(cl);
			}
		}
	}


	protected void heat(Commandline cl) throws MojoExecutionException {
		try {
			if (verbose) {
				getLog().info(cl.toString());
			} else {
				getLog().debug(cl.toString());
			}

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
				throw new MojoExecutionException("Problem executing heat, return code " + returnValue + "\nFailed execution of " + cl.toString());
			}
		} catch (CommandLineException e) {
			throw new MojoExecutionException("Problem executing heat\nFailed execution of " + cl.toString(), e);
		}
	}

	public void execute() throws MojoExecutionException, MojoFailureException {

		if ( skip )
		{
			if( verbose )
				getLog().info( getClass().getName() + " skipped" );
			return;
		}

		File heatTool = new File(toolDirectory, "bin/heat.exe");
		if (!heatTool.exists())
			throw new MojoExecutionException("Heat tool doesn't exist " + heatTool.getAbsolutePath());

// Heat requires side by side install of wixext even if unused? 
		Set<Artifact> dependentExtensions = getExtDependencySets();
		File toolDir = new File(toolDirectory, "bin");
		getLog().info( "Preparing heat tool with WixIISExtension, WixUtilExtension, WixVSExtension" );
		for (Artifact ext : dependentExtensions) {
			if( "WixIISExtension".equalsIgnoreCase(ext.getArtifactId())
				|| "WixUtilExtension".equalsIgnoreCase(ext.getArtifactId())
				|| "WixVSExtension".equalsIgnoreCase(ext.getArtifactId()) )
			getLog().info(ext.getFile().getName());
			try {
				FileUtils.copyFileToDirectoryIfModified( ext.getFile(), toolDir );
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				// TODO: resolve if we should raise this as a failure/excution exception or just ignore.
				//throw new MojoExecutionException( "NAR: could not copy include files", e );
			}
		}

		multiHeat(heatTool);
	}

	/**
	 * Translate packaging type into output filename extension
	 * @return output filename extension
	 */
	private String outputExtension() {
		return getPackaging();
	}

}
