package org.pentaho.di.ui.spoon.git;

import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.vfs2.FileObject;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.core.vfs.KettleVFS;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.repository.filerep.KettleFileRepository;
import org.pentaho.di.repository.filerep.KettleFileRepositoryMeta;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.repositoryexplorer.RepositoryExplorer;
import org.pentaho.di.ui.spoon.MainSpoonPerspective;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.SpoonPerspective;
import org.pentaho.di.ui.spoon.SpoonPerspectiveManager;
import org.pentaho.di.ui.spoon.git.model.UIFile;
import org.pentaho.di.ui.spoon.git.model.UIGit;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.components.XulButton;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.components.XulLabel;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulPromptBox;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.containers.XulTree;
import org.pentaho.ui.xul.dnd.DropEvent;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.swt.SwtBindingFactory;
import org.pentaho.ui.xul.swt.custom.DialogConstant;
import org.pentaho.ui.xul.util.XulDialogCallback.Status;
import org.pentaho.ui.xul.util.XulDialogLambdaCallback;

import com.google.common.annotations.VisibleForTesting;

public class GitController extends AbstractXulEventHandler {

  private static final Class<?> PKG = RepositoryExplorer.class;

  private UIGit uiGit = new UIGit();
  private String path;
  private String diff;
  private String authorName;
  private String commitMessage;
  private List<UIRepositoryObjectRevision> selectedRevisions;
  private List<UIFile> selectedUnstagedObjects;
  private List<UIFile> selectedStagedObjects;

  private XulTree revisionTable;
  private XulTree unstagedTable;
  private XulTree stagedTable;
  private XulButton browseButton;
  private XulButton remoteButton;
  private XulButton commitButton;
  private XulButton pullButton;
  private XulButton pushButton;

  private BindingFactory bf = new SwtBindingFactory();
  private Binding branchBinding;
  private Binding remoteBinding;
  private Binding revisionBinding;
  private Binding unstagedBinding;
  private Binding stagedBinding;

  public GitController() {
    setName( "gitController" );
  }

  public void init() throws IllegalArgumentException, InvocationTargetException, XulException {
    XulTextbox diffText = (XulTextbox) document.getElementById( "diff" );
    Text text = (Text) diffText.getManagedObject();
    text.setFont( JFaceResources.getFont( JFaceResources.TEXT_FONT ) );

    revisionTable = (XulTree) document.getElementById( "revision-table" );
    unstagedTable = (XulTree) document.getElementById( "unstaged-table" );
    stagedTable = (XulTree) document.getElementById( "staged-table" );
    browseButton = (XulButton) document.getElementById( "browseButton" );
    remoteButton = (XulButton) document.getElementById( "remoteButton" );
    commitButton = (XulButton) document.getElementById( "commit" );
    pullButton = (XulButton) document.getElementById( "pull" );
    pushButton = (XulButton) document.getElementById( "push" );

    createBindings();
  }

  private void createBindings() {
    XulLabel pathLabel = (XulLabel) document.getElementById( "path" );
    XulTextbox diffText = (XulTextbox) document.getElementById( "diff" );
    XulLabel branchLabel = (XulLabel) document.getElementById( "branch" );
    XulLabel remoteLabel = (XulLabel) document.getElementById( "remote" );
    XulTextbox authorName = (XulTextbox) document.getElementById( "author-name" );
    XulTextbox commitMessage = (XulTextbox) document.getElementById( "commit-message" );

    bf.setDocument( this.getXulDomContainer().getDocumentRoot() );
    bf.setBindingType( Binding.Type.ONE_WAY );
    bf.createBinding( this, "path", pathLabel, "value" );
    bf.createBinding( this, "diff", diffText, "value" );
    branchBinding = bf.createBinding( uiGit, "branch", branchLabel, "value" );
    remoteBinding = bf.createBinding( uiGit, "remote", remoteLabel, "value" );
    revisionBinding = bf.createBinding( uiGit, "revisions", revisionTable, "elements" );
    unstagedBinding = bf.createBinding( uiGit, "unstagedObjects", unstagedTable, "elements" );
    stagedBinding = bf.createBinding( uiGit, "stagedObjects", stagedTable, "elements" );

    bf.createBinding( revisionTable, "selectedItems", this, "selectedRevisions" );
    bf.createBinding( unstagedTable, "selectedItems", this, "selectedUnstagedObjects" );
    bf.createBinding( stagedTable, "selectedItems", this, "selectedStagedObjects" );

    bf.setBindingType( Binding.Type.BI_DIRECTIONAL );
    bf.createBinding( this, "authorName", authorName, "value" );
    bf.createBinding( this, "commitMessage", commitMessage, "value" );
  }

