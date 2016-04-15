package com.github.wix_maven;

import org.apache.maven.plugins.annotations.Parameter;

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

/**
 * ArtifactItem represents information specified in the plugin configuration
 * section for each artifact.
 */
public class ArtifactItem {
    /**
     * Group Id of Artifact
     */
	@Parameter(required=true)
    private String groupId;

    /**
     * Name of Artifact
     */
	@Parameter(required=true)
    private String artifactId;

    /**
     * Version of Artifact
     */
	@Parameter
    private String version = null;

    /**
     * Type of Artifact (wixpdb, msi)
     */
    @Parameter(defaultValue="wixpdb")
    private String type="wixpdb";

    
    private String filterEmptyString( String in )
    {
        if ( "".equals( in ) )
        {
            return null;
        }
        return in;
    }

    /**
     * @return Returns the artifactId.
     */
    public String getArtifactId()
    {
        return artifactId;
    }

    /**
     * @param artifactId
     *            The artifactId to set.
     */
    public void setArtifactId( String artifactId )
    {
        this.artifactId = filterEmptyString( artifactId );
    }

    /**
     * @return Returns the groupId.
     */
    public String getGroupId()
    {
        return groupId;
    }

    /**
     * @param groupId
     *            The groupId to set.
     */
    public void setGroupId( String groupId )
    {
        this.groupId = filterEmptyString( groupId );
        //if( this.groupId != null ) this.groupId = this.groupId.toLowerCase();
    }

    /**
     * @return Returns the type.
     */
    public String getType()
    {
        return type;
    }

    /**
     * @param type
     *            The type to set.
     */
    public void setType( String type )
    {
        this.type = filterEmptyString( type );
    }

    /**
     * @return Returns the version.
     */
    public String getVersion()
    {
        return version;
    }

    /**
     * @param version
     *            The version to set.
     */
    public void setVersion( String version )
    {
        this.version = filterEmptyString( version );
    }

}
