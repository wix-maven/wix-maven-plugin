package net.sf.wix;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.compiler.util.scan.InclusionScanException;
import org.codehaus.plexus.compiler.util.scan.SimpleSourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.SourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.StaleSourceScanner;
import org.codehaus.plexus.compiler.util.scan.mapping.SingleTargetSourceMapping;
import org.codehaus.plexus.compiler.util.scan.mapping.SuffixMapping;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

public abstract class AbstractLinker extends AbstractPackageable {

	/**
	 * Properties catch all in case we missed some configuration. Passed directly to light or lit
	 * 
	 * @parameter
	 */
	private Properties linkProperties;

	/**
	 * The set of files/patterns to include. <br>
	 * If not set Defaults to "**\/arch\/*.wixlib", "**\/arch\/*.wixobj"
	 * 
	 * @parameter
	 */
	private Set<String> intIncludes = new HashSet<String>();
	/**
	 * A list of exclusion filters. See intIncludes.
	 * 
	 * @parameter
	 */
	private Set<String> intExcludes = new HashSet<String>();

	/**
	 * The directory to scan for localisation (.wxl) files.
	 * For Light the sub culture is added as a -b path
	 * For Light and Lit added as a -b source path
	 * 
	 * @parameter default-value="${project.basedir}/src/main/wix-locale"
	 * @required
	 */
	protected File wxlInputDirectory;

	/**
	 * The directory to scan for wix files.
	 * For each build type there is at least one wxs file required
	 * 
	 * @parameter default-value="${project.basedir}/src/main/wix-resource"
	 * @required
	 */
	protected File resourceDirectory;
	
	/**
	 * The set of files/patterns to include. <br>
	 * If not set Defaults to "**\/*.wxl"
	 * Starting from wxlInputDirectory 
	 * - For light it is applied to the culture specific folder inside, 
	 * - For lit it applies to all cultures. 
	 * 
	 * @parameter
	 */
	private Set<String> wxlIncludes = new HashSet<String>();
	/**
	 * A list of exclusion filters. See wxlIncludes
	 * 
	 * @parameter
	 */
	private Set<String> wxlExcludes = new HashSet<String>();

	/**
	 * Specify a base paths to locate all files. (-b option)
	 * By default the wxsInputDirectory is added if it exists.
	 * By default the narUnpackDirectory is added if it exists.
	 * 
	 * @parameter 
	 */
	protected Set<String> fileSourceRoots = new HashSet<String>();
	
	/**
	 * Target directory for Nar file unpacking. 
	 * 
	 * @parameter expression="${nar.unpackDirectory}" default-value="${project.build.directory}/nar"
	 * @readonly
	 */
	private File narUnpackDirectory;

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

	protected void addOptions(Commandline cl, Set<String> allFileSourceRoots) {
// defensive assert(allFileSourceRoots != null)
		if( wxsInputDirectory.exists() )
			allFileSourceRoots.add(wxsInputDirectory.getAbsolutePath());

		if( resourceDirectory.exists() )
			allFileSourceRoots.add(resourceDirectory.getAbsolutePath());

		if( narUnpackDirectory.exists() )
			allFileSourceRoots.add(narUnpackDirectory.getAbsolutePath());
		
		for (Iterator<String> i = allFileSourceRoots.iterator(); i.hasNext();) {
			//  cannot contain a quote. Quotes are often accidentally introduced when trying to refer to a directory path with spaces in it, such as "C:\Out Directory" or "C:\Out Directory\".  The correct representation for that path is: "C:\Out Directory\\"
			cl.addArguments(new String[] { "-b", i.next() + "\\\\" });//.getPath()
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
				throw new MojoExecutionException("Problem executing linker, return code " + returnValue);
			}
		} catch (CommandLineException e) {
			// throw new MojoExecutionException( "Error running mapping-tools.",
			// e );
			throw new MojoExecutionException("Problem executing linker", e);
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

		multilink(toolDirectory);

//		if (!extendedUse)
//			cleanupFileBasedResources();
	}

	protected abstract void multilink(File toolDirectory) throws MojoExecutionException;
}