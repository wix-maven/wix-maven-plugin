package net.sf.wix;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Reference - 
 * transform between different versions for patch (note same format must be used for all files - current 3.6)
 * torch.exe -p -xi Error\Product.wixpdb Fixed\Product.wixpdb -out Patch.wixmst
 * torch.exe -p -xo Error\Product.msi Fixed\Product.msi -out Patch.wixmst
 *  
 * pyro.exe Patch.wixmsp -out Patch.msp -t Sample Patch.wixmst
 */
/**
 * Goal which executes WiX torch & pyro to create msp files.
 * 
 * @goal patch
 * @phase package
 * @requiresProject true
 * @requiresDependencyResolution compile
 */
public class PatchMojo extends AbstractTorchMojo {

    /**
     * ArtifactItem to use as base. (ArtifactItem contains groupId, artifactId, version, type, classifier) 
     * See <a href="./usage.html">Usage</a> for details.
     *
     * @parameter
     * @required
     */
    private ArtifactItem baseArtifactItem;
    
    /**
     * ArtifactItem to use as patch. (ArtifactItem contains groupId, artifactId, version, type, classifier) 
     * See <a href="./usage.html">Usage</a> for details.
     *
     * @parameter
     * @required
     */
    private ArtifactItem patchedArtifactItem;

	/**
	 * Baseline id... needs to match the baseline in the patch file, why then is it needed...I don't get how this works...
	 * Can we just read this from the input xml?
	 * 
	 * @parameter expression="${wix.baseline}"
	 * @required
	 */
	protected String baseline;
	
