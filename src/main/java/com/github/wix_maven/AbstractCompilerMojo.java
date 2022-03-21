package com.github.wix_maven;

/*
 * #%L WiX Toolset (Windows Installer XML) Maven Plugin %% Copyright (C) 2013 - 2014 GregDomjan
 * NetIQ %% Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License. #L%
 */

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;

public abstract class AbstractCompilerMojo extends AbstractWixMojo {

  public AbstractCompilerMojo() {
    super();
  }

  protected abstract void addDefinition(String def);

  protected void addResource(File resUnpackDirectory, Artifact wixRes)
      throws MojoExecutionException {}

  @SuppressWarnings("unchecked")
  protected Set<Artifact> getNARDependencySets() throws MojoExecutionException {
    FilterArtifacts filter = new FilterArtifacts();
    // Cannot do this filter in maven3 as it blocks classifiers - works in maven 2.
    // filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), true));
    filter.addFilter(new TypeFilter("nar", ""));

    // start with all artifacts.
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
    getLog().info("Adding " + wixArtifacts.size() + " dependent msm/msi/msp/wixlib/bundle");
    if (!wixArtifacts.isEmpty()) {
      for (Artifact wix : wixArtifacts) {
        if (verbose)
          getLog().info(String.format("WIX added dependency %1$s", wix.getArtifactId()));
        else
          getLog().debug(String.format("WIX added dependency %1$s", wix.getArtifactId()));

        if (!wix.hasClassifier()) {
          File resUnpackDirectory = wixUnpackDirectory(wix);
          // if( resUnpackDirectory.exists() ) pending move of unpack to earlier phase, currently
          // run after this goal.
          {
            addDefinition(String.format("%1$s.%2$s.UnpackPath=%3$s", wix.getGroupId(),
                wix.getArtifactId(), defineWixUnpackFile(resUnpackDirectory)));
          }
          addResource(resUnpackDirectory, wix);
        }

        // TODO: resolve source platform issues - msm/msi don't have non classified artifacts.
        // if( PACK_MERGE.equalsIgnoreCase( wix.getType() ) ){
        // definitions.add(String.format("%1$s.TargetDir-%3$s=%2$s", wix.getArtifactId(),
        // wix.getFile().getParentFile().getAbsolutePath() ));
        // definitions.add(String.format("%1$s.TargetName-%3$s=%2$s", wix.getArtifactId(),
        // wix.getFile().getName() ));
        // definitions.add(String.format("%1$s.TargetPath-%3$s=%2$s", wix.getArtifactId(),
        // wix.getFile().getAbsolutePath() ));
        // }
        // definitions.add(String.format("%1$s.TargetName=%2$s", wix.getArtifactId(),
        // wix.getWixInfo().getName() ));
        if (wix.hasClassifier()) {
          addDefinition(String.format("%1$s.%2$s.TargetPath-%4$s=%3$s", wix.getGroupId(),
              wix.getArtifactId(), defineRepoFile(wix.getFile()), wix.getClassifier()));
        }
      }
    }
  }

  protected void addHarvestDefines() throws MojoExecutionException {
    // TODO: transitive only through direct attached jars...
    Set<Artifact> jarArtifacts = getJARDependencySets();
    if (!harvestInputDirectory.exists())
      return;
    getLog().info("Adding Harvest input locations from " + harvestInputDirectory.getPath());

    FileFilter directoryFilter = new FileFilter() {
      public boolean accept(File file) {
        return file.isDirectory();
      }
    };
    for (File folders : harvestInputDirectory.listFiles(directoryFilter)) {
      if (HarvestMojo.HT_DIR.equals(folders.getName())) {
        for (File subfolder : folders.listFiles(directoryFilter)) {
          addHarvestDefine(HarvestMojo.HT_DIR, subfolder);
        }
      } else if (HarvestMojo.HT_FILE.equals(folders.getName())) {
        // for (File subfolder: folders.listFiles(fileFilter) ){
        // multiHeat(heatTool, HT_FILE, subfolder);
        // }
      }
    }
  }

  protected void addHarvestDefine(String prefix, File folderOrFile) {
    addDefinition(String.format("%1$s-%2$s=%3$s", prefix, folderOrFile.getName(),
        folderOrFile.getAbsolutePath())); // possibly want this relative from some base /b
  }

  protected void addJARDefines() throws MojoExecutionException {
    // TODO: transitive only through direct attached jars...
    Set<Artifact> jarArtifacts = getJARDependencySets();
    getLog().info("Adding " + jarArtifacts.size() + " dependent JARs");
    if (!jarArtifacts.isEmpty()) {
      for (Artifact jar : jarArtifacts) {
        getLog().debug(String.format("JAR added dependency %1$s", jar.getArtifactId()));
        // Warn: may need to make artifacts unique using groupId... but nar doesn't do that yet.
        // when there are multiple jars with the same name,
        // there is a conflict between requirements for reactor 'compile' build Vs 'install' build
        // that can later be used in a patch,
        // the conflict is due to pathing or lack there of from compile not having the package id in
        // the path
        // so -b option used in linking cannot specify just the local repo, it must include the full
        // path to versioned package folder or 'target'
        // ie. foo/target/foo-1.jar repo/com/foo/1/foo-1.jar repo/net/foo/1/foo-1.jar

        if (jar.hasClassifier()) {
          addDefinition(String.format("%1$s.%2$s.%3$s.TargetJAR=%4$s", jar.getGroupId(),
              jar.getArtifactId(), jar.getClassifier(), defineRepoFile(jar.getFile())));
        } else {
          addDefinition(String.format("%1$s.%2$s.TargetJAR=%3$s", jar.getGroupId(),
              jar.getArtifactId(), defineRepoFile(jar.getFile())));
        }
      }
    }
  }

