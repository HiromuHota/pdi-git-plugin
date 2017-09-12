package org.pentaho.di.git.spoon.model;

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
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
    uiGit.setDirectory( git.getRepository().getDirectory().getParent() );

    // create another repository
    db2 = createWorkRepository();
  }

  @Test
  public void testGetBranch() {
    assertEquals( "master", uiGit.getBranch() );
  }

  @Test
  public void testGetBranches() throws Exception {
    initialCommit();

    assertEquals( Constants.MASTER, uiGit.getLocalBranches().get( 0 ) );
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
    setupRemote();
    uiGit.removeRemote();

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
  public void testCommit() throws Exception {
    writeTrashFile( "Test.txt", "Hello world" );
    uiGit.add( "Test.txt" );
    PersonIdent author = new PersonIdent( "author", "author@example.com" );
    String message = "Initial commit";

    uiGit.commit( author.toExternalString(), message );
    String commitId = uiGit.getCommitId( Constants.HEAD );

    assertTrue( author.toExternalString().contains( uiGit.getAuthorName( commitId ) ) );
    assertEquals( message, uiGit.getCommitMessage( commitId ) );
  }

  @Test
  public void shouldNotCommitWhenAuthorNameMalformed() throws Exception {
    writeTrashFile( "Test.txt", "Hello world" );
    uiGit.add( "Test.txt" );

    thrown.expect( NullPointerException.class );
    uiGit.commit( "random author", "Initial commit" );
  }

  @Test
  public void testGetRevisions() throws Exception {
    initialCommit();
    UIRepositoryObjectRevisions revisions = uiGit.getRevisions();
    assertEquals( 1, revisions.size() );
  }

  @Test
  public void testGetUnstagedAndStagedObjects() throws Exception {
    // Create files
    File a = writeTrashFile( "a.ktr", "1234567" );
    File b = writeTrashFile( "b.kjb", "content" );
    File c = writeTrashFile( "c.kjb", "abcdefg" );

    // Test for unstaged
    List<UIFile> unStagedObjects = uiGit.getUnstagedFiles();
    assertEquals( 3, unStagedObjects.size() );
    assertTrue( unStagedObjects.stream().anyMatch( obj -> obj.getName().equals( "a.ktr" ) ) );

    // Test for staged
    git.add().addFilepattern( "." ).call();
    List<UIFile> stagedObjects = uiGit.getStagedFiles();
    assertEquals( 3, stagedObjects.size() );
    assertTrue( stagedObjects.stream().anyMatch( obj -> obj.getName().equals( "a.ktr" ) ) );

    // Make a commit
    RevCommit commit = git.commit().setMessage( "initial commit" ).call();
    stagedObjects = uiGit.getStagedFiles( commit.getId().name() + "~", commit.getId().name() );
    assertEquals( 3, stagedObjects.size() );
    assertTrue( stagedObjects.stream().anyMatch( obj -> obj.getName().equals( "b.kjb" ) ) );

    // Change
    a.renameTo( new File( git.getRepository().getWorkTree(), "a2.ktr" ) );
    b.delete();
    FileUtils.writeStringToFile( c, "A change" );

    // Test for unstaged
    unStagedObjects = uiGit.getUnstagedFiles();
    assertEquals( ChangeType.DELETE, unStagedObjects.stream().filter( obj -> obj.getName().equals( "b.kjb" ) ).findFirst().get().getChangeType() );

    // Test for staged
    git.add().addFilepattern( "." ).call();
    git.rm().addFilepattern( a.getName() ).call();
    git.rm().addFilepattern( b.getName() ).call();
    stagedObjects = uiGit.getStagedFiles();
    assertEquals( 4, stagedObjects.size() );
    assertEquals( ChangeType.DELETE, stagedObjects.stream().filter( obj -> obj.getName().equals( "b.kjb" ) ).findFirst().get().getChangeType() );
    assertEquals( ChangeType.ADD, stagedObjects.stream().filter( obj -> obj.getName().equals( "a2.ktr" ) ).findFirst().get().getChangeType() );
    assertEquals( ChangeType.MODIFY, stagedObjects.stream().filter( obj -> obj.getName().equals( "c.kjb" ) ).findFirst().get().getChangeType() );
  }

  @Test
  public void testPull() throws Exception {
    // source: db2, target: db
    setupRemote();
    Git git2 = new Git( db2 );

    // put some file in the source repo and sync
    File sourceFile = new File( db2.getWorkTree(), "SomeFile.txt" );
    FileUtils.writeStringToFile( sourceFile, "Hello world" );
    git2.add().addFilepattern( "SomeFile.txt" ).call();
    git2.commit().setMessage( "Initial commit for source" ).call();
    PullResult pullResult = git.pull().call();

    // change the source file
    FileUtils.writeStringToFile( sourceFile, "Another change" );
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
    FileUtils.writeStringToFile( sourceFile, "Hello world" );
    git2.add().addFilepattern( "SomeFile.txt" ).call();
    git2.commit().setMessage( "Initial commit for source" ).call();
    PullResult pullResult = git.pull().call();

    // change the source file
    FileUtils.writeStringToFile( sourceFile, "Another change" );
    git2.add().addFilepattern( "SomeFile.txt" ).call();
    RevCommit sourceCommit = git2.commit().setMessage( "Some change in remote" ).call();
    git2.close();

    File targetFile = new File( db.getWorkTree(), "OtherFile.txt" );
    FileUtils.writeStringToFile( targetFile, "Unconflicting change" );
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

  @Test
  public void testDiff() throws Exception {
    File file = writeTrashFile( "Test.txt", "Hello world" );

    String diff = uiGit.diff( VCS.INDEX, VCS.WORKINGTREE, "Test.txt" );
    assertTrue( diff.contains( "+Hello world" ) );

    git.add().addFilepattern( "Test.txt" ).call();
    RevCommit commit1 = git.commit().setMessage( "initial commit" ).call();

    // Add another line
    FileUtils.writeStringToFile( file, "second commit" );
    git.add().addFilepattern( "Test.txt" ).call();
    RevCommit commit2 = git.commit().setMessage( "second commit" ).call();

    diff = uiGit.diff( commit1.getName(), VCS.WORKINGTREE );
    assertTrue( diff.contains( "-Hello world" ) );
    assertTrue( diff.contains( "+second commit" ) );
    diff = uiGit.diff( commit1.getName(), commit2.getName() );
    assertTrue( diff.contains( "+second commit" ) );

    // Should detect renames
    file.renameTo( new File( git.getRepository().getWorkTree(), "Test2.txt" ) );
    diff = uiGit.diff( Constants.HEAD, VCS.WORKINGTREE, "Test2.txt" );
    assertTrue( diff.contains( "rename" ) );
  }

  @Test
  public void testShow() throws Exception {
    RevCommit commit = initialCommit();

    String diff = uiGit.show( commit.getId().name() );
    assertTrue( diff.contains( "Hello world" ) );

    // Make the second commit
    writeTrashFile( "Test2.txt", "Second commit" );
    diff = uiGit.show( VCS.WORKINGTREE );
    assertTrue( diff.contains( "+Second commit" ) );
    git.add().addFilepattern( "Test2.txt" ).call();
    commit = git.commit().setMessage( "initial commit" ).call();

    diff = uiGit.show( commit.getId().name() );
    assertTrue( diff.contains( "Second commit" ) );
  }

  @Test
  public void testOpen() throws Exception {
    RevCommit commit = initialCommit();

    InputStream inputStream = uiGit.open( "Test.txt", commit.getName() );
    StringWriter writer = new StringWriter();
    IOUtils.copy( inputStream, writer, "UTF-8" );
    assertEquals( "Hello world", writer.toString() );

    inputStream = uiGit.open( "Test.txt", VCS.WORKINGTREE );
    writer = new StringWriter();
    IOUtils.copy( inputStream, writer, "UTF-8" );
    assertEquals( "Hello world", writer.toString() );
  }

  @Test
  public void testCheckout() throws Exception {
    initialCommit();

    git.branchCreate().setName( "develop" ).call();
    uiGit.checkout( "master" );
    assertEquals( "master", uiGit.getBranch() );
    uiGit.checkout( "develop" );
    assertEquals( "develop", uiGit.getBranch() );
  }

  @Test
  public void testCheckoutPath() throws Exception {
    // commit something
    File file = writeTrashFile( "Test.txt", "Hello world" );
    git.add().addFilepattern( "Test.txt" ).call();
    RevCommit commit = git.commit().setMessage( "initial commit" ).call();

    // Add some change
    FileUtils.writeStringToFile( file, "Change" );
    assertEquals( "Change", FileUtils.readFileToString( file ) );

    uiGit.checkout( null, file.getName() );
    assertEquals( "Hello world", FileUtils.readFileToString( file ) );

    uiGit.checkout( Constants.HEAD, file.getName() );
    assertEquals( "Hello world", FileUtils.readFileToString( file ) );

    uiGit.checkout( commit.getName(), file.getName() );
    assertEquals( "Hello world", FileUtils.readFileToString( file ) );
  }

  @Test
  public void testCreateDeleteBranch() throws Exception {
    initialCommit();

    // create a branch
    uiGit.createBranch( "test" );
    List<String> branches = uiGit.getLocalBranches();
    assertTrue( branches.contains( "test" ) );

    // delete the branch
    uiGit.deleteBranch( "test", true );
    branches = uiGit.getLocalBranches();
    assertEquals( 1, branches.size() );
    assertFalse( branches.contains( "test" ) );
  }

  @Test
  public void testCreateDeleteTag() throws Exception {
    initialCommit();

    // create a tag
    uiGit.createTag( "test" );
    List<String> tags = uiGit.getTags();
    assertTrue( tags.contains( "test" ) );

    // delete the branch
    uiGit.deleteTag( "test" );
    tags = uiGit.getTags();
    assertEquals( 0, tags.size() );
    assertFalse( tags.contains( "test" ) );
  }

  private RevCommit initialCommit() throws Exception {
    writeTrashFile( "Test.txt", "Hello world" );
    git.add().addFilepattern( "Test.txt" ).call();
    return git.commit().setMessage( "initial commit" ).call();
  }
}
