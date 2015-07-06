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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.compiler.util.scan.*;
import org.codehaus.plexus.compiler.util.scan.mapping.*;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

/**
 * Goal which executes WiX candle to create a .wixobj file.
 * 
 * The following project dependency inclusion patterns apply<br> 
 * Dependent NAR project 'Foo' with possible nar output redefined as 'bar' <br>
 * <li> -dFoo.TargetDir=Foo-version\ 
 * <li> -dFoo.TargetExt=.wixlib 
 * <li> -dFoo.TargetFileName=bar.type 
 * <li> -dFoo.TargetName=bar
 * 
 * @goal candle
 * @phase package
 * @requiresProject true
 * @requiresDependencyResolution compile
 */
public class CandleMojo extends AbstractWixMojo {
	/**
	 * Definitions (pre) Compilation (-d option)
	 * 
	 * @parameter
	 */
	private Set<String> definitions = new HashSet<String>();
	private Set<String> definitionsArch = new HashSet<String>();

	/**
	 * Include paths (-I option)
	 * 
	 * @parameter
	 */
	private String[] includePaths;

	/**
	 * The granularity in milliseconds of the last modification date for testing whether a source needs re-compilation
	 * 
	 * @parameter default-value="1000"
	 * @required
	 */
	protected int staleMillis;
	/**
	 * Set this value if you wish to have a single timestamp file to track changes rather than cxx,hxx comparison The time-stamp file for the
	 * processed xsd files.
	 * 
	 * @parameter
	 */
	protected String timestampFile = null;

	/**
	 * The directory to store the time-stamp file for the processed aid files. Defaults to outputDirectory. Only used with xsdTimestampFile being set.
	 * 
	 * @parameter default-value="${project.build.directory}/mapping/cpp"
	 */
	protected File timestampDirectory;

	/**
	 * The set of files/patterns to include Defaults to "**\/*.wxs"
	 * 
	 * @parameter
	 */
	private Set<String> includes = new HashSet<String>();
	/**
	 * A list of exclusion filters.
	 * 
	 * @parameter
	 */
	private Set<String> excludes = new HashSet<String>();

	/**
	 * Properties catch all in case we missed some configuration. Passed directly to candle
	 * 
	 * @parameter
	 */
	private Properties candleProperties;

	public final Set<String> getIncludes() {
		if (includes.isEmpty()) {
			includes.add("**/*.wxs");
		}
		return includes;
	}

	public final Set<String> getExcludes() {
		return excludes;
	}

	protected void addOtherOptions(Commandline cl) {
		if (candleProperties != null && !candleProperties.isEmpty()) {
			ArrayList<String> result = new ArrayList<String>();

			for (Enumeration<Object> keys = candleProperties.keys(); keys.hasMoreElements();) {
				String key = (String) keys.nextElement();
				result.add(key);
				String value = candleProperties.getProperty(key);
				if (null != value) {
					result.add(value);
				}
			}

			cl.addArguments(result.toArray(new String[0]));
		}
	}

	private void addOptions(Commandline cl, String arch) {
		// note: cl tool will add quotes if necessary - adding \" in an arg will break it.
		
		if (definitionsArch != null) {
			for (String def : definitionsArch) {
				cl.addArguments(new String[] { "-d" + def  });
			}
		}
		if (definitions != null) {
			for (String def : definitions) {
				cl.addArguments(new String[] { "-d" + def  });
			}
		}

		// TODO: shorten commandline, use relative paths where possible
		cl.addArguments(new String[] { "-dConfiguration=Release" });
		String intOutDir = getArchIntDirectory(arch).getAbsolutePath() + "\\";
		cl.addArguments(new String[] { "-out", intOutDir, "-dOutDir=" + intDirectory.getAbsolutePath() + "\\" });  // VS OutDir doesn't include arch
		cl.addArguments(new String[] { "-arch", arch, "-dPlatform=" + arch });
		cl.addArguments(new String[] { "-dProjectDir=" + project.getBasedir().getAbsolutePath() + "\\" });
		// -dProjectExt=.wixproj -dProjectFileName=Baz.wixproj
		// -dProjectName=Baz
		// -dProjectPath=C:\Baz\SecureLogin-32.wixproj
		cl.addArguments(new String[] { "-dProjectId=" + project.getArtifactId(), "-dProjectName=" + project.getName() });

		// -dTargetDir=C:\Baz\target\Release\
		// -dTargetExt=.msi 
		// -dTargetFileName=Bez.msi
		// -dTargetName=Bez
		// -dTargetPath=C:\Baz\target\Release\Bez.msi

// TODO: "Solution" values, or should we change this for maven build to artifacts... 		
//		if( project.getParent() != null ){ // direct parent, or top most parent, or aggregate parent?... not a great match to VS Solution.
//			cl.addArguments(new String[] { "-dSolutionName=" + project.getParent().getArtifactId() });
		// -dSolutionDir=\assemblies
		// -dSolutionExt=.sln 
		// -dSolutionFileName=Assemblies.sln
		// -dSolutionName=Assemblies
		// -dSolutionPath=\assemblies\Assemblies.sln
//		}

		if (includePaths != null) {
			for (String incPath : includePaths) {
				cl.addArguments(new String[] { "-I" + incPath });
			}
		}
	}
	
