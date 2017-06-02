package org.pentaho.di.ui.spoon.git.model;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.commons.vfs2.FileObject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.pentaho.di.core.vfs.KettleVFS;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;

public class UIGitTest extends RepositoryTestCase {
  private Git git;
  private UIGit uiGit;
  Repository db2;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    git = new Git( db );
    uiGit = new UIGit();
    uiGit.setGit( git );

    // create another repository
    db2 = createWorkRepository();
  }

  @Test
  public void testFindGitRepository() throws Exception {
    // Find a repo in the same dir
    File file = writeTrashFile( "a.ktr", "content" );
    FileObject f = KettleVFS.getFileObject( file.getPath() );
    assertEquals( db.getDirectory().getParent(), uiGit.findGitRepository( f.getURL().toString() ) );

    // Find a repo in the parent dir
    file = writeTrashFile( "subdir", "a.ktr", "content" );
    f = KettleVFS.getFileObject( file.getPath() );
    assertEquals( db.getDirectory().getParent(), uiGit.findGitRepository( f.getURL().toString() ) );
  }

  @Test
  public void testNotFindGitRepository() throws Exception {
    File dir = createTempDirectory( "nonGitDir" );
    File file = new File( dir, "test.ktr" );
    FileObject f = KettleVFS.getFileObject( file.getPath() );
    assertEquals( dir.getPath(), uiGit.findGitRepository( f.getURL().toString() ) );
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
    URIish uri = new URIish(
        db2.getDirectory().toURI().toURL() );
    RemoteAddCommand cmd = git.remoteAdd();
    cmd.setName( Constants.DEFAULT_REMOTE_NAME );
    cmd.setUri( uri );
    return cmd.call();
  }

  @Test
  public void testGetRevisions() throws Exception {
    writeTrashFile( "Test.txt", "Hello world" );
    git.add().addFilepattern( "Test.txt" ).call();
    git.commit().setMessage( "initial commit" ).call();
    UIRepositoryObjectRevisions revisions = uiGit.getRevisions();
    assertEquals( 1, revisions.size() );
  }

  @Test
  public void testGetUnstagedObjects() throws Exception {
    writeTrashFile( "a.ktr", "content" );
    writeTrashFile( "b.kjb", "content" );
    List<UIFile> stagedObjects = uiGit.getUnstagedObjects();
    assertEquals( 2, stagedObjects.size() );
    assertEquals( "a.ktr", stagedObjects.get( 0 ).getName() );
    assertEquals( "b.kjb", stagedObjects.get( 1 ).getName() );
  }

  @Test
  public void testGetStagedObjects() throws Exception {
    writeTrashFile( "a.ktr", "content" );
    writeTrashFile( "b.kjb", "content" );
    git.add().addFilepattern( "." ).call();
    List<UIFile> stagedObjects = uiGit.getStagedObjects();
    assertEquals( 2, stagedObjects.size() );
    assertEquals( "a.ktr", stagedObjects.get( 0 ).getName() );
    assertEquals( "b.kjb", stagedObjects.get( 1 ).getName() );
  }

  @Test
  public void testPull() throws Exception {
    // source: db2, target: db
    setupRemote();
    Git git2 = new Git( db2 );

    // put some file in the source repo and sync
    File sourceFile = new File( db2.getWorkTree(), "SomeFile.txt" );
    writeToFile( sourceFile, "Hello world" );
    git2.add().addFilepattern( "SomeFile.txt" ).call();
    git2.commit().setMessage( "Initial commit for source" ).call();
    PullResult pullResult = git.pull().call();

    // change the source file
    writeToFile( sourceFile, "Another change" );
    git2.add().addFilepattern( "SomeFile.txt" ).call();
    git2.commit().setMessage( "Some change in remote" ).call();
    git2.close();

    pullResult = uiGit.pull();

    assertFalse( pullResult.getFetchResult().getTrackingRefUpdates().isEmpty() );
    assertEquals( pullResult.getMergeResult().getMergeStatus(),
                    MergeStatus.FAST_FORWARD );
    assertEquals( RepositoryState.SAFE, db.getRepositoryState() );
  }

  @Test
  public void testPullMerge() throws Exception {
    // source: db2, target: db
    setupRemote();
    Git git2 = new Git( db2 );

    // put some file in the source repo and sync
    File sourceFile = new File( db2.getWorkTree(), "SomeFile.txt" );
    writeToFile( sourceFile, "Hello world" );
    git2.add().addFilepattern( "SomeFile.txt" ).call();
    git2.commit().setMessage( "Initial commit for source" ).call();
    PullResult pullResult = git.pull().call();

    // change the source file
    writeToFile( sourceFile, "Another change" );
    git2.add().addFilepattern( "SomeFile.txt" ).call();
    RevCommit sourceCommit = git2.commit().setMessage( "Some change in remote" ).call();
    git2.close();

    File targetFile = new File( db.getWorkTree(), "OtherFile.txt" );
    writeToFile( targetFile, "Unconflicting change" );
    git.add().addFilepattern( "OtherFile.txt" ).call();
    RevCommit targetCommit = git.commit().setMessage( "Unconflicting change in local" ).call();

    pullResult = uiGit.pull();

    MergeResult mergeResult = pullResult.getMergeResult();
    ObjectId[] mergedCommits = mergeResult.getMergedCommits();
    assertEquals( targetCommit.getId(), mergedCommits[0] );
    assertEquals( sourceCommit.getId(), mergedCommits[1] );
    try ( RevWalk rw = new RevWalk( db ) ) {
      RevCommit mergeCommit = rw.parseCommit( mergeResult.getNewHead() );
      URIish uri = new URIish(
          db2.getDirectory().toURI().toURL() );
      String message = "Merge branch 'master' of " + uri;
      assertEquals( message, mergeCommit.getShortMessage() );
    }
  }

  @Test
  public void testPush() throws Exception {
    // Set remote
    URIish uri = new URIish(
      db2.getDirectory().toURI().toURL() );
    RemoteAddCommand cmd = git.remoteAdd();
    cmd.setName( Constants.DEFAULT_REMOTE_NAME );
    cmd.setUri( uri );
    cmd.call();

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

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testShouldPushOnlyToOrigin() throws Exception {
    // origin for db2
    URIish uri = new URIish(
      db2.getDirectory().toURI().toURL() );
    RemoteAddCommand cmd = git.remoteAdd();
    cmd.setName( Constants.DEFAULT_REMOTE_NAME );
    cmd.setUri( uri );
    cmd.call();

    // upstream for db3
    Repository db3 = createWorkRepository();
    uri = new URIish(
        db3.getDirectory().toURI().toURL() );
    cmd = git.remoteAdd();
    cmd.setName( "upstream" );
    cmd.setUri( uri );
    cmd.call();

    // create some refs via commits and tag
    RevCommit commit = git.commit().setMessage( "initial commit" ).call();
    Ref tagRef = git.tag().setName( "tag" ).call();

    try {
      db3.resolve( commit.getId().getName() + "^{commit}" );
      fail( "id shouldn't exist yet" );
    } catch ( MissingObjectException e ) {
      // we should get here
    }

    uiGit.push();

    // The followings should throw MissingObjectException
    thrown.expect( MissingObjectException.class );
    db3.resolve( commit.getId().getName() + "^{commit}" );
    db3.resolve( tagRef.getObjectId().getName() );
  }

  private static void writeToFile( File actFile, String string ) throws IOException {
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream( actFile );
      fos.write( string.getBytes( "UTF-8" ) );
      fos.close();
    } finally {
      if ( fos != null ) {
        fos.close();
      }
    }
  }
}
