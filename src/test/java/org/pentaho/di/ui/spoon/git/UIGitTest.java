package org.pentaho.di.ui.spoon.git;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;
import org.pentaho.di.ui.spoon.git.model.UIGit;

public class UIGitTest extends RepositoryTestCase {
  private UIGit uiGit;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    uiGit = new UIGit();
    uiGit.setGit( new Git( db ) );
    uiGit.setAuthorName( "test <test@example.com>" );
    uiGit.setCommitMessage( "test" );
  }

  @Test
  public void testGetAuthorName() {
    assertEquals( "test <test@example.com>", uiGit.getAuthorName() );
  }

  @Test
  public void testGetCommitMessage() {
    assertEquals( "test", uiGit.getCommitMessage() );
  }

  @Test
  public void testGetBranch() {
    assertEquals( "master", uiGit.getBranch() );
  }

  @Test
  public void testGetRemote() throws IOException, URISyntaxException {
    setupRemote();

    assertNotNull( uiGit.getRemote() );
  }

  private RemoteConfig setupRemote() throws IOException, URISyntaxException {
    // create another repository
    Repository remoteRepository = createWorkRepository();

    // set it up as a remote to this repository
    final StoredConfig config = db.getConfig();
    RemoteConfig remoteConfig = new RemoteConfig( config, Constants.DEFAULT_REMOTE_NAME );

    RefSpec refSpec = new RefSpec();
    refSpec = refSpec.setForceUpdate( true );
    refSpec = refSpec.setSourceDestination( Constants.R_HEADS + "*",
                   Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/*" );
    remoteConfig.addFetchRefSpec( refSpec );

    URIish uri = new URIish( remoteRepository.getDirectory().toURI().toURL() );
    remoteConfig.addURI( uri );

    remoteConfig.update( config );
    config.save();

    return remoteConfig;
  }
}