  protected void addNARDefines() throws MojoExecutionException {
    // TODO: transitive only through direct attached nars...
    Set<Artifact> narArtifacts = getNARDependencySets();
    getLog().info("Adding " + narArtifacts.size() + " dependent NARs");
    if (!narArtifacts.isEmpty()) {
      /*
       * VisualStudio Reference Projects -dConsoleApplication1.Configuration=Release
       * -d"ConsoleApplication1.FullConfiguration=Release|Win32"
       * -dConsoleApplication1.Platform=Win32
       * -dConsoleApplication1.ProjectDir=C:\sln\ConsoleApplication1\
       * -dConsoleApplication1.ProjectExt=.vcxproj
       * -dConsoleApplication1.ProjectFileName=ConsoleApplication1.vcxproj
       * -dConsoleApplication1.ProjectName=ConsoleApplication1
       * -dConsoleApplication1.ProjectPath=C:\sln\ConsoleApplication1\CA1.vcxproj
       * -dConsoleApplication1.TargetDir=C:\sln\Release\ -dConsoleApplication1.TargetExt=.exe
       * -dConsoleApplication1.TargetFileName=CA1.exe -dConsoleApplication1.TargetName=CA1
       * -dConsoleApplication1.TargetPath=C:\sln\Release\CA1.exe
       * 
       * Working with nar unpack adding equivelant -b c:\sln and narDir partials.
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
      for (Artifact nar : narArtifacts) {
        getLog().debug(String.format("NAR added dependency %1$s", nar.getArtifactId()));
        // Warn: may need to make artifacts unique using groupId... but nar doesn't do that yet.
        addDefinition(String.format("%1$s.TargetDir=%1$s-%2$s-", nar.getArtifactId(),
            nar.getBaseVersion()));
        addDefinition(String.format("%1$s.TargetNAR=%2$s", nar.getArtifactId(),
            defineRepoFile(nar.getFile())));
        // ... have to open up the narInfo to get the name... it can wait
        // definitions.add(String.format("%1$s.TargetFileName=%1$s-%2$s-", nar.getArtifactId(),
        // narInfo.getOutput() ));
      }
    }
  }

  protected void addNPANDAYDefines() throws MojoExecutionException {
    // TODO: transitive only through direct attached nars...
    Set<Artifact> npandayArtifacts = getNPANDAYDependencySets();
    getLog().info("Adding " + npandayArtifacts.size() + " dependent NPANDAY dependencies");

    if (!npandayArtifacts.isEmpty()) {
      /*
       * VisualStudio Reference Projects
       * 
       * Working with nar unpack adding equivelant -b c:\sln and narDir partials.
       */

      // Overall definitions for NPANDAY? definitions.add("narDirNA=noarch");
      for (Artifact npanday : npandayArtifacts) {
        // TODO: There are various different types for npanday, this list might need to expand to
        // support multiple
        if (!(npanday.getType().endsWith("-config") || npanday.getType().endsWith(".config"))) {
          if (npanday.hasClassifier()) {
            getLog().debug(
                String.format("NPANDAY added dependency %1$s with classifier %2$s",
                    npanday.getArtifactId(), npanday.getClassifier()));
            addDefinition(String
                .format("%1$s.%2$s.%4$s.TargetNPANDAY=%3$s", npanday.getGroupId(),
                    npanday.getArtifactId(), defineRepoFile(npanday.getFile()),
                    npanday.getClassifier()));
          } else {
            getLog().debug(String.format("NPANDAY added dependency %1$s", npanday.getArtifactId()));
            addDefinition(String.format("%1$s.%2$s.TargetNPANDAY=%3$s", npanday.getGroupId(),
                npanday.getArtifactId(), defineRepoFile(npanday.getFile())));
          }
        } else {
          getLog().debug(
              String.format("NPANDAY added config dependency %1$s", npanday.getArtifactId()));
          addDefinition(String.format("%1$s.%2$s.TargetNPANDAYConfig=%3$s", npanday.getGroupId(),
              npanday.getArtifactId(), defineRepoFile(npanday.getFile())));
        }
      }
    }
  }

  protected String defineRepoFile(File toTrim) {
    return toTrim.getAbsolutePath().replace(localRepository.getBasedir() + "\\", "");
  }

  protected String defineWixUnpackFile(File toTrim) {
    return toTrim.getAbsolutePath().replace(unpackDirectory + "\\", "");
  }

}