	protected void compile(Set<String> files, File toolDirectory, String arch) throws MojoExecutionException {

		Commandline cl = new Commandline();

		cl.setExecutable(new File(toolDirectory, "bin/candle.exe").getAbsolutePath());
		cl.setWorkingDirectory(relativeBase);
		addToolsetGeneralOptions(cl);

		addWixExtensions(cl);
		addOptions(cl, arch);
		addOtherOptions(cl);

		cl.addArguments(files.toArray(new String[0]));

		try {
			if (verbose) {
				getLog().info(cl.toString());
			} else {
				getLog().debug(cl.toString());
			}

			// TODO: maybe should report or do something with return value.
			int returnValue = CommandLineUtils.executeCommandLine(cl, new StreamConsumer() {

				public void consumeLine(final String line) {
					if (line.contains(") : error ")) {
						getLog().error(line);
					} else if (line.contains(") : warning ")) {
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
				throw new MojoExecutionException("Problem executing compiler, return code " + returnValue + "\nFailed execution of " + cl.toString());
			}
		} catch (CommandLineException e) {
			throw new MojoExecutionException("Problem executing compiler candle \nFailed execution of " + cl.toString(), e);
		}
	}

	public void execute() throws MojoExecutionException {

		if ( skip )
		{
			getLog().info( getClass().getName() + " skipped" );
			return;
		}

		if (!wxsInputDirectory.exists()) {
			return;
		}

		addWixDefines();
		addNARDefines();
		addJARDefines();
		addNPANDAYDefines();
		
		for (String arch : getPlatforms() ) {

			getArchIntDirectory(arch).mkdirs();

			definitionsArch.clear();
			addNARArchDefines(arch);
			// and intel... and... 
			try {

				// TODO: there is a limitation here - if you change config options in pom, then we don't check to see if the file is older than the new config...
				SourceInclusionScanner scanner = new StaleSourceScanner(staleMillis, getIncludes(), getExcludes());
				if (timestampFile != null && timestampDirectory != null) {
					getLog().debug("Using timestamp file tracking for sources");
					// if( !xsdTimestampDirectory.exists() ||
					// xsdTimestampFile.isEmpty() ) tracking isn't going to work,
					// always rebuild - warning?

					scanner.addSourceMapping(new SingleTargetSourceMapping(".wxs", timestampFile));
				} else {
					Set<String> fileExts = new HashSet<String>();
					fileExts.add(".wixobj");
					timestampDirectory = getArchIntDirectory(arch);
					scanner.addSourceMapping(new SuffixMapping(".wxs", fileExts));
				}

				if (!timestampDirectory.exists())
					timestampDirectory.mkdirs();

				Set<File> wixSources = scanner.getIncludedSources(wxsInputDirectory, timestampDirectory);

				if (wixSources.isEmpty()) {
					getLog().info("All objects appear up to date");
				} else {
					Set<String> files = new HashSet<String>();
					for (Iterator<File> i = wixSources.iterator(); i.hasNext();) {
						files.add(getRelative( i.next() ) );
					}

					compile(files, toolDirectory, arch);

					if (timestampFile != null && timestampDirectory != null) {
						File timeStamp = new File(timestampDirectory, timestampFile);
						if (!timeStamp.exists())
							try {
								timeStamp.createNewFile();
							} catch (IOException e) {
								getLog().warn("XSD: Unable to touch timestamp file");
							}
						else if (!timeStamp.setLastModified(System.currentTimeMillis()))
							getLog().warn("XSD: Unable to touch timestamp file");
					}
				}

				// project.addCompileSourceRoot(
				// outputDirectory.getAbsolutePath() );
				// updateProject( );
			} catch (InclusionScanException e) {
				throw new MojoExecutionException("XSD: scanning for updated files failed", e);
			}
		}
//		if (!extendedUse)
//			cleanupFileBasedResources();

	}

	private void addNARArchDefines(String arch) {
		if( "x86".equals(arch) ){
			definitionsArch.add("IsWin64=no");
			definitionsArch.add("narDir.dll=x86-Windows-msvc-shared/lib/x86-Windows-msvc/shared");
			definitionsArch.add("narDir.exe=x86-Windows-msvc-executable/bin/x86-Windows-msvc");
		} else {
			definitionsArch.add("IsWin64=yes");
			definitionsArch.add("narDir.dll=amd64-Windows-msvc-shared/lib/amd64-Windows-msvc/shared");
			definitionsArch.add("narDir.exe=amd64-Windows-msvc-executable/bin/amd64-Windows-msvc");
		}
	}

	protected void addDefinition(String def ) {
		definitions.add(def);
	}

	protected Set<Artifact> getNARDependencySets() throws MojoExecutionException {
		FilterArtifacts filter = new FilterArtifacts();
// Cannot do this filter in maven3 as it blocks classifiers - works in maven 2. 
//		filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), true));
		filter.addFilter(new TypeFilter("nar", ""));

		// start with all artifacts.
		@SuppressWarnings("unchecked")
		Set<Artifact> artifacts = project.getArtifacts();

		// perform filtering
		try {
			artifacts = filter.filter(artifacts);
		} catch (ArtifactFilterException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}

		return artifacts;
	}

	protected void addWixDefines() throws MojoExecutionException {
		Set<Artifact> wixArtifacts = getWixDependencySets();
		getLog().info( "Adding "+wixArtifacts.size()+" dependent msm/msi/msp/wixlib/bundle" );
		if( !wixArtifacts.isEmpty() ){
			for(Artifact wix : wixArtifacts){
				if( verbose )
					getLog().info( String.format("WIX added dependency %1$s", wix.getArtifactId() ) );
				else 
					getLog().debug( String.format("WIX added dependency %1$s", wix.getArtifactId() ) );
				
				if( ! wix.hasClassifier() ){
					File resUnpackDirectory = wixUnpackDirectory(wix);
					// if( resUnpackDirectory.exists() )  pending move of unpack to earlier phase, currently run after this goal.
					{
						addDefinition(String.format("%1$s.%2$s.UnpackPath=%3$s", wix.getGroupId(), wix.getArtifactId(), defineWixUnpackFile( resUnpackDirectory ) ) );
					}
				}

				// TODO: resolve source platform issues - msm/msi don't have non classified artifacts.
//				if( PACK_MERGE.equalsIgnoreCase( wix.getType() ) ){
//					definitions.add(String.format("%1$s.TargetDir-%3$s=%2$s", wix.getArtifactId(), wix.getFile().getParentFile().getAbsolutePath() ));
//					definitions.add(String.format("%1$s.TargetName-%3$s=%2$s", wix.getArtifactId(), wix.getFile().getName() ));
//					definitions.add(String.format("%1$s.TargetPath-%3$s=%2$s", wix.getArtifactId(), wix.getFile().getAbsolutePath() ));
//				} 
				// definitions.add(String.format("%1$s.TargetName=%2$s", wix.getArtifactId(), wix.getWixInfo().getName() ));
				if( wix.hasClassifier() ){
					addDefinition(String.format("%1$s.%2$s.TargetPath-%4$s=%3$s", wix.getGroupId(), wix.getArtifactId(), defineRepoFile( wix.getFile() ), wix.getClassifier() ));
				}
			}
		}
	}


	protected void addJARDefines() throws MojoExecutionException {
		// TODO: transitive only through direct attached jars...
		Set<Artifact> jarArtifacts = getJARDependencySets();
		getLog().info( "Adding "+jarArtifacts.size()+" dependent JARs" );
		if( !jarArtifacts.isEmpty() ){
			for(Artifact jar : jarArtifacts){
				getLog().debug( String.format("JAR added dependency %1$s", jar.getArtifactId() ) );
				// Warn: may need to make artifacts unique using groupId... but nar doesn't do that yet.
				// when there are multiple jars with the same name,
				// there is a conflict between requirements for reactor 'compile' build Vs 'install' build that can later be used in a patch, 
				// the conflict is due to pathing or lack there of from compile not having the package id in the path
				// so -b option used in linking cannot specify just the local repo, it must include the full path to versioned package folder or 'target'
				// ie.  foo/target/foo-1.jar  repo/com/foo/1/foo-1.jar  repo/net/foo/1/foo-1.jar
				addDefinition(String.format("%1$s.%2$s.TargetJAR=%3$s", jar.getGroupId(), jar.getArtifactId(), defineRepoFile( jar.getFile() )) );
			}
		}
	}

	protected void addNARDefines() throws MojoExecutionException {
		// TODO: transitive only through direct attached nars...
		Set<Artifact> narArtifacts = getNARDependencySets();
		getLog().info( "Adding "+narArtifacts.size()+" dependent NARs" );
		if( !narArtifacts.isEmpty() ){
			/* VisualStudio Reference Projects 
			 * -dConsoleApplication1.Configuration=Release 
			 * -d"ConsoleApplication1.FullConfiguration=Release|Win32" 
			 * -dConsoleApplication1.Platform=Win32 
			 * -dConsoleApplication1.ProjectDir=C:\sln\ConsoleApplication1\ 
			 * -dConsoleApplication1.ProjectExt=.vcxproj 
			 * -dConsoleApplication1.ProjectFileName=ConsoleApplication1.vcxproj 
			 * -dConsoleApplication1.ProjectName=ConsoleApplication1 
			 * -dConsoleApplication1.ProjectPath=C:\sln\ConsoleApplication1\CA1.vcxproj 
			 * -dConsoleApplication1.TargetDir=C:\sln\Release\ 
			 * -dConsoleApplication1.TargetExt=.exe 
			 * -dConsoleApplication1.TargetFileName=CA1.exe 
			 * -dConsoleApplication1.TargetName=CA1 
			 * -dConsoleApplication1.TargetPath=C:\sln\Release\CA1.exe
			 * 
			 * Working with nar unpack adding equivelant -b c:\sln  and narDir partials.
			 * -dConsoleApplication1.TargetDir=ConsoleApplication1-1.0.0-
			 * -dConsoleApplication1.TargetFileName=CA1.exe 
			 */
	
			// TODO: work out what narUnpack happened?
			// Nar Layout 21 segments.. 
			addDefinition("narDirx86.dll=x86-Windows-msvc-shared/lib/x86-Windows-msvc/shared");
			addDefinition("narDirx86.exe=x86-Windows-msvc-executable/bin/x86-Windows-msvc");
			addDefinition("narDiramd64.dll=amd64-Windows-msvc-shared/lib/amd64-Windows-msvc/shared");
			addDefinition("narDiramd64.exe=amd64-Windows-msvc-executable/bin/amd64-Windows-msvc");
			// and intel... and... 
			addDefinition("narDirNA=noarch");
			for(Artifact nar : narArtifacts){
				getLog().debug( String.format("NAR added dependency %1$s", nar.getArtifactId() ) );
				// Warn: may need to make artifacts unique using groupId... but nar doesn't do that yet.
				addDefinition(String.format("%1$s.TargetDir=%1$s-%2$s-", nar.getArtifactId(), nar.getVersion()));
				addDefinition(String.format("%1$s.TargetNAR=%2$s", nar.getArtifactId(), defineRepoFile( nar.getFile() ) ));
				// ... have to open up the narInfo to get the name... it can wait
				//definitions.add(String.format("%1$s.TargetFileName=%1$s-%2$s-", nar.getArtifactId(), narInfo.getOutput() ));
			}
		}
	}

	protected void addNPANDAYDefines() throws MojoExecutionException {
		// TODO: transitive only through direct attached nars...
		Set<Artifact> npandayArtifacts = getNPANDAYDependencySets();
		getLog().info( "Adding "+npandayArtifacts.size()+" dependent NPANDAY dependencies" );
		
		if( !npandayArtifacts.isEmpty() ){
			/* VisualStudio Reference Projects 
			 * 
			 * Working with nar unpack adding equivelant -b c:\sln  and narDir partials.
			 */
	
			// Overall definitions for NPANDAY?  definitions.add("narDirNA=noarch");
			for(Artifact npanday : npandayArtifacts){
// TODO: There are various different types for npanday, this list might need to expand to support multiple
				if( !( npanday.getType().endsWith("-config") || npanday.getType().endsWith(".config")) ){
					getLog().debug( String.format("NPANDAY added dependency %1$s", npanday.getArtifactId() ) );
					addDefinition(String.format("%1$s.%2$s.TargetNPANDAY=%3$s", npanday.getGroupId(), npanday.getArtifactId(), defineRepoFile( npanday.getFile() ) ));
				} else { 
					getLog().debug( String.format("NPANDAY added config dependency %1$s", npanday.getArtifactId() ) );
					addDefinition(String.format("%1$s.%2$s.TargetNPANDAYConfig=%3$s", npanday.getGroupId(), npanday.getArtifactId(), defineRepoFile( npanday.getFile() ) ));
				}
			}
		}
	}
	
	protected String defineRepoFile( File toTrim ){
		return toTrim.getAbsolutePath().replace(localRepository.getBasedir()+"\\", "");
	}

	protected String defineWixUnpackFile( File toTrim ){
		return toTrim.getAbsolutePath().replace(unpackDirectory+"\\", "");
	}
}