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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.cli.Commandline;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

/**
 * Goal which executes WiX candle to create a .wixobj file.
 * 
 * The following project dependency inclusion patterns apply<br>
 * Dependent NAR project 'Foo' with possible nar output redefined as 'bar' <br>
 * <li>-dFoo.TargetDir=Foo-version\ <li>-dFoo.TargetExt=.wixlib <li>
 * -dFoo.TargetFileName=bar.type <li>-dFoo.TargetName=bar
 */
@Mojo(name = "wixproj", defaultPhase = LifecyclePhase.VALIDATE,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class WixprojMojo extends AbstractCompilerMojo {

  /**
   * The directory to store the time-stamp file for the processed aid files. Defaults to
   * outputDirectory. Only used with xsdTimestampFile being set.
   */
  @Parameter(property = "wix.updateVSProj", defaultValue = "true")
  protected boolean updateVSProj = true;

  /**
   * Target directory for Nar file unpacking.
   */
  @Parameter(property = "nar.unpackDirectory", defaultValue = "${project.build.directory}/nar")
  protected File narUnpackDirectory;

  /**
   * The file to store vsproj settings for maven dependencies.
   */
  @Parameter(property = "wix.vsprojTarget",
      defaultValue = "${project.basedir}/MavenDependency.targets")
  protected File vsprojTarget;

  /**
   * Do the binds need to be named for matching during patching.
   */
  @Parameter(property = "wix.useNamedBindPath", defaultValue = "false")
  protected boolean useNamedBindPath = false;

  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!updateVSProj) {
      getLog().warn("VS target file unchanged");
      return;
    }

    startVSProjUpdater();

    addWixDefines();
    addNARDefines();
    addJARDefines();
    addNPANDAYDefines();
    addHarvestDefines();
    addWixExtensions(null);

    addDefinitionX86("IsWin64=no");
    addDefinitionX86("narDir.dll=x86-Windows-msvc-shared/lib/x86-Windows-msvc/shared");
    addDefinitionX86("narDir.exe=x86-Windows-msvc-executable/bin/x86-Windows-msvc");
    addDefinitionX64("IsWin64=yes");
    addDefinitionX64("narDir.dll=amd64-Windows-msvc-shared/lib/amd64-Windows-msvc/shared");
    addDefinitionX64("narDir.exe=amd64-Windows-msvc-executable/bin/amd64-Windows-msvc");

    stopVSProjUpdater();

    getLog().info("Update VS target file " + vsprojTarget);
  }

  Document dom = null;
  Text mvnExt = null;
  Text mvnCompileDep = null;
  Text mvnLinkDep = null;
  Text mvnCompileDepX86 = null;
  Text mvnCompileDepX64 = null;
  Text mvnLinkDepX86 = null;
  Text mvnLinkDepX64 = null;

  private void startVSProjUpdater() throws MojoExecutionException {

    if (!vsprojTarget.getParentFile().exists()) {
      vsprojTarget.getParentFile().mkdirs();
    }
    try {
      if (!vsprojTarget.exists()) {
        vsprojTarget.createNewFile();
      }

      // instance of a DocumentBuilderFactory
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

      // use factory to get an instance of document builder
      DocumentBuilder db = dbf.newDocumentBuilder();
      // create instance of DOM
      dom = db.newDocument();
      Element ele = null;

      // create the root element
      // xmlns="http://schemas.microsoft.com/developer/msbuild/2003"
      Element rootEle = dom.createElement("Project");
      rootEle.setAttribute("xmlns", "http://schemas.microsoft.com/developer/msbuild/2003");
      dom.appendChild(rootEle);
      rootEle
          .appendChild(dom
              .createComment("This is a generated file from wix-maven-plugin to provide maven dependency values\n  To update this file, from the project root run\n      mvn net.sf.wix:wix-maven-plugin:wixproj "));
      ele = dom.createElement("PropertyGroup");
      rootEle.appendChild(ele);
      rootEle = ele;

      ele = dom.createElement("MavenRepoPath");
      ele.setAttribute("Condition", " '$(MavenRepoPath)' == '' ");
      ele.appendChild(dom.createTextNode(localRepository.getBasedir()));
      rootEle.appendChild(ele);

      ele = dom.createElement("narUnpackDirectory");
      ele.setAttribute("Condition", " '$(narUnpackDirectory)' == '' ");
      ele.appendChild(dom.createTextNode(narUnpackDirectory.getAbsolutePath()));
      rootEle.appendChild(ele);

      ele = dom.createElement("wixUnpackDirectory");
      ele.setAttribute("Condition", " '$(wixUnpackDirectory)' == '' ");
      ele.appendChild(dom.createTextNode(unpackDirectory.getAbsolutePath()));
      rootEle.appendChild(ele);

      ele = dom.createElement("mavenExtensions");
      mvnExt = dom.createTextNode("\n");
      ele.appendChild(mvnExt);
      rootEle.appendChild(ele);

      ele = dom.createElement("mavenCompileDependencies");
      mvnCompileDep = dom.createTextNode("\n");
      ele.appendChild(mvnCompileDep);
      rootEle.appendChild(ele);

      ele = dom.createElement("mavenCompileDependencies");
      ele.setAttribute("Condition", " '$(Platform)' == 'x86' ");
      mvnCompileDepX86 = dom.createTextNode("$(mavenCompileDependencies)\n");
      ele.appendChild(mvnCompileDepX86);
      rootEle.appendChild(ele);

      ele = dom.createElement("mavenCompileDependencies");
      ele.setAttribute("Condition", " '$(Platform)' == 'x64' ");
      mvnCompileDepX64 = dom.createTextNode("$(mavenCompileDependencies)\n");
      ele.appendChild(mvnCompileDepX64);
      rootEle.appendChild(ele);

      ele = dom.createElement("mavenLinkerDependencies");
      if (useNamedBindPath) {
        mvnLinkDep = dom.createTextNode("\n  -b narUnpackDir=$(narUnpackDirectory)\\\n");
        mvnLinkDep.appendData("  -b mavenRepoDir=$(MavenRepoPath)\\\n");
        mvnLinkDep.appendData("  -b wixUnpackDirectory=$(wixUnpackDirectory)\\\n");
      } else {
        mvnLinkDep = dom.createTextNode("\n  -b $(narUnpackDirectory)\\\n");
        mvnLinkDep.appendData("  -b $(MavenRepoPath)\\\n");
        mvnLinkDep.appendData("  -b $(wixUnpackDirectory)\\\n");
      }
      ele.appendChild(mvnLinkDep);
      rootEle.appendChild(ele);

      ele = dom.createElement("mavenLinkerWixlibDependencies");
      ele.setAttribute("Condition", " '$(Platform)' == 'x86' ");
      mvnLinkDepX86 = dom.createTextNode("$(mavenLinkerWixlibDependencies)\n");
      ele.appendChild(mvnLinkDepX86);
      rootEle.appendChild(ele);

      ele = dom.createElement("mavenLinkerWixlibDependencies");
      ele.setAttribute("Condition", " '$(Platform)' == 'x64' ");
      mvnLinkDepX64 = dom.createTextNode("$(mavenLinkerWixlibDependencies)\n");
      ele.appendChild(mvnLinkDepX64);
      rootEle.appendChild(ele);

      // ele = dom.createElement("");

    } catch (ParserConfigurationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
    }
  }

  @Override
  protected void addDefinition(String def) {
    addDefinitionBase(mvnCompileDep, def);
  }

  protected void addDefinitionBase(Text set, String def) {
    set.appendData("  -d" + def + "\n");
  }

  protected void addDefinitionX86(String def) {
    addDefinitionBase(mvnCompileDepX86, def);
  }

  protected void addDefinitionX64(String def) {
    addDefinitionBase(mvnCompileDepX64, def);
  }

  @Override
  protected void addExtension(Commandline cl, String extFile) {
    mvnExt.appendData("  -ext " + extFile + "\n");
  }

  protected void addLib(Artifact wixRes, Text linkDep, String arch) throws MojoExecutionException {
    if (verbose)
      getLog().debug("Checking for  " + wixRes.getArtifactId() + " wixlib " + arch);
    if (getPlatforms().contains(arch)) {
      Set<Artifact> depArtifacts = getRelatedArtifacts(wixRes, arch, "en-US");
      for (Iterator<Artifact> j = depArtifacts.iterator(); j.hasNext();) {
        Artifact lib = j.next();
        getLog().debug("Adding " + wixRes.getArtifactId() + " wixlib " + arch);
        // doesn't use /b option to path, but niceer to have
        // $(MavenRepoPath) than hard coded root
        linkDep.appendData(lib.getFile().getAbsolutePath()
            .replace(localRepository.getBasedir() + "\\", "$(MavenRepoPath)\\")
            + "\n");
      }
    }
  }

  @Override
  protected void addResource(File resUnpackDirectory, Artifact wixRes)
      throws MojoExecutionException {
    File neutralFolder = new File(resUnpackDirectory, "wix-locale");
    if (neutralFolder.exists()) {
      getLog().debug("Adding " + wixRes.getArtifactId() + " neutral resource folder");
      addLinkPath(defineWixUnpackFile(neutralFolder), wixRes.getArtifactId() + "neutral");
      File enFolder = new File(neutralFolder, "en-US");
      if (enFolder.exists()) {
        getLog().debug("Adding " + wixRes.getArtifactId() + " neutral en-US folder");
        addLinkPath(defineWixUnpackFile(enFolder), wixRes.getArtifactId() + "loc");
      }
    }

    if (PACK_LIB.equalsIgnoreCase(wixRes.getType())) {
      addLib(wixRes, mvnLinkDepX86, "x86");
      addLib(wixRes, mvnLinkDepX64, "x64");
      // addLib( wixRes, mvnLinkDepX64, "intell" );
    }
  }

  protected void addLinkPath(String libFolder, String namedBindPath) {
    mvnLinkDep.appendData("  -b " + (useNamedBindPath ? namedBindPath + "=" : "") + libFolder
        + "\\\n");
  }

  private void stopVSProjUpdater() {
    if (dom != null) {
      try {
        Transformer tr = TransformerFactory.newInstance().newTransformer();
        tr.setOutputProperty(OutputKeys.INDENT, "yes");
        tr.setOutputProperty(OutputKeys.METHOD, "xml");
        tr.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        // send DOM to file
        tr.transform(new DOMSource(dom), new StreamResult(new FileOutputStream(vsprojTarget)));

      } catch (TransformerException te) {
        System.out.println(te.getMessage());
      } catch (IOException ioe) {
        System.out.println(ioe.getMessage());
      }
    }

  }

}
