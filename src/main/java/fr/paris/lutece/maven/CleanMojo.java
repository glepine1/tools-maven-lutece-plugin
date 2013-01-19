package fr.paris.lutece.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Goal which cleans the build.
 * Especially for the multi-modules projects.
 *
 * <P>
 * This attempts to clean a project's working directory of the files that were
 * generated at build-time. By default, it discovers and deletes the directories
 * configured in <code>project.build.directory</code>,
 * <code>project.build.outputDirectory</code>,
 * <code>project.build.testOutputDirectory</code>, and
 * <code>project.reporting.outputDirectory</code>.
 * </P>
 *
 * <P>
 * Files outside the default may also be included in the deletion by configuring
 * the <code>filesets</code> tag.
 * </P>
 *
 * @goal clean
 *
 * @see org.apache.maven.plugin.clean.Fileset.
 */
public class CleanMojo
    extends AbstractLuteceWebappMojo
{
    /**
     * This is where compiled test classes go.
     *
     * @parameter expression="${project.build.directory}/test-classes"
     * @required
     * @readonly
     */
    private File testOutputDirectory;

    /**
     * This is where the site plugin generates its pages.
     *
     * @parameter expression="${project.build.directory}/site"
     * @required
     * @readonly
     */
    private File reportDirectory;

    /**
     * Sets whether the plugin runs in verbose mode.
     *
     * @parameter expression="${clean.verbose}" default-value="false"
     */
    private boolean verbose;

    /**
     * The list of fileSets to delete, in addition to the default directories.
     *
     * @parameter
     */
    private List<FileSet> filesets;

    /**
     * Sets whether the plugin should follow Symbolic Links to delete files.
     *
     * @parameter expression="${clean.followSymLinks}" default-value="false"
     */
    private boolean followSymLinks;

    /**
     * Finds and retrieves included and excluded files, and handles their
     * deletion
     */
    private FileSetManager fileSetManager;

    /**
     * Deletes file-sets in the following project build directory order:
     * (source) directory, output directory, test directory, report directory,
     * and then the additional file-sets.
     *
     * @see org.apache.maven.plugin.Mojo#execute()
     * @throws MojoExecutionException
     *             When
     */
    public void execute(  )
                 throws MojoExecutionException
    {
        // for remove the global target before the modules target
        if ( ( reactorProjects.size(  ) > 1 ) && ( reactorProjects.indexOf( project ) == 0 ) )
        {
            //Delete the global project target
            fileSetManager = new FileSetManager( getLog(  ),
                                                 verbose );
            removeDirectory( getRootProjectBuildDirectoryTarget(  ) );
            removeAdditionalFilesets(  );
        }

        if ( ! POM_PACKAGING.equals( project.getPackaging(  ) ) ) // For not delete the global target after the modules target
        {
            //delete the current project target
            fileSetManager = new FileSetManager( getLog(  ),
                                                 verbose );
            removeDirectory( classesDirectory );
            removeDirectory( testOutputDirectory );
            removeDirectory( reportDirectory );
            removeDirectory( outputDirectory );
            removeAdditionalFilesets(  );
        }
    }

    /**
     * Deletes additional file-sets specified by the <code>filesets</code>
     * tag.
     *
     * @throws MojoExecutionException
     *             When a directory failed to get deleted.
     */
    private void removeAdditionalFilesets(  )
                                   throws MojoExecutionException
    {
        if ( ( filesets != null ) && ! filesets.isEmpty(  ) )
        {
            for ( Iterator it = filesets.iterator(  ); it.hasNext(  ); )
            {
                FileSet fileset = (FileSet) it.next(  );

                try
                {
                    getLog(  ).info( "Deleting " + fileset );

                    fileSetManager.delete( fileset );
                } catch ( IOException e )
                {
                    throw new MojoExecutionException( "Failed to delete directory: " + fileset.getDirectory(  ) +
                                                      ". Reason: " + e.getMessage(  ), e );
                }
            }
        }
    }

    /**
     * Deletes a directory and its contents.
     *
     * @param dir
     *            The base directory of the included and excluded files.
     * @throws MojoExecutionException
     *             When a directory failed to get deleted.
     */
    private void removeDirectory( File dir )
                          throws MojoExecutionException
    {
        if ( dir != null )
        {
            if ( ! dir.exists(  ) )
            {
                return;
            }

            if ( ! dir.isDirectory(  ) )
            {
                throw new MojoExecutionException( dir + " is not a directory." );
            }

            FileSet fs = new FileSet(  );
            fs.setDirectory( dir.getPath(  ) );
            fs.addInclude( "**/**" );
            fs.setFollowSymlinks( followSymLinks );

            try
            {
                getLog(  ).info( "Deleting directory " + dir.getAbsolutePath(  ) );
                fileSetManager.delete( fs );
            } catch ( IOException e )
            {
                throw new MojoExecutionException( "Failed to delete directory: " + dir + ". Reason: " +
                                                  e.getMessage(  ), e );
            } catch ( IllegalStateException e )
            {
                // TODO: IOException from plexus-utils should be acceptable here
                throw new MojoExecutionException( "Failed to delete directory: " + dir + ". Reason: " +
                                                  e.getMessage(  ), e );
            }
        }
    }

    /**
     * Sets the project build test output directory.
     *
     * @param newTestOutputDirectory
     *            The project build test output directory to set.
     */
    protected void setTestOutputDirectory( File newTestOutputDirectory )
    {
        this.testOutputDirectory = newTestOutputDirectory;
    }

    /**
     * Sets the project build report directory.
     *
     * @param newReportDirectory
     *            The project build report directory to set.
     */
    protected void setReportDirectory( File newReportDirectory )
    {
        this.reportDirectory = newReportDirectory;
    }

    /**
     * Adds a file-set to the list of file-sets to clean.
     *
     * @param fileset
     *            the fileset
     */
    public void addFileset( FileSet fileset )
    {
        if ( filesets == null )
        {
            filesets = new LinkedList<FileSet>(  );
        }

        filesets.add( fileset );
    }
}
