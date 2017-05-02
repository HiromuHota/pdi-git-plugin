package org.pentaho.di.spoon.git;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIJob;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryObjects;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UITransformation;

public class GitControllerTest extends RepositoryTestCase {

  private GitController controller;
  private Git git;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    git = new Git( db );

    controller = mock( GitController.class );
    doCallRealMethod().when( controller ).getPath();
    doCallRealMethod().when( controller ).commit();
    doAnswer( new Answer<Void>() {
      @Override
      public Void answer( InvocationOnMock invocation ) throws Throwable {
        git.push().call();
        return null;
      }
    } ).when( controller ).push();
    doCallRealMethod().when( controller ).getRevisionObjects();
    doCallRealMethod().when( controller ).getStagedObjects();
    doCallRealMethod().when( controller ).getUnstagedObjects();
    doCallRealMethod().when( controller ).setGit( (Git) any() );
    when( controller.getAuthorName() ).thenReturn( "test <test@example.com>" );
    when( controller.getCommitMessage() ).thenReturn( "test" );
    controller.setGit( git );
  }

  @Test
  public void testGetPath() {
    assertEquals( db.getDirectory().getParent(), controller.getPath() );
  }

  @Test
  public void testGetRevisionObjects() throws IOException, NoFilepatternException, GitAPIException {
    writeTrashFile( "Test.txt", "Hello world" );
    git.add().addFilepattern( "Test.txt" ).call();
    git.commit().setMessage( "initial commit" ).call();
    UIRepositoryObjectRevisions revisions = controller.getRevisionObjects();
    assertEquals( 1, revisions.size() );
  }

  @Test
  public void testGetUnstagedObjects() throws Exception {
    writeTrashFile( "a.ktr", "content" );
    writeTrashFile( "b.kjb", "content" );
    UIRepositoryObjects stagedObjects = controller.getUnstagedObjects();
    assertEquals( 2, stagedObjects.size() );
    assertEquals( UITransformation.class, stagedObjects.get( 0 ).getClass() );
    assertEquals( UIJob.class, stagedObjects.get( 1 ).getClass() );
  }

  @Test
  public void testGetStagedObjects() throws Exception {
    writeTrashFile( "a.ktr", "content" );
    writeTrashFile( "b.kjb", "content" );
    git.add().addFilepattern( "." ).call();
    UIRepositoryObjects stagedObjects = controller.getStagedObjects();
    assertEquals( 2, stagedObjects.size() );
    assertEquals( UITransformation.class, stagedObjects.get( 0 ).getClass() );
    assertEquals( UIJob.class, stagedObjects.get( 1 ).getClass() );
  }

  @Test
  public void testAddToIndex() {
  }

  @Test
  public void testRemoveFromIndex() {
  }

  @Test
  public void testCommit() throws Exception {
    writeTrashFile( "a.ktr", "content" );
    git.add().addFilepattern( "." ).call();
    controller.commit();
    RevCommit commit = git.log().call().iterator().next();
    assertEquals( "test", commit.getShortMessage() );
  }

  @Test
  public void testPush() throws Exception {
    // create other repository
    Repository db2 = createWorkRepository();

    // setup the first repository
    final StoredConfig config = db.getConfig();
    RemoteConfig remoteConfig = new RemoteConfig( config, "origin" );
    URIish uri = new URIish( db2.getDirectory().toURI().toURL() );
    remoteConfig.addURI( uri );
    remoteConfig.update( config );
    config.save();

    // commit a test file
    writeTrashFile( "a.ktr", "content" );
    git.add().addFilepattern( "." ).call();
    RevCommit commit = git.commit().setAuthor( "test", "test@example.com" ).setMessage( "initial commit" ).call();

    // push
    controller.push();

    assertEquals( commit.getId(), db2.resolve( commit.getId().getName() + "^{commit}" ) );
  }

}