	public PatchMojo() {
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void addValidationOptions(Commandline cl) {
		cl.addArguments(new String[] { "-t", "patch" });

		if( "wixpdb".equals(patchedArtifactItem.getType() ) )
			cl.addArguments(new String[] { "-xi" });
		else
			cl.addArguments(new String[] { "-xo" });
	}

	@Override
	protected String torchOutputExtension() {
		return "wixmst";
	}

	/**
	 * Prepare and execute pyro commandline tool
	 *  
	 * @param pyroTool
	 * @param patchInputFile
	 * @param transformInputFile
	 * @param archOutputFile
	 * @throws MojoExecutionException
	 */
//	protected void pyro(File pyroTool, File patchInputFile, File transformInputFile, File archOutputFile) throws MojoExecutionException {
//		getLog().info(" -- Pyro : " + archOutputFile.getPath());
//		Commandline cl = new Commandline();
//
//		cl.setExecutable(pyroTool.getAbsolutePath());
//		// cl.setWorkingDirectory(wxsInputDirectory);
//		addToolsetGeneralOptions(cl);
//
//		//addOptions(cl);
//		addWixExtensions(cl);
//		cl.addArguments(new String[] { patchInputFile.getAbsolutePath(), "-t", baseline, transformInputFile.getAbsolutePath(), "-out", archOutputFile.getAbsolutePath() });
//		// addOtherOptions(cl);
//
//		pyro(cl);
//	} 

	protected void pyro(File pyroTool, File patchInputFile, Map<String,File> transformInputFiles, File archOutputFile) throws MojoExecutionException {
		getLog().info(" -- Pyro : " + archOutputFile.getPath());
		Commandline cl = new Commandline();

		cl.setExecutable(pyroTool.getAbsolutePath());
		// cl.setWorkingDirectory(wxsInputDirectory);
		addToolsetGeneralOptions(cl);

		//addOptions(cl);
		addWixExtensions(cl);
		cl.addArguments(new String[] { patchInputFile.getAbsolutePath() });
		for (Map.Entry<String, File> entry : transformInputFiles.entrySet())
		{
			cl.addArguments(new String[] { "-t", baseline+"_"+entry.getKey().replace('-', '_'), entry.getValue().getAbsolutePath() });
		}
		cl.addArguments(new String[] { "-out", archOutputFile.getAbsolutePath() });
		// addOtherOptions(cl);

		pyro(cl);
	} 
	
	/**
	 * Execute the given command line parsing output for pyro comments
	 * @param cl
	 * @throws MojoExecutionException
	 */
	protected void pyro(Commandline cl) throws MojoExecutionException {
		try {
			if (verbose) {
				getLog().info(cl.toString());
			} else {
				getLog().debug(cl.toString());
			}

			// TODO: maybe should report or do something with return value.
			int returnValue = CommandLineUtils.executeCommandLine(cl, new StreamConsumer() {

				public void consumeLine(final String line) {
					// TODO: pyro specific message handling
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
				throw new MojoExecutionException("Problem executing pyro, return code " + returnValue);
			}
		} catch (CommandLineException e) {
			// throw new MojoExecutionException( "Error running mapping-tools.",
			// e );
			throw new MojoExecutionException("Problem executing pyro", e);
		}
	}

	protected Set<Artifact> getDependencySets() throws MojoExecutionException {
		// add filters in well known order, least specific to most specific
		FilterArtifacts filter = new FilterArtifacts();

		filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), true));

		// filter.addFilter( new ScopeFilter( DependencyUtil.cleanToBeTokenizedString( this.includeScope ),
		// DependencyUtil.cleanToBeTokenizedString( this.excludeScope ) ) );
		//
		// filter.addFilter( new TypeFilter( DependencyUtil.cleanToBeTokenizedString( this.includeTypes ),
		// DependencyUtil.cleanToBeTokenizedString( this.excludeTypes ) ) );
		//
		// filter.addFilter( new ClassifierFilter( DependencyUtil.cleanToBeTokenizedString( this.includeClassifiers ),
		// DependencyUtil.cleanToBeTokenizedString( this.excludeClassifiers ) ) );
		//
		// filter.addFilter( new GroupIdFilter( DependencyUtil.cleanToBeTokenizedString( this.includeGroupIds ),
		// DependencyUtil.cleanToBeTokenizedString( this.excludeGroupIds ) ) );
		//
		// filter.addFilter( new ArtifactIdFilter( DependencyUtil.cleanToBeTokenizedString( this.includeArtifactIds ),
		// DependencyUtil.cleanToBeTokenizedString( this.excludeArtifactIds ) ) );

		filter.addFilter(new TypeFilter("msi", ""));
		// String clasfilter = arch+"-"+culture+","+arch+"-neutral";
		// getLog().debug(clasfilter);
		// filter.addFilter( new ClassifierFilter( clasfilter, "" ) );

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

	public void execute() throws MojoExecutionException, MojoFailureException {
		if ( skip )
		{
			getLog().info( getClass().getName() + " skipped" );
			return;
		}

		File torchTool = validateTool();
		File pyroTool = new File(toolDirectory, "/bin/pyro.exe");
		if (!pyroTool.exists())
			throw new MojoExecutionException("Pyro tool doesn't exist " + pyroTool.getAbsolutePath());

		defaultLocale();

		//Set<Artifact> artifacts = getDependencySets();
				
		for (String arch : getPlatforms()) {
			Map<String,File> archIntermediateFiles = new HashMap<String, File>();
			for (String culture : culturespecs()) {

				// download dependant msi versions
				File baseInputFile;
				File latestInputFile;

				if( ! baseArtifactItem.getType().equals(patchedArtifactItem.getType()))
					throw new MojoExecutionException( "Wix Pyro currently requires that both inputs to the patch are the same type, wixpdb or msi." );
				
				baseInputFile = getRelatedArtifacts( baseArtifactItem, arch, culture, baseArtifactItem.getType());
				latestInputFile = getRelatedArtifacts( patchedArtifactItem, arch, culture, patchedArtifactItem.getType());
			
				// 
				File archIntermediateFile = getOutput(intDirectory, arch, culture, "wixmst");
				// TODO: do we really need culture specific patch files ever?  
				//File archPatchFile = getOutput( intDirectory, arch, null, "wixmsp");

				torch(torchTool, baseInputFile, latestInputFile, archIntermediateFile);

//				File archPatchFile = getOutput(arch, culture, "wixmsp"); // output from earlier light
//				File archOutputFile = getOutput(arch, culture, "msp");
//				pyro(pyroTool, archPatchFile, archIntermediateFile, archOutputFile);
				archIntermediateFiles.put(culture, archIntermediateFile);
			}
			String culture = baseCulturespec();
			File patchFile = getOutput(arch, culture, "wixmsp"); // output from earlier light
			File outputFile = getOutput(arch, culture, "msp");
			pyro(pyroTool, patchFile, archIntermediateFiles, outputFile);
		}
	}
	
	/**
	 * Based on Maven-dependency-plugin AbstractFromConfigurationMojo.
	 * 
	 * Resolves the Artifact from the remote repository if necessary. If no version is specified, it will be retrieved from the dependency list or
	 * from the DependencyManagement section of the pom.
	 * 
	 * @param artifactItem
	 *            containing information about artifact from plugin configuration.
	 * @return Artifact object representing the specified file.
	 * @throws MojoExecutionException
	 *             with a message if the version can't be found in DependencyManagement.
	 */
	protected File getRelatedArtifacts(ArtifactItem artifactItem, String arch, String culture, String type) throws MojoExecutionException {

		VersionRange vr;
		try {
			vr = VersionRange.createFromVersionSpec(artifactItem.getVersion());
		} catch (InvalidVersionSpecificationException e1) {
			vr = VersionRange.createFromVersion(artifactItem.getVersion());
		}

		Artifact artifact;
		Set<Artifact> artifactSet = new HashSet<Artifact>();

		String classifier = arch + "-" + (culture == null ? "neutral" : culture);
		getArtifact(artifactItem.getGroupId(), artifactItem.getArtifactId(), type, artifactSet, vr, classifier);

		if( artifactSet.size() != 1 ) // this is more like an assert - we are only asking for one, and if none it already threw.
			throw new MojoExecutionException(String.format("Found multiple artifacts for : %1:%2:%3:%4:%5", artifactItem.getGroupId(), artifactItem.getArtifactId(), type, vr, classifier) );

		File artifactFile = null;
		for( Artifact res : artifactSet )
			artifactFile = res.getFile();

		if( "wixpdb".equals( type ) )
			return artifactFile;
		throw new MojoExecutionException("Incomplete Mojo - add tools for admin unpacking msi");
//		else{
//		 	File resolvedArtifactFile = getOutput(new File(intDirectory,"base"), arch, culture, "msi");
//		
//		throw new MojoExecutionException("Incomplete Mojo - add tools for admin unpacking msi");
//		// copy artifactFile to resolvedArtifactFile
//		// unpack resolvedArtifactFile in intdir like 
//		//msiexec /a %newmsi% TARGETDIR="%workdir%\new" /qb /l*v "%workdir%\logs\new.log" Reboot=ReallySuppres
//		// return resolvedArtifactFile;
//	}

	}

}