  public void setActive() {
    openGit();
    if ( !uiGit.isOpen() ) {
      return;
    }

    if ( getRepository() == null ) { // when not connected to a repository
      browseButton.setDisabled( false );
    }
    remoteButton.setDisabled( false );
    commitButton.setDisabled( false );
    pullButton.setDisabled( false );
    pushButton.setDisabled( false );

    setAuthorName( uiGit.getAuthorName() );

    try {
      fireSourceChanged();
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  public void setInactive() {
    if ( !uiGit.isOpen() ) {
      return; // No thing to do
    }

    browseButton.setDisabled( true );
    remoteButton.setDisabled( true );
    commitButton.setDisabled( true );
    pullButton.setDisabled( true );
    pushButton.setDisabled( true );

    uiGit.closeGit();
    setPath( null );

    try {
      fireSourceChanged();
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  private void openGit() {
    String baseDirectory = determineBaseDirectory();
    openGit( baseDirectory );
  }

  private void openGit( String baseDirectory ) {
    try {
      uiGit.openGit( baseDirectory );
      setPath( baseDirectory );
      setDiff( "" );
    } catch ( RepositoryNotFoundException e ) {
      initGit( baseDirectory );
    } catch ( NullPointerException e ) {
      return;
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  @VisibleForTesting
  String determineBaseDirectory() {
    if ( getRepository() != null ) { // when connected to a repository
      if ( getRepository().getRepositoryMeta().getId().equals( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID ) ) {
        return ( (KettleFileRepository) getRepository() ).getRepositoryMeta().getBaseDirectory();
      } else {
        return null; // PentahoEnterpriseRepository and KettleDatabaseRepository are not supported.
      }
    } else { // when not connected to a repository
      // Get the active Kettle file
      EngineMetaInterface meta = getActiveMeta();
      if ( meta == null ) { // no file is opened.
        return null;
      } else if ( meta.getFilename() == null ) { // not saved yet
        return null;
      } else {
        // Find the git repository for this file
        String fileName = meta.getFilename();
        try {
          FileObject f = KettleVFS.getFileObject( fileName );
          return uiGit.findGitRepository( f.getURL().toString() );
        } catch ( Exception e ) {
          return null;
        }
      }
    }
  }

  @VisibleForTesting
  EngineMetaInterface getActiveMeta() {
    // Get the data integration perspective
    List<SpoonPerspective> perspectives = SpoonPerspectiveManager.getInstance().getPerspectives();
    SpoonPerspective mainSpoonPerspective = null;
    for ( SpoonPerspective perspective : perspectives ) {
      if ( perspective.getId().equals( MainSpoonPerspective.ID ) ) {
        mainSpoonPerspective = perspective;
        break;
      }
    }
    // Get the active Kettle file
    return mainSpoonPerspective.getActiveMeta();
  }

  @VisibleForTesting
  void initGit( final String baseDirectory ) {
    try {
      XulConfirmBox confirmBox = (XulConfirmBox) document.createElement( "confirmbox" );
      confirmBox.setTitle( "Repository not found" );
      confirmBox.setMessage( "Create a new repository in the following path?\n" + baseDirectory );
      confirmBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
      confirmBox.setCancelLabel( BaseMessages.getString( PKG, "Dialog.Cancel" ) );
      confirmBox.addDialogCallback( (XulDialogLambdaCallback<Object>) ( sender, returnCode, retVal ) -> {
        if ( returnCode == Status.ACCEPT ) {
          try {
            uiGit.initGit( baseDirectory );
            setPath( baseDirectory );
            showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
          } catch ( Exception e ) {
            showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), BaseMessages.getString( PKG, e.getLocalizedMessage() ) );
          }
        }
      } );
      confirmBox.open();
    } catch ( XulException e ) {
      e.printStackTrace();
    }
  }

  protected void fireSourceChanged() throws IllegalArgumentException, InvocationTargetException, XulException {
    branchBinding.fireSourceChanged();
    remoteBinding.fireSourceChanged();
    revisionBinding.fireSourceChanged();
    unstagedBinding.fireSourceChanged();
    stagedBinding.fireSourceChanged();
  }

  public void addToIndex() throws Exception {
    List<UIFile> contents = getSelectedUnstagedObjects();
    for ( UIFile content : contents ) {
      uiGit.add( content.getName() );
    }
    fireSourceChanged();
  }

  public void removeFromIndex() throws Exception {
    List<UIFile> contents = getSelectedStagedObjects();
    for ( UIFile content : contents ) {
      uiGit.reset( content.getName() );
    }
    fireSourceChanged();
  }

  public void onDropToStaged( DropEvent event ) throws Exception {
    for ( Object o : event.getDataTransfer().getData() ) {
      if ( o instanceof UIFile ) {
        UIFile content = (UIFile) o;
        uiGit.add( content.getName() );
      }
    }
  }

  public void onDropToUnstaged( DropEvent event ) throws Exception {
    for ( Object o : event.getDataTransfer().getData() ) {
      if ( o instanceof UIFile ) {
        UIFile content = (UIFile) o;
        uiGit.reset( content.getName() );
      }
    }
  }

  public void onDrag( DropEvent event ) {
  }

  public List<UIRepositoryObjectRevision> getSelectedRevisions() {
    return selectedRevisions;
  }

  public void setSelectedRevisions( List<UIRepositoryObjectRevision> selectedRevisions ) throws Exception {
    this.selectedRevisions = selectedRevisions;
    if ( selectedRevisions.size() != 0 ) {
      if ( getSelectedRevisions().get( 0 ).getName().equals( "" ) ) { //When WIP is selected
        setDiff( "" );
      } else {
        // TODO Should show diff for multiple commits
        setDiff( uiGit.show( getSelectedRevisions().get( 0 ).getName() ) );
      }
    }
  }

  public List<UIFile> getSelectedUnstagedObjects() {
    return selectedUnstagedObjects;
  }

  public void setSelectedUnstagedObjects( List<UIFile> selectedUnstagedObjects ) throws Exception {
    this.selectedUnstagedObjects = selectedUnstagedObjects;
    if ( selectedUnstagedObjects.size() != 0 ) {
      setDiff( uiGit.diff( selectedUnstagedObjects.get( 0 ).getName(), false ) );
    }
  }

  public List<UIFile> getSelectedStagedObjects() {
    return selectedStagedObjects;
  }

  public void setSelectedStagedObjects( List<UIFile> selectedStagedObjects ) throws Exception {
    this.selectedStagedObjects = selectedStagedObjects;
    if ( selectedStagedObjects.size() != 0 ) {
      setDiff( uiGit.diff( selectedStagedObjects.get( 0 ).getName(), true ) );
    }
  }

  private Shell getShell() {
    return Spoon.getInstance().getShell();
  }

  public String getPath() {
    return this.path;
  }

  public void setPath( String path ) {
    this.path = "".equals( path ) ? null : path;
    firePropertyChange( "path", null, path );
  }

  public String getDiff() {
    return this.diff;
  }

  public void setDiff( String diff ) {
    this.diff = diff;
    firePropertyChange( "diff", null, diff );
  }

  public String getAuthorName() {
    return authorName;
  }

  public void setAuthorName( String authorName ) {
    this.authorName = authorName;
    firePropertyChange( "authorName", null, authorName );
  }

  public String getCommitMessage() {
    return commitMessage;
  }

  public void setCommitMessage( String commitMessage ) {
    this.commitMessage = commitMessage;
    firePropertyChange( "commitMessage", null, commitMessage );
  }

  public void commit() throws Exception {
    if ( !uiGit.hasStagedObjects() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
          "There are no staged files" );
      return;
    }

    try {
      PersonIdent author = RawParseUtils.parsePersonIdent( getAuthorName() );
      // Set the local time
      PersonIdent author2 = new PersonIdent( author.getName(), author.getEmailAddress(),
          SystemReader.getInstance().getCurrentTime(),
          SystemReader.getInstance().getTimezone( SystemReader.getInstance().getCurrentTime() ) );

      uiGit.commit( author2, getCommitMessage() );
      setCommitMessage( "" );
      fireSourceChanged();
    } catch ( NullPointerException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
          "Malformed author name" );
    }
  }

  public void pull() {
    try {
      PullResult pullResult = uiGit.pull();
      processPullResult( pullResult );
    } catch ( TransportException e ) {
      if ( e.getMessage().contains( "Authentication is required but no CredentialsProvider has been registered" ) ) {
        pullWithUsernamePassword();
      } else {
        e.printStackTrace();
      }
    } catch ( Exception e ) {
      if ( uiGit.hasRemote() ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getLocalizedMessage() );
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
            "Please setup a remote" );
      }
    }
  }

