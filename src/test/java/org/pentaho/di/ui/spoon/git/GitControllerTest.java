package org.pentaho.di.ui.spoon.git;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.net.URISyntaxException;

import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.junit.Before;
import org.junit.Test;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.repository.RepositoryMeta;
import org.pentaho.di.repository.filerep.KettleFileRepository;
import org.pentaho.di.repository.filerep.KettleFileRepositoryMeta;
import org.pentaho.di.repository.kdr.KettleDatabaseRepository;
import org.pentaho.di.repository.kdr.KettleDatabaseRepositoryMeta;
import org.pentaho.di.repository.pur.PurRepository;
import org.pentaho.di.repository.pur.PurRepositoryMeta;
import org.pentaho.di.ui.spoon.git.model.UIGit;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulPromptBox;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.dom.DocumentFactory;
import org.pentaho.ui.xul.dom.dom4j.ElementDom4J;
import org.pentaho.ui.xul.swt.custom.MessageDialogBase;
import org.pentaho.ui.xul.util.XulDialogCallback;

public class GitControllerTest {

  private static final String CONFIRMBOX = "confirmbox";
  private static final String MESSAGEBOX = "messagebox";
  private static final String PROMPTBOX = "promptbox";
  private Document document;
  private GitController controller;
  private UIGit uiGit;

  @Before
  public void setUp() throws Exception {
    controller = spy( new GitController() );
    controller.setAuthorName( "test <test@example.com>" );
    controller.setCommitMessage( "test" );
    uiGit = mock( UIGit.class );
    controller.setUIGit( uiGit );
    doNothing().when( controller ).fireSourceChanged();

    DocumentFactory.registerElementClass( ElementDom4J.class );
    document = mock( Document.class );
    XulDomContainer xulDomContainer = mock( XulDomContainer.class );
    when( xulDomContainer.getDocumentRoot() ).thenReturn( document );
    controller.setXulDomContainer( xulDomContainer );
  }

  @Test
  public void testGetAuthorName() {
    assertEquals( "test <test@example.com>", controller.getAuthorName() );
  }

  @Test
  public void testGetCommitMessage() {
    assertEquals( "test", controller.getCommitMessage() );
  }

  @Test
  public void returnNullWhenNonKettleFileRepository() {
    Repository rep = mock( KettleDatabaseRepository.class );
    doReturn( rep ).when( controller ).getRepository();
    when( controller.getRepository().getRepositoryMeta() )
      .thenReturn( mock( KettleDatabaseRepositoryMeta.class ) );
    when( controller.getRepository().getRepositoryMeta().getId() )
      .thenReturn( KettleDatabaseRepositoryMeta.REPOSITORY_TYPE_ID );

    String baseDirectory = controller.determineBaseDirectory();

    assertNull( baseDirectory );

    rep = mock( PurRepository.class );
    doReturn( rep ).when( controller ).getRepository();
    when( controller.getRepository().getRepositoryMeta() )
      .thenReturn( mock( PurRepositoryMeta.class ) );
    when( controller.getRepository().getRepositoryMeta().getId() )
      .thenReturn( PurRepositoryMeta.REPOSITORY_TYPE_ID );

    baseDirectory = controller.determineBaseDirectory();

    assertNull( baseDirectory );
  }

  @Test
  public void returnBaseDirectoryWhenKettleFileRepository() {
    Repository rep = new KettleFileRepositoryMock();
    doReturn( rep ).when( controller ).getRepository();

    String baseDirectory = controller.determineBaseDirectory();

    assertEquals( "/tmp/test", baseDirectory );
  }

  @Test
  public void returnNullWhenNoFileIsOpen() {
    doReturn( null ).when( controller ).getRepository();
    doReturn( null ).when( controller ).getActiveMeta();

    String baseDirectory = controller.determineBaseDirectory();

    assertNull( baseDirectory );
  }

  @Test
  public void returnNullWhenFileNotSaved() {
    doReturn( null ).when( controller ).getRepository();
    EngineMetaInterface meta = mock( EngineMetaInterface.class );
    when( meta.getFilename() ).thenReturn( null );
    doReturn( meta ).when( controller ).getActiveMeta();

    String baseDirectory = controller.determineBaseDirectory();

    assertNull( baseDirectory );
  }

  @Test
  public void returnWhenFileIsOpen() {
    doReturn( null ).when( controller ).getRepository();
    EngineMetaInterface meta = mock( EngineMetaInterface.class );
    when( meta.getFilename() ).thenReturn( "/tmp/test" );
    doReturn( meta ).when( controller ).getActiveMeta();
    doReturn( "/tmp/test" ).when( uiGit ).findGitRepository( anyString() );

    String baseDirectory = controller.determineBaseDirectory();

    assertEquals( "/tmp/test", baseDirectory );
  }

