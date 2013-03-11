package net.sf.wix;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Reference - 
 * transform between different locales
 * torch.exe -p -t language SampleMulti.msi Sample_Hu-hu.msi -out hu-hu.mst
 * torch.exe -p -t language SampleMulti.msi Sample_Fr-fr.msi -out fr-fr.mst
 */
/**
 * Goal which executes WiX torch to create diff files - mst, cab
 * 
 * @goal transform
 * @phase package
 * @requiresProject true
 */
public class TransformMojo extends AbstractTorchMojo {

	public TransformMojo() {
	}

	@Override
	protected void addValidationOptions(Commandline cl) {
		cl.addArguments(new String[] { "-t", "language" });
	}

	@Override
	protected String torchOutputExtension() {
		return "mst";
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		if ( skip )
		{
			getLog().info( getClass().getName() + " skipped" );
			return;
		}

		File torchTool = validateTool();

		// TODO: add auto ident cultures?
		// TODO: add handling for msp/burn
		if (localeList.size() < 2 
				|| ! ( ML_TRANSFORM.equalsIgnoreCase(mergeLevel) || ML_REPACK.equalsIgnoreCase(mergeLevel) )
			)
			return;

		Set<String> subcultures = alternateCulturespecs();
		String base = baseCulturespec();

		String extension = "mst";
		for (String arch : getPlatforms()) {
			File baseInputFile = getOutput(arch, base, packaging);

			for (String culture : subcultures) {

				File archInputFile = getOutput(arch, culture, packaging);
				File archOutputFile = getOutput(arch, culture, extension);

				torch(torchTool, baseInputFile, archInputFile, archOutputFile);
			}
		}
	}
}
