package fr.paris.lutece.maven;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.war.WarArchiver;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Assembly zips for Lutece core or plugin project.<br/> If you wish to force
 * webapp re-creation (for instance, if you changed the version of a
 * dependency), call the <code>clean</code> phase before this goal.
 *
 * @goal assembly
 * @execute phase="process-classes"
 * @requiresDependencyResolution compile
 */
public class AssemblyMojo
    extends AbstractLuteceWebappMojo
{
    private static final String WEB_INF_WEB_XML_PATH = "/WEB-INF/web.xml";
    private static final String WEB_INF_LIB_PATH = "WEB-INF/lib/";
    private static final String WEB_INF_PATH = "WEB-INF/";
    private static final String WEBAPP_WEB_INF_LIB_PATH = "webapp/WEB-INF/lib/";

    //The path to the classes directory
    private static final String WEB_INF_CLASSES_PATH = "WEB-INF/classes/";
    private static final String JUNIT = "junit";
    private static final String SERVELT_API = "servlet-api";

    /**
     * The directory containing the site resource files.
     *
     * @parameter expression="${basedir}/src/site/resources"
     * @required
     */
    private File resourcesDirectory;

    /**
     * The directory containing the source files.
     *
     * @parameter expression="${basedir}/src"
     * @required
     */
    private File sourcesDirectory;

    /**
     * The name of the generated artifact.
     *
     * @parameter expression="${project.build.finalName}"
     * @required
     */
    private String artifactName;

    /**
     * The project's output directory.
     *
     * @parameter expression="${assemblyOutputDirectory}"
     */
    protected File assemblyOutputDirectory;

    /**
     * Whether creating the archives should be forced.
     *
     * @parameter expression="${jar.forceCreation}" default-value="false"
     */
    private boolean forceCreation;

    /**
     * The Zip archiver.
     *
     * @parameter expression="${component.org.codehaus.plexus.archiver.Archiver#zip}"
     * @required
     */
    private ZipArchiver zipSrcArchiver;

    /**
     * The Zip archiver.
     *
     * @parameter expression="${component.org.codehaus.plexus.archiver.Archiver#zip}"
     * @required
     */
    private ZipArchiver zipBinArchiver;

    /**
     * The Jar archiver.
     *
     * @parameter expression="${component.org.codehaus.plexus.archiver.Archiver#jar}"
     * @required
     */
    private JarArchiver jarArchiver;

    /**
     * The War archiver.
     *
     * @parameter expression="${component.org.codehaus.plexus.archiver.Archiver#war}"
     * @required
     */
    private WarArchiver warArchiver;

    /**
     * The maven archive configuration to use.
     *
     * @see <a
     *      href="http://maven.apache.org/ref/current/maven-archiver/apidocs/org/apache/maven/archiver/MavenArchiveConfiguration.html">the
     *      Javadocs for MavenArchiveConfiguration</a>.
     *
     * @parameter
     */
    private MavenArchiveConfiguration archiveCfg = new MavenArchiveConfiguration(  );

    /**
     * Executes the mojo on the current project.
     *
     * @throws MojoExecutionException
     *             if an error occured while building the artifact.
     */
    @Override
    public void execute(  )
                 throws MojoExecutionException, MojoFailureException
    {
        if ( ! LUTECE_CORE_PACKAGING.equals( project.getPackaging(  ) ) &&
                 ! LUTECE_PLUGIN_PACKAGING.equals( project.getPackaging(  ) ) )
        {
            throw new MojoExecutionException( "This goal can be invoked only on a " + LUTECE_CORE_PACKAGING + " or " +
                                              LUTECE_PLUGIN_PACKAGING + " project." );
        } else
        {
            getLog(  ).info( "Assembly " + project.getArtifact(  ).getType(  ) + " artifact..." );
            assemblyBinaries(  );
            assemblySources(  );
        }
    }

    private File getOutputDirectory(  )
    {
        String strPath = "";

        if ( ( assemblyOutputDirectory == null ) || "".equals( assemblyOutputDirectory ) )
        {
            strPath = outputDirectory.getAbsolutePath(  );
        } else
        {
            strPath = assemblyOutputDirectory.getAbsolutePath(  );
        }

        strPath += ( File.separatorChar + project.getArtifactId(  ) );

        return new File( strPath );
    }

    /**
     * Create a zip with binaries files.
     *
     * @throws MojoExecutionException
     *             if an error occured while building the artifact.
     */
    private void assemblyBinaries(  )
                           throws MojoExecutionException
    {
        try
        {
            // Get the project type
            String projectType = project.getArtifact(  ).getType(  );

            MavenArchiver archiver = new MavenArchiver(  );

            // Create the jar file, containing compiled classes
            File jarFile = getArchiveFile( null, false, "jar" );
            jarArchiver.reset(  );
            archiver.setArchiver( jarArchiver );
            archiver.setOutputFile( jarFile );

            if ( ! classesDirectory.exists(  ) )
            {
                getLog(  ).warn( "Could not find classes directory " + classesDirectory.getAbsolutePath(  ) );
            } else
            {
                archiver.getArchiver(  )
                        .addDirectory( classesDirectory, PACKAGE_CLASSES_INCLUDES, PACKAGE_CLASSES_EXCLUDES );
            }

            archiver.createArchive( project, archiveCfg );

            // Create core war
            File warFile = getArchiveFile( null, false, "war" );

            if ( LUTECE_CORE_TYPE.equals( projectType ) )
            {
                archiver = new MavenArchiver(  );
                archiver.setArchiver( warArchiver );
                archiver.setOutputFile( warFile );

                if ( ! webappSourceDirectory.exists(  ) )
                {
                    getLog(  ).warn( "Could not find webapp directory " + webappSourceDirectory.getAbsolutePath(  ) );
                } else
                {
                    // we have to add the web.xml reference to build the webapp
                    warArchiver.setWebxml( new File( webappSourceDirectory.getPath(  ) + WEB_INF_WEB_XML_PATH ) );
                    // add the builded webapp to the archive
                    warArchiver.addDirectory( webappSourceDirectory, ASSEMBLY_WEBAPP_INCLUDES, ASSEMBLY_WEBAPP_EXCLUDES );
                }

                if ( ! sqlDirectory.exists(  ) )
                {
                    getLog(  ).warn( "Could not find core jar file " + sqlDirectory.getAbsolutePath(  ) );
                } else
                {
                    // add the sql directory to the archive
                    warArchiver.addDirectory( sqlDirectory.getParentFile(  ),
                                              WEB_INF_PATH,
                                              ASSEMBLY_WEBAPP_INCLUDES,
                                              new String[]
                                              {
                                                  EXCLUDE_PATTERN_JAVA, EXCLUDE_PATTERN_SITE, EXCLUDE_PATTERN_SVN
                                              } );
                }

                if ( ! classesDirectory.exists(  ) )
                {
                    getLog(  ).warn( "Could not find classes directory " + classesDirectory.getAbsolutePath(  ) );
                } else
                {
                    //add the Classes resources directories to the archive
                    warArchiver.addDirectory( classesDirectory, WEB_INF_CLASSES_PATH, PACKAGE_WEBAPP_RESOURCES_INCLUDE,
                                              PACKAGE_WEBAPP_RESOURCES_EXCLUDES );
                }

                if ( ! siteDirectory.exists(  ) )
                {
                    getLog(  ).warn( "Could not find core jar file " + siteDirectory.getAbsolutePath(  ) );
                } else
                {
                    // add the site user directories to the archive
                    warArchiver.addDirectory( siteDirectory, WEB_INF_DOC_XML_PATH, ASSEMBLY_WEBAPP_SITE_INCLUDES,
                                              ASSEMBLY_WEBAPP_SITE_EXCLUDES );
                }

                if ( ! jarFile.exists(  ) )
                {
                    getLog(  ).warn( "Could not find core jar file " + webappSourceDirectory.getAbsolutePath(  ) );
                } else
                {
                    // add the jar to the archive
                    warArchiver.addFile( jarFile, WEB_INF_LIB_PATH + jarFile.getName(  ) );
                }

                // Add dependant jars
                for ( File f : getDependentJars(  ) )
                {
                    if ( ( null != f ) && f.exists(  ) )
                    {
                        warArchiver.addFile( f, WEB_INF_LIB_PATH + f.getName(  ) );
                    }
                }

                archiver.createArchive( project, archiveCfg );
            }

            // Create the final zip file
            File webappZip = getArchiveFile( ( LUTECE_CORE_TYPE.equals( projectType ) ? "war" : "bin" ), true, "zip" );
            zipBinArchiver.reset(  );
            zipBinArchiver.setCompress( true );
            zipBinArchiver.setForced( forceCreation );
            zipBinArchiver.setDestFile( webappZip );

            if ( LUTECE_CORE_TYPE.equals( projectType ) )
            {
                // Add core war file
                zipBinArchiver.addFile( warFile,
                                        warFile.getName(  ) );

                // Add the sql directory
                if ( sourcesDirectory.exists(  ) )
                {
                    zipBinArchiver.addDirectory( sourcesDirectory,
                                                 new String[] { INCLUDE_PATTERN_SQL },
                                                 new String[] { EXCLUDE_PATTERN_SVN, EXCLUDE_PATTERN_JAVA } );
                }
            } else if ( LUTECE_PLUGIN_TYPE.equals( projectType ) )
            {
                // Add the resource files
                if ( resourcesDirectory.exists(  ) )
                {
                    zipBinArchiver.addDirectory( resourcesDirectory, ASSEMBLY_WEBAPP_INCLUDES, ASSEMBLY_WEBAPP_EXCLUDES );
                }

                // Add the webapp files
                if ( webappSourceDirectory.exists(  ) )
                {
                    zipBinArchiver.addDirectory( webappSourceDirectory, ASSEMBLY_WEBAPP_INCLUDES,
                                                 ASSEMBLY_WEBAPP_EXCLUDES );
                }

                // Add the sql directory to WEB-INF folder
                if ( sqlDirectory.exists(  ) )
                {
                    zipBinArchiver.addDirectory( sqlDirectory.getParentFile(  ),
                                                 WEB_INF_PATH,
                                                 ASSEMBLY_WEBAPP_INCLUDES,
                                                 new String[]
                                                 {
                                                     EXCLUDE_PATTERN_JAVA, EXCLUDE_PATTERN_ASSEMBLY,
                                                     EXCLUDE_PATTERN_SITE, EXCLUDE_PATTERN_TEST, EXCLUDE_PATTERN_SVN
                                                 } );
                }

                //add the Classes resources directories to the archive
                if ( classesDirectory.exists(  ) )
                {
                    zipBinArchiver.addDirectory( classesDirectory, WEB_INF_CLASSES_PATH,
                                                 PACKAGE_WEBAPP_RESOURCES_INCLUDE, PACKAGE_WEBAPP_RESOURCES_EXCLUDES );
                }

                //Add the site user directories to WEB-INF/doc/xml/ folder
                if ( siteDirectory.exists(  ) )
                {
                    zipBinArchiver.addDirectory( siteDirectory, WEB_INF_DOC_XML_PATH, ASSEMBLY_WEBAPP_SITE_INCLUDES,
                                                 ASSEMBLY_WEBAPP_SITE_EXCLUDES );
                }

                // Add jar
                zipBinArchiver.addFile( jarFile, WEB_INF_LIB_PATH + jarFile.getName(  ) );

                // Add the dependency libraries
                for ( File f : getDependentJars(  ) )
                {
                    if ( ( null != f ) && f.exists(  ) )
                    {
                        zipBinArchiver.addFile( f, WEB_INF_LIB_PATH + f.getName(  ) );
                    }
                }
            }

            // Finaly build the zip file.
            zipBinArchiver.createArchive(  );

            // Delete temp files
            if ( jarFile.exists(  ) )
            {
                jarFile.delete(  );
            }

            if ( LUTECE_CORE_TYPE.equals( projectType ) && warFile.exists(  ) )
            {
                warFile.delete(  );
            }
        } catch ( Exception e )
        {
            throw new MojoExecutionException( "Error assembling ZIP", e );
        }
    }

    /**
     * Create a zip with the source files.
     *
     * @throws MojoExecutionException
     */
    private void assemblySources(  )
                          throws MojoExecutionException
    {
        try
        {
            // Prepare the source zip file
            File webappZip = getArchiveFile( "src", true, "zip" );
            zipSrcArchiver.reset(  );
            zipSrcArchiver.setCompress( true );
            zipSrcArchiver.setForced( forceCreation );
            zipSrcArchiver.setDestFile( webappZip );

            // Add the source files to the zip
            if ( ! sourcesDirectory.exists(  ) )
            {
                getLog(  ).warn( "Could not find webapp directory " + sourcesDirectory.getAbsolutePath(  ) );
            } else
            {
                zipSrcArchiver.addDirectory( sourcesDirectory.getParentFile(  ),
                                             new String[] { INCLUDE_PATTERN_SRC },
                                             new String[]
                                             {
                                                 EXCLUDE_PATTERN_SVN, EXCLUDE_PATTERN_TARGET, EXCLUDE_PATTERN_WEBAPP
                                             } );
            }

            // Add the webapp files to the zip
            if ( ! webappSourceDirectory.exists(  ) )
            {
                getLog(  ).warn( "Could not find webapp directory " + webappSourceDirectory.getAbsolutePath(  ) );
            } else
            {
                zipSrcArchiver.addDirectory( webappSourceDirectory.getParentFile(  ),
                                             new String[] { INCLUDE_PATTERN_WEBAPP },
                                             new String[] { EXCLUDE_PATTERN_SVN, EXCLUDE_PATTERN_CLASSES } );
            }

            // Add the dependency libraries to the zip
            for ( File f : getDependentJars(  ) )
            {
                if ( ( null != f ) && f.exists(  ) )
                {
                    zipSrcArchiver.addFile( f, WEBAPP_WEB_INF_LIB_PATH + f.getName(  ) );
                }
            }

            zipSrcArchiver.createArchive(  );
        } catch ( Exception e )
        {
            throw new MojoExecutionException( "Error assembling ZIP", e );
        }
    }

    /**
     * Builds the name of the destination ZIP file with a timestamp if
     * necessary.
     *
     * @param classifier
     *            The type of target appears in the name (bin or src)
     * @param timestamp
     *            Tell if the file name must contain timestamp.
     * @param extension
     *            The extension of tager file.
     * @return new File build with params.
     */
    private File getArchiveFile( String classifier, boolean timestamp, String extension )
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat( "yyMMdd-hhmm" );
        dateFormat.format( new Date(  ) ).toString(  );

        return new File( getOutputDirectory(  ),
                         artifactName + ( ( null != classifier ) ? ( "-" + classifier ) : "" ) +
                         ( timestamp ? ( "-" + dateFormat.format( new Date(  ) ).toString(  ) ) : "" ) + "." +
                         extension );
    }

    /**
     * Get the collection of non lutece-core and non lutece-plugin jars.
     *
     * @return Collection of jar
     */
    @SuppressWarnings( "unchecked" )
    private Collection<File> getDependentJars(  )
    {
        HashSet<File> result = new HashSet<File>(  );

        // Direct dependency artifacts of project
        Set<Artifact> resultArtifact = new HashSet<Artifact>(  );

        for ( Object o : project.getDependencyArtifacts(  ) )
        {
            Artifact a = null;

            try
            {
                a = (Artifact) o;
            } catch ( ClassCastException e )
            {
                e.printStackTrace(  );

                continue;
            }

            if ( ! LUTECE_CORE_TYPE.equals( a.getType(  ) ) &&
                     ! LUTECE_PLUGIN_TYPE.equals( a.getType(  ) ) &&
                     ! Artifact.SCOPE_PROVIDED.equals( a.getScope(  ) ) &&
                     ! Artifact.SCOPE_TEST.equals( a.getScope(  ) ) )
            {
                resultArtifact.add( a );
                result.add( a.getFile(  ) );
            }
        }

        // add the transitive dependency
        ArtifactResolutionResult artifactResolutionResult = null;

        try
        {
            artifactResolutionResult =
                resolver.resolveTransitively( resultArtifact,
                                              project.getArtifact(  ),
                                              remoteRepositories,
                                              localRepository,
                                              metadataSource );
        } catch ( ArtifactResolutionException e )
        {
            e.printStackTrace(  );
        } catch ( ArtifactNotFoundException e )
        {
            e.printStackTrace(  );
        }

        for ( Object o : artifactResolutionResult.getArtifacts(  ) )
        {
            Artifact a = null;

            try
            {
                a = (Artifact) o;
            } catch ( ClassCastException e )
            {
                e.printStackTrace(  );

                continue;
            }

            if ( ! Artifact.SCOPE_PROVIDED.equals( a.getScope(  ) ) &&
                     ! Artifact.SCOPE_TEST.equals( a.getScope(  ) )//for transitively dependencies artifact are not a good scope ( junit and servlet-api )
                      &&
                     ! JUNIT.equals( a.getArtifactId(  ) ) &&
                     ! SERVELT_API.equals( a.getArtifactId(  ) ) )
            {
                result.add( a.getFile(  ) );
            }
        }

        return result;
    }
}
