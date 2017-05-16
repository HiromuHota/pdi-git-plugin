package org.pentaho.di.ui.spoon.git;

import static org.junit.Assert.*;

import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIJob;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryObjects;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UITransformation;
import org.pentaho.di.ui.spoon.git.model.UIGit;

public class UIGitTest extends RepositoryTestCase {
  private Git git;
  private UIGit uiGit;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    uiGit = new UIGit();
    uiGit.openGit( db.getDirectory().getPath() );
    uiGit.setAuthorName( "test <test@example.com>" );
    uiGit.setCommitMessage( "test" );
    git = uiGit.getGit();
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
  public void testGetRemote() throws Exception {
    RemoteConfig remote = setupRemote();

    assertEquals( remote.getURIs().get( 0 ).toString(), uiGit.getRemote() );
  }

  @Test
  public void testSetRemote() throws Exception {
    RemoteConfig remote = setupRemote();

    // assert that the added remote represents the remote repository
    assertEquals( Constants.DEFAULT_REMOTE_NAME, remote.getName() );
    assertEquals( 1, remote.getFetchRefSpecs().size() );
    assertEquals( 1, remote.getURIs().size() );
    assertEquals(
      String.format( "+refs/heads/*:refs/remotes/%s/*", Constants.DEFAULT_REMOTE_NAME ),
      remote.getFetchRefSpecs().get( 0 ).toString() );
  }

  @Test
  public void testDeleteRemote() throws Exception {
    RemoteConfig remoteConfig = setupRemote();
    RemoteConfig remote = uiGit.removeRemote();

    // assert that the removed remote is the initial remote
    assertEquals( remoteConfig.getName(), remote.getName() );
    // assert that there are no remotes left
    assertTrue( RemoteConfig.getAllRemoteConfigs( db.getConfig() ).isEmpty() );
  }

  private RemoteConfig setupRemote() throws Exception {
    // create another repository
    Repository remoteRepository = createWorkRepository();
    URIish uri = new URIish(
        remoteRepository.getDirectory().toURI().toURL() );
    return uiGit.addRemote( uri.toString() );
  }

  @Test
  public void testGetRevisionObjects() throws IOException, NoFilepatternException, GitAPIException {
    writeTrashFile( "Test.txt", "Hello world" );
    git.add().addFilepattern( "Test.txt" ).call();
    git.commit().setMessage( "initial commit" ).call();
    UIRepositoryObjectRevisions revisions = uiGit.getRevisionObjects();
    assertEquals( 1, revisions.size() );
  }

  @Test
  public void testGetUnstagedObjects() throws Exception {
    writeTrashFile( "a.ktr", "content" );
    writeTrashFile( "b.kjb", "content" );
    UIRepositoryObjects stagedObjects = uiGit.getUnstagedObjects();
    assertEquals( 2, stagedObjects.size() );
    assertEquals( UITransformation.class, stagedObjects.get( 0 ).getClass() );
    assertEquals( UIJob.class, stagedObjects.get( 1 ).getClass() );
  }

  @Test
  public void testGetStagedObjects() throws Exception {
    writeTrashFile( "a.ktr", "content" );
    writeTrashFile( "b.kjb", "content" );
    git.add().addFilepattern( "." ).call();
    UIRepositoryObjects stagedObjects = uiGit.getStagedObjects();
    assertEquals( 2, stagedObjects.size() );
    assertEquals( UITransformation.class, stagedObjects.get( 0 ).getClass() );
    assertEquals( UIJob.class, stagedObjects.get( 1 ).getClass() );
  }

  @Test
  public void testPush() throws Exception {
    // create another repository
    Repository db2 = createWorkRepository();
    URIish uri = new URIish(
      db2.getDirectory().toURI().toURL() );
    uiGit.addRemote( uri.toString() );

    // create some refs via commits and tag
    RevCommit commit = git.commit().setMessage( "initial commit" ).call();
    Ref tagRef = git.tag().setName( "tag" ).call();

    try {
      db2.resolve( commit.getId().getName() + "^{commit}" );
      fail( "id shouldn't exist yet" );
    } catch ( MissingObjectException e ) {
      // we should get here
    }

    uiGit.push();

    assertEquals( commit.getId(),
        db2.resolve( commit.getId().getName() + "^{commit}" ) );
    assertEquals( tagRef.getObjectId(),
        db2.resolve( tagRef.getObjectId().getName() ) );
  }
}