  @Test
  public void shouldInitializeGitOnAccept() throws Exception {
    XulConfirmBox confirm = new XulConfirmBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( CONFIRMBOX ) ).thenReturn( confirm );
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );

    controller.initGit( "random-path" );

    verify( uiGit ).initGit( anyString() );
  }

  @Test
  public void shouldNotInitializeGitOnCencel() throws Exception {
    XulConfirmBox confirm = new XulConfirmBoxMock( XulDialogCallback.Status.CANCEL );
    when( document.getElementById( CONFIRMBOX ) ).thenReturn( confirm );

    controller.initGit( "random-path" );

    verify( uiGit, never() ).initGit( anyString() );
  }

  @Test
  public void testAddToIndex() {
  }

  @Test
  public void testRemoveFromIndex() {
  }

  @Test
  public void shouldNotCommitWhenNoStagedObjects() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );
    doReturn( false ).when( uiGit ).hasStagedObjects();

    controller.commit();

    verify( uiGit, never() ).commit( anyString(), anyString(), anyString() );
  }

  @Test
  public void shouldNotCommitWhenAuthorNameMalformed() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );
    doReturn( true ).when( uiGit ).hasStagedObjects();
    controller.setAuthorName( "random author" );

    controller.commit();

    verify( uiGit, never() ).commit( anyString(), anyString(), anyString() );
  }

  @Test
  public void shouldCommit() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );
    doReturn( true ).when( uiGit ).hasStagedObjects();

    controller.commit();

    verify( uiGit ).commit( anyString(), anyString(), anyString() );
  }

  @Test
  public void shouldFireSourceChangedWhenSuccessful() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );
    PullResult pullResult = mock( PullResult.class );
    when( pullResult.isSuccessful() ).thenReturn( true );
    doReturn( pullResult ).when( uiGit ).pull();

    controller.pull();

    verify( controller ).fireSourceChanged();
  }

  @Test
  public void shouldResetHardWhenMergeConflict() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );
    PullResult pullResult = mock( PullResult.class );
    when( pullResult.isSuccessful() ).thenReturn( false );
    doReturn( pullResult ).when( uiGit ).pull();
    MergeResult mergeResult = mock( MergeResult.class );
    when( mergeResult.getMergeStatus() ).thenReturn( MergeStatus.CONFLICTING );
    when( pullResult.getMergeResult() ).thenReturn( mergeResult );

    controller.pull();

    verify( uiGit ).resetHard();
  }

  @Test
  public void shouldNotPushWhenNoRemote() throws Exception {
    XulMessageBox message = new XulMessageBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( MESSAGEBOX ) ).thenReturn( message );
    doReturn( false ).when( uiGit ).hasRemote();
    doNothing().when( controller ).editPath();

    controller.push();

    verify( uiGit, never() ).push();
  }

  @Test
  public void shouldNotEditRemoteOnCancel() throws Exception {
    XulPromptBox prompt = new XulPromptBoxMock( XulDialogCallback.Status.CANCEL );
    when( document.getElementById( PROMPTBOX ) ).thenReturn( prompt );

    controller.editRemote();

    verify( uiGit, never() ).addRemote( anyString() );
  }

  @Test
  public void shouldDeleteRemoteWhenEmptyString() throws Exception {
    XulPromptBox prompt = new XulPromptBoxMock( XulDialogCallback.Status.ACCEPT );
    when( document.getElementById( PROMPTBOX ) ).thenReturn( prompt );
    doThrow( URISyntaxException.class ).when( uiGit ).addRemote( anyString() );
    doReturn( "" ).when( uiGit ).getRemote();

    controller.editRemote();

    verify( uiGit ).addRemote( anyString() );
    verify( uiGit ).removeRemote();
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

  private static class XulPromptBoxMock extends MessageDialogBase implements XulPromptBox {
    private final XulDialogCallback.Status status;
    private String value;

    public XulPromptBoxMock( XulDialogCallback.Status status ) {
      super( PROMPTBOX );
      this.status = status;
    }

    @Override
    public int open() {
      for ( XulDialogCallback<String> callback : callbacks ) {
        callback.onClose( null, status, value );
      }
      return 0;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public void setValue( String value ) {
      this.value = value;
    }
  }

  private static class KettleFileRepositoryMock extends KettleFileRepository implements Repository {
    @Override
    public KettleFileRepositoryMeta getRepositoryMeta() {
      return new KettleFileRepositoryMetaMock();
    }
  }

  private static class KettleFileRepositoryMetaMock extends KettleFileRepositoryMeta implements RepositoryMeta {
    @Override
    public String getBaseDirectory() {
      return "/tmp/test";
    }
  }
}