  public void pullWithUsernamePassword() {
    UsernamePasswordDialog dialog = new UsernamePasswordDialog( getShell() );
    if ( dialog.open() == Window.OK ) {
      String username = dialog.getUsername();
      String password = dialog.getPassword();
      try {
        PullResult pullResult = uiGit.pull( username, password );
        processPullResult( pullResult );
      } catch ( Exception e ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getLocalizedMessage() );
      }
    }
  }

  private void processPullResult( PullResult pullResult ) throws Exception {
    FetchResult fetchResult = pullResult.getFetchResult();
    MergeResult mergeResult = pullResult.getMergeResult();
    if ( pullResult.isSuccessful() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
      fireSourceChanged();
    } else {
      String msg = mergeResult.getMergeStatus().toString();
      if ( mergeResult.getMergeStatus() == MergeStatus.CONFLICTING ) {
        uiGit.resetHard();
      } else if ( mergeResult.getFailingPaths().size() != 0 ) {
        for ( Entry<String, MergeFailureReason> failingPath : mergeResult.getFailingPaths().entrySet() ) {
          msg += "\n" + String.format( "%s: %s", failingPath.getKey(), failingPath.getValue() );
        }
      }
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), msg );
    }
  }

  public void push() {
    try {
      Iterable<PushResult> resultIterable = uiGit.push();
      processPushResult( resultIterable );
    } catch ( TransportException e ) {
      if ( e instanceof TransportException && !uiGit.hasRemote() ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
            "Please setup a remote" );
      } else if ( e.getMessage().contains( "Authentication is required but no CredentialsProvider has been registered" ) ) {
        pushWithUsernamePassword();
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getLocalizedMessage() );
      }
    } catch ( Exception e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getLocalizedMessage() );
    }
  }

  private void pushWithUsernamePassword() {
    UsernamePasswordDialog dialog = new UsernamePasswordDialog( getShell() );
    if ( dialog.open() == Window.OK ) {
      String username = dialog.getUsername();
      String password = dialog.getPassword();
      try {
        Iterable<PushResult> resultIterable = uiGit.push( username, password );
        processPushResult( resultIterable );
      } catch ( Exception e ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getLocalizedMessage() );
      }
    }
  }

  private void processPushResult( Iterable<PushResult> resultIterable ) throws Exception {
    PushResult result = resultIterable.iterator().next();
    String fullBranch = uiGit.getFullBranch();
    RemoteRefUpdate update = result.getRemoteUpdate( fullBranch );
    if ( update.getStatus() == RemoteRefUpdate.Status.OK ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ),
          update.getStatus().toString() );
    } else {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
          update.getStatus().toString() );
    }
  }

  public void editPath() throws IllegalArgumentException, InvocationTargetException, XulException {
    DirectoryDialog dialog = new DirectoryDialog( getShell(), SWT.OPEN );
    if ( dialog.open() != null ) {
      uiGit.closeGit();
      setPath( null );
      openGit( dialog.getFilterPath() );
      fireSourceChanged();
    }
  }

  public void editRemote() {
    try {
      XulPromptBox promptBox = (XulPromptBox) document.createElement( "promptbox" );
      promptBox.setTitle( "Remote repository" );
      promptBox.setButtons( new DialogConstant[] { DialogConstant.OK, DialogConstant.CANCEL } );
      promptBox.setMessage( "URL/path (The remote name will be \"" + Constants.DEFAULT_REMOTE_NAME + "\")" );
      promptBox.setValue( uiGit.getRemote() );
      promptBox.addDialogCallback( (XulDialogLambdaCallback<String>) ( component, status, value ) -> {
        if ( status.equals( Status.ACCEPT ) ) {
          try {
            uiGit.addRemote( value );
          } catch ( URISyntaxException e ) {
            if ( value.equals( "" ) ) {
              try {
                uiGit.removeRemote();
              } catch ( Exception e1 ) {
                e1.printStackTrace();
              }
            } else {
              editRemote();
            }
          } catch ( Exception e ) {
            e.printStackTrace();
          }
        }
      } );
      promptBox.open();
    } catch ( XulException e ) {
      e.printStackTrace();
    }
  }

  private void showMessageBox( String title, String message ) {
    try {
      XulMessageBox messageBox = (XulMessageBox) document.createElement( "messagebox" );
      messageBox.setTitle( title );
      messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
      messageBox.setMessage( message );
      messageBox.open();
    } catch ( XulException e ) {
      e.printStackTrace();
    }
  }

  @VisibleForTesting
  void setUIGit( UIGit uiGit ) {
    this.uiGit = uiGit;
  }

  @VisibleForTesting
  Repository getRepository() {
    return Spoon.getInstance().rep;
  }
}
