package org.pentaho.di.ui.spoon.git;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import java.io.File;
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
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.dom.DocumentFactory;
import org.pentaho.ui.xul.dom.dom4j.ElementDom4J;
import org.pentaho.ui.xul.swt.custom.MessageDialogBase;
import org.pentaho.ui.xul.util.XulDialogCallback;

public class GitControllerTest extends RepositoryTestCase {

  private static final String CONFIRMBOX = "confirmbox";
  private static final String MESSAGEBOX = "messagebox";
  private Document document;
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
    doCallRealMethod().when( controller ).initMessageBox();
    doCallRealMethod().when( controller ).getRevisionObjects();
    doCallRealMethod().when( controller ).getStagedObjects();
    doCallRealMethod().when( controller ).getUnstagedObjects();
    doCallRealMethod().when( controller ).setGit( (Git) any() );
    doCallRealMethod().when( controller ).setXulDomContainer( (XulDomContainer) any() );
    doCallRealMethod().when( controller ).initGit( anyString() );
    when( controller.getAuthorName() ).thenReturn( "test <test@example.com>" );
    when( controller.getCommitMessage() ).thenReturn( "test" );
    controller.setGit( git );

    DocumentFactory.registerElementClass( ElementDom4J.class );
    document = mock( Document.class );
    XulDomContainer xulDomContainer = mock( XulDomContainer.class );
    when( xulDomContainer.getDocumentRoot() ).thenReturn( document );
    controller.setXulDomContainer( xulDomContainer );
  }

  @Test
  public void testGetPath() {
    assertEquals( db.getDirectory().getParent(), controller.getPath() );
  }

  @Test
  public void shouldInitializeGitOnAccept() throws IOException, XulException {
    XulConfirmBox prompt = new XulConfirmBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.createElement( CONFIRMBOX ) ).thenReturn( prompt );
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.createElement( MESSAGEBOX ) ).thenReturn( message );

    controller.initMessageBox();
    controller.setGit( null );

    File directory = createTempDirectory( "testInitRepository" );
    controller.initGit( directory.getPath() );
    assertNotEquals( controller.getPath(), "" );
  }

  @Test
  public void shouldNotInitializeGitOnCencel() throws IOException, XulException {
    XulConfirmBox prompt = new XulConfirmBoxMock( XulDialogCallback.Status.CANCEL );
    when( document.createElement( CONFIRMBOX ) ).thenReturn( prompt );

    controller.initMessageBox();
    controller.setGit( null );

    File directory = createTempDirectory( "testInitRepository" );
    controller.initGit( directory.getPath() );
    assertEquals( controller.getPath(), "" );
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

  private static class XulConfirmBoxMock extends MessageDialogBase implements XulConfirmBox {
    private final XulDialogCallback.Status status;

    public XulConfirmBoxMock( XulDialogCallback.Status status ) {
      super( CONFIRMBOX );
      this.status = status;
    }

    @Override
    public int open() {
      for ( XulDialogCallback<String> callback : callbacks ) {
        callback.onClose( null, status, null );
      }
      return 0;
    }
  }

  private static class XulMessageBoxMock extends MessageDialogBase implements XulMessageBox {
    private final XulDialogCallback.Status status;

    public XulMessageBoxMock( XulDialogCallback.Status status ) {
      super( MESSAGEBOX );
      this.status = status;
    }

    @Override
    public int open() {
      for ( XulDialogCallback<String> callback : callbacks ) {
        callback.onClose( null, status, null );
      }
      return 0;
    }
  }
}
