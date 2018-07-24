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
import java.io.FileFilter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/***
 * Generates WiX authoring from various input formats.
 * 
 * Every time heat is run it regenerates the output file and any changes are lost.
 * heat.exe [-?] harvestType &lt;harvester arguments&gt; -out sourceFile.wxs
 * 
 * Options:
   -ag      autogenerate component guids at compile time
   -cg <ComponentGroupName>  component group name (cannot contain spaces e.g -cg MyComponentGroup)
   -configuration  configuration to set when harvesting the project
   -directoryid  overridden directory id for generated directory elements
   -dr <DirectoryName>  directory reference to root directories (cannot contain spaces e.g. -dr MyAppDirRef)
   -ext     <extension>  extension assembly or "class, assembly"
   -g1      generated guids are not in brackets
   -generate
            specify what elements to generate, one of:
                components, container, payloadgroup, layout, packagegroup
                (default is components)
   -gg      generate guids now
   -indent <N>  indentation multiple (overrides default of 4)
   -ke      keep empty directories
   -nologo  skip printing heat logo information
   -out     specify output file (default: write to current directory)
   -platform  platform to set when harvesting the project
   -pog
            specify output group of VS project, one of:
                Binaries,Symbols,Documents,Satellites,Sources,Content
              This option may be repeated for multiple output groups.
   -projectname  overridden project name to use in variables
   -scom    suppress COM elements
   -sfrag   suppress fragments
   -srd     suppress harvesting the root directory as an element
   -sreg    suppress registry harvesting
   -suid    suppress unique identifiers for files, components, & directories
   -svb6    suppress VB6 COM elements
   -sw<N>   suppress all warnings or a specific message ID
            (example: -sw1011 -sw1012)
   -swall   suppress all warnings (deprecated)
   -t       transform harvested output with XSL file
   -template  use template, one of: fragment,module,product
   -v       verbose output
   -var <VariableName>  substitute File/@Source="SourceDir" with a preprocessor or a wix variable
(e.g. -var var.MySource will become File/@Source="$(var.MySource)\myfile.txt" and
-var wix.MySource will become File/@Source="!(wix.MySource)\myfile.txt"
   -wixvar  generate binder variables instead of preprocessor variables
   -wx[N]   treat all warnings or a specific message ID as an error
            (example: -wx1011 -wx1012)
   -wxall   treat all warnings as errors (deprecated)

For more information see: http://wixtoolset.org/
 */
@Mojo( name = "harvest", defaultPhase=LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution=ResolutionScope.COMPILE )
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
	String harvestType;
	public final static String HT_DIR="dir";
	public final static String HT_FILE="file";
	public final static String HT_PROJECT="project";
	public final static String HT_WEBSITE="website";
	public final static String HT_PERFORMANCE="perf";
	public final static String HT_REGISTRY="reg";

	/**
	 * Component group name (cannot contain spaces e.g MyComponentGroup).
	 **/
	@Parameter (defaultValue="")
	String harvestComponentGroupName;

	/**
	 * Overridden directory id for generated directory elements.
	 **/
	@Parameter (defaultValue="")
	String harvestDirectoryid;
	
	/**
	 * Directory reference to root directories (cannot contains spaces e.g. MyAppDirRef).
	 **/
	@Parameter (defaultValue="")
	String harvestDirectoryRef;
	
	/**
	 * Plugin will provide reference to files based on generated id, or you can override with your own variable.
	 * Substitute File/@Source="SourceDir" with a preprocessor or a wix variable (e.g. -var var.MySource will become File/@Source="$(var.MySource)\myfile.txt" and -var wix.MySource will become File/@Source="!(wix.MySource)\myfile.txt".
	 **/
	@Parameter (defaultValue="")
	String harvestSourceVar;
	
	/**
	 * Specify what elements to generate, one of: 
	 * <li>components, 
	 * <li>container, 
	 * <li>payloadgroup, 
	 * <li>layout
	 **/
	@Parameter (defaultValue="components")
	String harvestGenerate;
	
	/**
	 * Generate Component GUID now during heat (true) or later during link (false) 
	 */
	@Parameter (defaultValue="false")
	protected boolean generateComponentGUIDs;

	/**
	 * Generate component guids curly braces. 
	 */
	@Parameter (defaultValue="true")
	protected boolean generateGUIDBrackets;

	/**
	 * Generate binder variables (true) instead of preprocessor variables (false). 
	 */
	@Parameter (defaultValue="false")
	protected boolean generateBinderVariables;

	/**
	 * Keep empty directories. 
	 */
	@Parameter (defaultValue="false")
	protected boolean harvestKeepEmpty;
	
	/**
	 * Generate binder variables (true) instead of preprocessor variables (false). 
	 */
	@Parameter
	protected Set<String> suppress;
	
	/**
	 * Use template, one of: fragment, module, product.
	 */
	@Parameter(defaultValue="fragment")
	protected String harvestTemplate;
//	public final String HT_FRAGMENT="fragment";
//	public final String HT_MODULE="module";
//	public final String HT_PRODUCT="product";

	/**
	 * Use template, one of: fragment, module, product.
	 */
	@Parameter(defaultValue="")
	protected String harvestVariableName;

	// -generate Specify what elements to generate, one of: components, container, payloadgroup, layout (default is components)
