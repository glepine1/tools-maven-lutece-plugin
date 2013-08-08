package fr.paris.lutece.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.TypeArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import org.codehaus.plexus.archiver.zip.ZipUnArchiver;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Abstracts functionnality common to mojos that create a Lutece webapp from
 * Lutece artifacts.
 */
public abstract class AbstractLuteceWebappMojo
    extends AbstractLuteceMojo
{
    /**
     * The directory containing the local, user-specific configuration files.
     *
     * @parameter default-value="${user.home}/lutece/conf/${project.artifactId}"
     */
    protected File localConfDirectory;

    /**
     * The set of artifacts required by this project, including transitive
     * dependencies.
     *
     * @parameter default-value="${project.artifacts}"
     * @required
     * @readonly
     */
    protected Set<Artifact> artifacts;

    /**
     * The unarchiver.
     *
     * @parameter expression="${component.org.codehaus.plexus.archiver.UnArchiver#zip}"
     * @required
     */
    private ZipUnArchiver unArchiver;

    /**
     * The artifact factory.
     *
     * @component
     */
    private org.apache.maven.artifact.factory.ArtifactFactory artifactFactory;

    /**
     * The artifact resolver.
     *
     * @component
     */
    protected org.apache.maven.artifact.resolver.ArtifactResolver resolver;

    /**
     * The local repository.
     *
     * @parameter expression="${localRepository}"
     */
    protected org.apache.maven.artifact.repository.ArtifactRepository localRepository;

    /**
     * The remote repositories.
     *
     * @parameter expression="${project.remoteArtifactRepositories}"
     */
    protected java.util.List<ArtifactRepository> remoteRepositories;

    /**
     * The artifact metadata source.
     *
     *  @component
     */
    protected ArtifactMetadataSource metadataSource;

    /**
     * The directory where to explode the test webapp.
     *
     * @parameter expression="${testWebappDirectory}" default-value="${project.build.directory}/lutece"
     */
    protected File testWebappDirectory;

    /**
     * Creates an exploded webapp structure from the current project.
     *
     * @param targetDir
     *            the destination directory.
     * @throws MojoExecutionException
     *             if an error occurs while exploding the webapp.
     */
    protected void explodeWebapp( File targetDir )
                          throws MojoExecutionException
    {
        try
        {
            // Indicates whether we perform in-place deployment (ie the webapp
            // is assembled in the webapp source directory)
            boolean isInplace = targetDir.equals( webappSourceDirectory );

            // Indicates whether the exploded webapp already exists
            boolean isUpdate = targetDir.exists(  );

            getLog(  ).info( ( isUpdate ? "Updating" : "Exploding" ) + " webapp in " + targetDir + "..." );

            // Create the directory if necessary
            targetDir.mkdirs(  );

            if ( ! isInplace && ! isUpdate )
            {
                // Explode the lutece-core artifact
                explodeCore( targetDir );

                // Explode all lutece-plugin artifacts
                explodePlugins( targetDir );
            }

            // Copy third-party JARs
            copyThirdPartyJars( targetDir );

            if ( ! isInplace && webappSourceDirectory.exists(  ) )
            {
                // Copy project-specific webapp components
                if ( ! isUpdate )
                {
                    // First deployment : copy all files, in case
                    // project-specific files are meant to overwrite files from
                    // the core or the plugins.
                    FileUtils.copyDirectoryStructure( webappSourceDirectory, targetDir );

                    if ( FileUtils.getNbFileCopy(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to copy - all webapp files are up to date" );
                    } else
                    {
                        // we can't know how many file has been copied anymore
                        // due to plexus fileUtils
                        getLog(  ).info( "Copying webapp files" );
                    }

                    FileUtils.setNbFileCopy( 0 );
                } else
                {
                    // This time only overwrite newer files, since we are sure
                    // that all files common with the core have either been
                    // overwritten at webapp creation, or are older
                    FileUtils.copyDirectoryStructureIfModified( webappSourceDirectory, targetDir );

                    if ( FileUtils.getNbFileModified(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to update - all webapp files are up to date" );
                    } else
                    {
                        getLog(  ).info( "Copying " + FileUtils.getNbFileModified(  ) + " webapp files" );
                    }

                    FileUtils.setNbFileModified( 0 );
                }
            }

            // Copy SQL files
            if ( ! isInplace && sqlDirectory.exists(  ) )
            {
                getLog(  ).debug( "Copying SQL files from " + sqlDirectory.getAbsolutePath(  ) );

                File sqlTargetDir = new File( targetDir, WEB_INF_SQL_PATH );

                if ( ! isUpdate )
                {
                    FileUtils.copyDirectoryStructure( sqlDirectory, sqlTargetDir );

                    if ( FileUtils.getNbFileCopy(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to copy - all sql files are up to date" );
                    } else
                    {
                        getLog(  ).info( "Copying " + FileUtils.getNbFileCopy(  ) + " sql files" );
                    }

                    FileUtils.setNbFileCopy( 0 );
                } else
                {
                    // This time only overwrite newer files, since we are sure
                    // that all files common with the core have either been
                    // overwritten at webapp creation, or are older
                    FileUtils.copyDirectoryStructureIfModified( sqlDirectory, sqlTargetDir );

                    if ( FileUtils.getNbFileModified(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to update - all sql files are up to date" );
                    } else
                    {
                        getLog(  ).info( "Copying " + FileUtils.getNbFileModified(  ) + " sql files" );
                    }

                    FileUtils.setNbFileModified( 0 );
                }
            }

            //Copy Site User files
            if ( ! isInplace && siteDirectory.exists(  ) )
            {
                getLog(  ).debug( "Copying Site User files from " + siteDirectory.getAbsolutePath(  ) );

                File siteUserTargetDir = new File( targetDir, WEB_INF_DOC_XML_PATH );

                if ( ! isUpdate )
                {
                    FileUtils.copyDirectoryStructure( siteDirectory, siteUserTargetDir );

                    if ( FileUtils.getNbFileCopy(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to copy - all site user files are up to date" );
                    } else
                    {
                        getLog(  ).info( "Copying " + FileUtils.getNbFileCopy(  ) + " site user files" );
                    }

                    FileUtils.setNbFileCopy( 0 );
                } else
                {
                    // This time only overwrite newer files, since we are sure
                    // that all files common with the core have either been
                    // overwritten at webapp creation, or are older
                    FileUtils.copyDirectoryStructureIfModified( siteDirectory, siteUserTargetDir );

                    if ( FileUtils.getNbFileModified(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to update - all site user files are up to date" );
                    } else
                    {
                        getLog(  ).info( "Copying " + FileUtils.getNbFileModified(  ) + " site user files" );
                    }

                    FileUtils.setNbFileModified( 0 );
                }
            }

            // Copy compiled classes
            if ( classesDirectory.exists(  ) )
            {
                File classesDir = new File( targetDir, "WEB-INF/classes" );
                classesDir.mkdirs(  );
                FileUtils.copyDirectoryStructureIfModified( classesDirectory, classesDir );

                if ( FileUtils.getNbFileModified(  ) == 0 )
                {
                    getLog(  ).info( "Nothing to update - all classe and resource files are up to date" );
                } else
                {
                    getLog(  ).info( "Copying " + FileUtils.getNbFileModified(  ) + " classe and resource files" );
                }

                FileUtils.setNbFileModified( 0 );
            }
        } catch ( IOException e )
        {
            // Use the same catch block for all IOExceptions, presumably the
            // exception's message will be clear enough.
            throw new MojoExecutionException( "Error while copying resources", e );
        }
    }

    protected void explodeConfigurationFiles( File targetDir )
                                      throws MojoExecutionException
    {
        try
        {
            // Copy default configuration files
            if ( defaultConfDirectory.exists(  ) )
            {
                getLog(  ).debug( "Copying default configuration from " + defaultConfDirectory.getAbsolutePath(  ) );
                FileUtils.copyDirectoryStructure( defaultConfDirectory, targetDir );
            } else
            {
                getLog(  )
                    .warn( "Default configuration directory " + defaultConfDirectory.getAbsolutePath(  ) +
                           " does not exist" );
            }

            // Copy user-specific configuration files
            getLog(  ).info( "Local configuration directory is " + localConfDirectory.getAbsolutePath(  ) );

            if ( localConfDirectory.exists(  ) )
            {
                getLog(  ).debug( "Copying local configuration" );
                FileUtils.copyDirectoryStructure( localConfDirectory, targetDir );
            } else
            {
                getLog(  )
                    .warn( "Local configuration directory " + localConfDirectory.getAbsolutePath(  ) +
                           " does not exist" );
            }
        } catch ( IOException e )
        {
            // Use the same catch block for all IOExceptions, presumably the
            // exception's message will be clear enough.
            throw new MojoExecutionException( "Error while copying configuration resources", e );
        }
    }

    /**
     * Add the lutece-core dependency's files to an exploded webapp directory.
     *
     * @param webappDir
     *            the webapp directory.
     *
     * @throws MojoExecutionException
     *             if there is no lutece-core dependency, or more than one, or
     *             if an error occurs while resolving the artifact.
     */
    private void explodeCore( File webappDir )
                      throws MojoExecutionException
    {
        // Get all the lutece-core artifacts from the project
        Set cores = filterArtifacts( new TypeArtifactFilter( LUTECE_CORE_TYPE ) );

        // There must be exactly one
        if ( LUTECE_CORE_TYPE.equals( project.getArtifactId(  ) ) )
        {
            return;
        } else if ( ( cores == null ) || cores.isEmpty(  ) || ( cores.size(  ) > 1 ) )
        {
            throw new MojoExecutionException( "Project \"" + project.getName(  ) +
                                              "\" must have exactly one dependency of type " + LUTECE_CORE_TYPE );
        }

        // Now we know the Set has exactly one element
        Artifact coreArtifact = (Artifact) cores.iterator(  ).next(  );

        addToExplodedWebapp( coreArtifact, webappDir );
    }

    /**
     * Add the lutece-plugin dependencies' files to an exploded webapp
     * directory.
     *
     * @param webappDir
     *            the webapp directory.
     *
     * @throws MojoExecutionException
     *             if an error occurs while resolving the artifacts.
     */
    private void explodePlugins( File webappDir )
                         throws MojoExecutionException
    {
        // Get all the lutece-plugin artifacts from the project
        Set plugins = filterArtifacts( new TypeArtifactFilter( LUTECE_PLUGIN_TYPE ) );

        // Explode each artifact file
        for ( Iterator iterArtifacts = plugins.iterator(  ); iterArtifacts.hasNext(  ); )
        {
            Artifact pluginArtifact = (Artifact) iterArtifacts.next(  );
            addToExplodedWebapp( pluginArtifact, webappDir );
        }
    }

    /**
     * Copy third-party JARs to an exploded webapp directory.
     *
     * @param webappDir
     *            the webapp directory
     *
     * @throws MojoExecutionException
     *             if an error occurs while copying the files.
     */
    protected void copyThirdPartyJars( File webappDir )
                             throws MojoExecutionException
    {
        File webinfLib = new File( webappDir, "WEB-INF/lib" );
        webinfLib.mkdirs(  );

        // Filter jar artifacts in scope 'compile' or 'runtime'
        ArtifactFilter thirdPartyFilter =
            new ArtifactFilter(  )
            {
                @Override
                public boolean include( Artifact artifact )
                {
                    return ( "jar".equals( artifact.getType(  ) ) &&
                           ( 
                               Artifact.SCOPE_RUNTIME.equals( artifact.getScope(  ) ) ||
                               Artifact.SCOPE_COMPILE.equals( artifact.getScope(  ) )
                            ) );
                }
            };

        // if multi project
        if ( ( reactorProjects.size(  ) > 1 ) && ! project.isExecutionRoot(  ) )
        {
            //add dependencies of modules for filter duplicate entry
            multiProjectArtifacts.addAll( filterArtifacts( thirdPartyFilter ) );
        } else
        { // no in multi project

            Set thirdPartyJARs = filterArtifacts( thirdPartyFilter );

            for ( Iterator iterJARs = thirdPartyJARs.iterator(  ); iterJARs.hasNext(  ); )
            {
                Artifact artifact = (Artifact) iterJARs.next(  );
                File jarFile = artifact.getFile(  );
                File newFile = new File( webinfLib,
                                         jarFile.getName(  ) );

                try
                {
                    FileUtils.copyFileIfModified( jarFile, newFile );
                } catch ( IOException e )
                {
                    throw new MojoExecutionException( "Error while copying " + jarFile.getAbsolutePath(  ) + " to " +
                                                      newFile.getAbsolutePath(  ), e );
                }
            }
        }
    }

    /**
     * Adds a Lutece artifact to an exploded webapp.
     *
     * @param luteceArtifact
     *            the Lutece artifact.
     * @param webappDir
     *            the exploded webapp's base directory.
     *
     * @throws MojoExecutionException
     *             if an error occurs while unpacking.
     */
    protected void addToExplodedWebapp( Artifact luteceArtifact, File webappDir )
                                throws MojoExecutionException
    {
        // Copy the artifact's main JAR to WEB-INF/lib
        File repoJar = luteceArtifact.getFile(  );

        File webinfLib = new File( webappDir, "WEB-INF/lib" );
        webinfLib.mkdirs(  );

        File webinfJar = new File( webinfLib,
                                   repoJar.getName(  ) );

        try
        {
            FileUtils.copyFileIfModified( repoJar, webinfJar );
        } catch ( IOException e )
        {
            throw new MojoExecutionException( "Error while copying " + repoJar.getAbsolutePath(  ) + " to " +
                                              webinfJar.getAbsolutePath(  ), e );
        }

        // Every Lutece artifact has an attached webapp artifact
        Artifact webappArtifact =
            artifactFactory.createArtifactWithClassifier( luteceArtifact.getGroupId(  ),
                                                          luteceArtifact.getArtifactId(  ),
                                                          luteceArtifact.getVersion(  ),
                                                          "zip",
                                                          WEBAPP_CLASSIFIER );

        // Resolve the webapp artifact
        try
        {
            resolver.resolve( webappArtifact, remoteRepositories, localRepository );
        } catch ( Exception e )
        {
            throw new MojoExecutionException( "Error while resolving artifact " + webappArtifact, e );
        }

        // Unzip the webapp artifact to the webapp directory
        try
        {
            unArchiver.setSourceFile( webappArtifact.getFile(  ) );
            unArchiver.setDestDirectory( webappDir );
            unArchiver.extract(  );
        } catch ( Exception e )
        {
            throw new MojoExecutionException( "Error while unpacking file " +
                                              webappArtifact.getFile(  ).getAbsolutePath(  ), e );
        }
    }

    /**
     * Filters the project's set of artifacts with an ArtifactFilter.
     *
     * @param type
     *            the type to retain.
     * @return a new Set containing the filtered elements.
     */
    protected Set<Artifact> filterArtifacts( ArtifactFilter filter )
    {
        Set<Artifact> result = new HashSet<Artifact>(  );

        for ( Artifact artifact : artifacts )
        {
            if ( filter.include( artifact ) )
            {
                result.add( artifact );
            }
        }

        return result;
    }

    /**
     * Used for multiproject, return the output directory ( /target/lutece/ ) of root project.
     *
     * @return File corresponding to the root project output directory
     */
    protected File getRootProjectBuildDirectory(  )
    {
        MavenProject mp = null;

        for ( Object o : reactorProjects )
        {
            mp = (MavenProject) o;

            if ( mp.isExecutionRoot(  ) )
            {
                break;
            }
        }

        return new File( mp.getBuild(  ).getDirectory(  ) + File.separatorChar + LUTECE_DIRECTORY );
    }

    /**
     * Used for multiproject, return the output directory ( /target ) of root project.
     *
     * @return File corresponding to the root project output directory
     */
    protected File getRootProjectBuildDirectoryTarget(  )
    {
        MavenProject mp = null;

        for ( Object o : reactorProjects )
        {
            mp = (MavenProject) o;

            if ( mp.isExecutionRoot(  ) )
            {
                break;
            }
        }

        return new File( mp.getBuild(  ).getDirectory(  ) );
    }
}
