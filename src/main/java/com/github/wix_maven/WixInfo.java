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
/*
 * Derived from work NAR-maven-plugin (c) Mark Donscelmann
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * @author Mark Donszelmann
 */
public class WixInfo
{

    public static final String PROPERTIES = "wix.properties";

    private String groupId, artifactId, version;

    private Properties info;

    public WixInfo( String groupId, String artifactId, String version ) throws MojoExecutionException
    {
        this( groupId, artifactId, version, null );
    }
    
    public WixInfo( String groupId, String artifactId, String version, File propertiesFile ) throws MojoExecutionException
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        info = new Properties();

        // Fill with general properties. file
        if( propertiesFile != null )
        {
            try
            {
                info.load( new FileInputStream( propertiesFile ) );
            }
            catch ( FileNotFoundException e )
            {
                // ignored
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Problem loading "+propertiesFile, e );
            }
        }
    }

    public final String toString()
    {
        StringBuffer s = new StringBuffer( "Info for " );
        s.append( groupId );
        s.append( ":" );
        s.append( artifactId );
        s.append( "-" );
        s.append( version );
        s.append( " {\n" );

        for ( Iterator i = info.keySet().iterator(); i.hasNext(); )
        {
            String key = (String) i.next();
            s.append( "   " );
            s.append( key );
            s.append( "='" );
            s.append( info.getProperty( key, "<null>" ) );
            s.append( "'\n" );
        }

        s.append( "}\n" );
        return s.toString();
    }

    public final boolean exists( JarFile jar )
    {
        return getPropertiesEntry( jar ) != null;
    }

    public final void read( JarFile jar )
        throws IOException
    {
        info.load( jar.getInputStream( getPropertiesEntry( jar ) ) );
    }

    public final String getProperties(){
    	return "META-INF/wix/"; // + groupId + "/" + artifactId + "/"
    }
    
    private JarEntry getPropertiesEntry( JarFile jar )
    {
        return jar.getJarEntry( getProperties() + PROPERTIES );
    }

    public final void writeToPath( String path )
        throws IOException
    {
    	File filePath = new File(path,getProperties());
		if (!filePath.exists()) {
			filePath.mkdirs();
		}
    	File file = new File(filePath,PROPERTIES);

        info.store( new FileOutputStream( file ), "WiX Properties " ); // for " + groupId + "." + artifactId + "-" + version );
    }

    /**
     * getCultures list recorded for this wix object
     * If no cultures are listed, then null result representing default neutral. 
     * @return culture csv
     */
    public final String getCulture( )
    {
        return getProperty( "cultures", null );
    }

//    public final List<String> getCultures( )
//    {
//        String culture = getProperty( "cultures", null );
//        if( culture == null )
//    }
    
    public final void setCulture( String value )
    {
        setProperty( "cultures", value );
    }

    public final String getPlatform( )
    {
        return getProperty( "platforms", null );
    }
    
    public final void setPlatform( String value )
    {
        setProperty( "platforms", value );
    }

    private void setProperty( String key, String value )
    {
           info.setProperty( key, value );
    }

    public final String getProperty( String key, String defaultValue )
    {
        if ( key == null )
        {
            return defaultValue;
        }
        return info.getProperty( key, defaultValue );
    }

    public final String getACProperty( String key, String defaultValue, String arch, String culture )
    {
        if ( key == null )
        {
            return defaultValue;
        }
        String value = info.getProperty( key, defaultValue );
        if ( value == null || value.isEmpty() )
        	return value;
        value.replaceAll("{{Arch}}", arch);
        value.replaceAll("{{Culture}}", culture);
        return value;
    }
}