// heat dir target/appassembler/lib -gg -ke -cg Lib -sfrag -dr LibDir -srd -out target/wix/Lib.wxs
// heat dir ${build.dir}/msi/files -o ${build.dir}/msi/build/payload.wxs  
	

	protected void addToolsetOptions(Commandline cl) {
		if (generateComponentGUIDs)
			cl.addArguments(new String[] { "-gg" });
		else
			cl.addArguments(new String[] { "-ag" });

		if (generateGUIDBrackets)
			cl.addArguments(new String[] { "-g1" });

		if (harvestKeepEmpty)
			cl.addArguments(new String[] { "-ke" });

		// warning HEAT1108 : The command line switch 'template:' is deprecated. Please use 'template' instead
		// many examples show "-template:fragment"  however it's standardised since ?? to "-template" "fragment"
		cl.addArguments(new String[] { "-template", harvestTemplate });
		cl.addArguments(new String[] { "-generate", harvestGenerate });
		
		if( StringUtils.isNotEmpty(harvestDirectoryRef) )
			cl.addArguments(new String[] { "-dr", harvestDirectoryRef });
		if( StringUtils.isNotEmpty(harvestDirectoryid) )
			cl.addArguments(new String[] { "-directoryid", harvestDirectoryid });
		
		for (String sup : suppress) {
			cl.addArguments(new String[] { "-s"+sup });
		}
		
		if( generateBinderVariables )
			cl.addArguments(new String[] { "-wixvar" });
	}
	
	public void multiHeat(File heatTool, String harvestType, File harvest) throws MojoExecutionException, MojoFailureException {

		getLog().info("Harvesting " + harvestType + " input " + harvest.getPath());
//		
//		{ Project ??
//		defaultLocale();
//			String culture = baseCulturespec();
//				File archOutputFile = getOutput(arch, culture, outputExtension());
//
//				getLog().info(" -- Heat harvesting : " + archOutputFile.getPath());
//			}				
				Commandline cl = new Commandline();

				cl.setExecutable(heatTool.getAbsolutePath());
				cl.setWorkingDirectory(relativeBase);
				
				cl.addArguments(new String[] { harvestType, harvest.getAbsolutePath() });

				addToolsetGeneralOptions(cl);
				addToolsetOptions(cl);

				cl.addArguments(new String[] { "-cg", StringUtils.isNotEmpty(harvestComponentGroupName) ? harvestComponentGroupName : getHarvestID(harvestType, harvest) });
				cl.addArguments(new String[] { "-var", StringUtils.isNotEmpty(harvestSourceVar)? harvestSourceVar : "var."+getHarvestID(harvestType, harvest) });
				
				File target = new File( wxsGeneratedDirectory, getHarvestID(harvestType, harvest)+".wxs");
				cl.addArguments(new String[] { "-out", target.getAbsolutePath() });
				
				heat(cl);
//		}
	}

	private String getHarvestID(String harvestType, File harvest) {
		return harvestType+"-"+harvest.getName();
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
		if(! harvestInputDirectory.exists()) {
			if( verbose )
				getLog().info( "Skipping executing heat\nInput doesn't exist " + harvestInputDirectory.getAbsolutePath() );
			return;
		}

		File heatTool = new File(toolDirectory, "bin/heat.exe");
		if (!heatTool.exists())
			throw new MojoExecutionException("Heat tool doesn't exist " + heatTool.getAbsolutePath());

// Heat requires side by side install of wixext even if unused? 
		Set<Artifact> dependentExtensions = getExtDependencySets();
		File toolDir = new File(toolDirectory, "bin");
		getLog().info( "Preparing heat tool with WixIISExtension, WixUtilExtension, WixVSExtension from " + dependentExtensions.size() );
		
		for (Artifact ext : dependentExtensions) {
			getLog().debug(String.format("Extension artifact %1$s:%2$s:%3$s found", ext.getGroupId(), ext.getArtifactId(), ext.getClassifier()));
			if( "WixIISExtension".equalsIgnoreCase(ext.getClassifier())
				|| "WixUtilExtension".equalsIgnoreCase(ext.getClassifier())
				|| "WixVSExtension".equalsIgnoreCase(ext.getClassifier()) ) {
				getLog().info(ext.getFile().getName());
				try {
					FileUtils.copyFileIfModified(ext.getFile(), new File(toolDir,ext.getClassifier()+".dll") );
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					// TODO: resolve if we should raise this as a failure/excution exception or just ignore.
					//throw new MojoExecutionException( "NAR: could not copy include files", e );
				}
			}
		}
		if(! new File(toolDir,"WixIISExtension.dll").exists()) {
			throw new MojoExecutionException("Problem executing heat\nUnable to find dependent extension WixIISExtension" );
		}
		if(! new File(toolDir,"WixUtilExtension.dll").exists()) {
			throw new MojoExecutionException("Problem executing heat\nUnable to find dependent extension WixUtilExtension" );
		}
		if(! new File(toolDir,"WixVSExtension.dll").exists()) {
			throw new MojoExecutionException("Problem executing heat\nUnable to find dependent extension WixVSExtension" );
		}
				

		if( !wxsGeneratedDirectory.exists() )
			wxsGeneratedDirectory.mkdirs();
		
		if( StringUtils.isNotEmpty(harvestType) ){
			multiHeat(heatTool, harvestType, harvestInputDirectory);
		} else {

			FileFilter directoryFilter = new FileFilter() {
				public boolean accept(File file) {
					return file.isDirectory();
				}
			};
			getLog().info("Harvesting inputs from " + harvestInputDirectory.getPath());

			for (File folders : harvestInputDirectory.listFiles(directoryFilter)) {
				if( HT_DIR.equals(folders.getName()) ) {
					for (File subfolder: folders.listFiles(directoryFilter) ){
						multiHeat(heatTool, HT_DIR, subfolder);
					}
				} else if( HT_FILE.equals(folders.getName()) ) {
//					for (File subfolder: folders.listFiles(fileFilter) ){
//						multiHeat(heatTool, HT_FILE, subfolder);
//					}
				}
			}
		}
	}

	/**
	 * Translate packaging type into output filename extension
	 * @return output filename extension
	 */
	private String outputExtension() {
		return getPackaging();
	}

}
