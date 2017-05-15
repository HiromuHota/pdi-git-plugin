package org.pentaho.di.ui.spoon.git;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.RemoteRemoveCommand;
import org.eclipse.jgit.api.RemoteSetUrlCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.filerep.KettleFileRepository;
import org.pentaho.di.ui.repository.repositoryexplorer.RepositoryExplorer;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryContent;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryObject;
import org.pentaho.di.ui.spoon.MainSpoonPerspective;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.SpoonPerspective;
import org.pentaho.di.ui.spoon.SpoonPerspectiveManager;
import org.pentaho.di.ui.spoon.git.model.UIGit;
import org.pentaho.ui.xul.XulComponent;
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
import org.pentaho.ui.xul.util.XulDialogCallback;

import com.google.common.annotations.VisibleForTesting;

public class GitController extends AbstractXulEventHandler {

  private static final Class<?> PKG = RepositoryExplorer.class;

  private String path;
  private UIGit uiGit = new UIGit();

  private XulTextbox pathText;
  private XulTree revisionTable;
  private XulTree unstagedTable;
  private XulTree stagedTable;
  private XulButton browseButton;
  private XulButton remoteButton;
  private XulButton commitButton;
  private XulButton pullButton;
  private XulButton pushButton;
  private XulMessageBox messageBox;
  private XulConfirmBox confirmBox;
  private XulPromptBox promptBox;

  private BindingFactory bf = new SwtBindingFactory();
  private Binding pathBinding;
  private Binding branchBinding;
  private Binding remoteBinding;
  private Binding revisionBinding;
  private Binding unstagedBinding;
  private Binding stagedBinding;

  public GitController() {
    setName( "gitController" );
  }

  public void init() throws IllegalArgumentException, InvocationTargetException, XulException {
    pathText = (XulTextbox) document.getElementById( "path-text" );
    XulLabel branchLabel = (XulLabel) document.getElementById( "branch" );
    XulLabel remoteLabel = (XulLabel) document.getElementById( "remote" );
    revisionTable = (XulTree) document.getElementById( "revision-table" );
    unstagedTable = (XulTree) document.getElementById( "unstaged-table" );
    stagedTable = (XulTree) document.getElementById( "staged-table" );
    XulTextbox authorName = (XulTextbox) document.getElementById( "author-name" );
    XulTextbox commitMessage = (XulTextbox) document.getElementById( "commit-message" );
    browseButton = (XulButton) document.getElementById( "browseButton" );
    remoteButton = (XulButton) document.getElementById( "remoteButton" );
    commitButton = (XulButton) document.getElementById( "commit" );
    pullButton = (XulButton) document.getElementById( "pull" );
    pushButton = (XulButton) document.getElementById( "push" );
    messageBox = (XulMessageBox) document.getElementById( "messagebox" );
    confirmBox = (XulConfirmBox) document.getElementById( "confirmbox" );
    promptBox = (XulPromptBox) document.getElementById( "promptbox" );

    bf.setDocument( this.getXulDomContainer().getDocumentRoot() );
    bf.setBindingType( Binding.Type.ONE_WAY );
    branchBinding = bf.createBinding( uiGit, "branch", branchLabel, "value" );
    remoteBinding = bf.createBinding( uiGit, "remote", remoteLabel, "value" );
    revisionBinding = bf.createBinding( uiGit, "revisionObjects", revisionTable, "elements" );
    unstagedBinding = bf.createBinding( uiGit, "unstagedObjects", unstagedTable, "elements" );
    stagedBinding = bf.createBinding( uiGit, "stagedObjects", stagedTable, "elements" );

    bf.setBindingType( Binding.Type.BI_DIRECTIONAL );
    pathBinding = bf.createBinding( this, "path", pathText, "value" );
    bf.createBinding( uiGit, "authorName", authorName, "value" );
    bf.createBinding( uiGit, "commitMessage", commitMessage, "value" );
  }

  public void setActive() {
    openGit();
    if ( uiGit.getGit() == null ) {
      return;
    }

    if ( Spoon.getInstance().rep == null ) { // when not connected to a repository
      pathText.setDisabled( false );
      browseButton.setDisabled( false );
    }
    remoteButton.setDisabled( false );
    commitButton.setDisabled( false );
    pullButton.setDisabled( false );
    pushButton.setDisabled( false );

    uiGit.setAuthorName( uiGit.getGit().getRepository().getConfig().getString( "user", null, "name" )
        + " <" + uiGit.getGit().getRepository().getConfig().getString( "user", null, "email" ) + ">" );

    try {
      fireSourceChanged();
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  public void setInactive() {
    if ( uiGit.getGit() == null ) {
      return; // No thing to do
    }

    pathText.setDisabled( true );
    browseButton.setDisabled( true );
    remoteButton.setDisabled( true );
    commitButton.setDisabled( true );
    pullButton.setDisabled( true );
    pushButton.setDisabled( true );

    closeGit();

    try {
      fireSourceChanged();
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  private void openGit() {
    String baseDirectory = determineBaseDirectory();
    try {
      uiGit.openGit( baseDirectory );
      path = baseDirectory;
    } catch ( RepositoryNotFoundException e ) {
      initGit( baseDirectory );
      openGit();
    } catch ( IOException e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch ( NullPointerException e ) {
      return;
    }
  }

  private String determineBaseDirectory() {
    if ( Spoon.getInstance().rep != null ) { // when connected to a repository
      if ( Spoon.getInstance().rep.getClass() != KettleFileRepository.class ) {
        return null; // PentahoEnterpriseRepository and KettleDatabaseRepository are not supported.
      } else {
        return ( (KettleFileRepository) Spoon.getInstance().rep ).getRepositoryMeta().getBaseDirectory();
      }
    } else { // when not connected to a repository
      if ( path != null ) { // when specified by the user
        return path;
      } else {
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
        EngineMetaInterface meta = mainSpoonPerspective.getActiveMeta();
        if ( meta == null ) { // no file is opened.
          return null;
        } else if ( meta.getFilename() == null ) { // not saved yet
          return null;
        }
        // Find the git repository for this file
        String fileName = meta.getFilename();
        try {
          Repository repository = ( new FileRepositoryBuilder() ).readEnvironment() // scan environment GIT_* variables
            .findGitDir( new File( fileName ).getParentFile() ) // scan up the file system tree
            .build();
          return repository.getDirectory().getParent();
        } catch ( IOException e ) {
          return null;
        }
      }
    }
  }

  private void closeGit() {
    path = null;
    uiGit.getGit().close();
    uiGit.setGit( null );
  }

  @VisibleForTesting
  void initGit( final String baseDirectory ) {
    confirmBox = (XulConfirmBox) document.getElementById( "confirmbox" );
    confirmBox.setTitle( "Repository not found" );
    confirmBox.setMessage( "Create a new repository in the following path?\n" + baseDirectory );
    confirmBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
    confirmBox.setCancelLabel( BaseMessages.getString( PKG, "Dialog.Cancel" ) );
    confirmBox.addDialogCallback( new XulDialogCallback<Object>() {

      public void onClose( XulComponent sender, Status returnCode, Object retVal ) {
        messageBox = (XulMessageBox) document.getElementById( "messagebox" );
        if ( returnCode == Status.ACCEPT ) {
          try {
            uiGit.initGit( baseDirectory );
            path = baseDirectory;
            messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Success" ) );
            messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
            messageBox.setMessage( BaseMessages.getString( PKG, "Dialog.Success" ) );
            messageBox.open();
          } catch ( Exception e ) {
            messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
            messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
            messageBox.setMessage( BaseMessages.getString( PKG, e.getLocalizedMessage() ) );
            messageBox.open();
          }
        }
      }

      public void onError( XulComponent sender, Throwable t ) {
        throw new RuntimeException( t );
      }
    } );
    confirmBox.open();
  }

  protected void fireSourceChanged() throws IllegalArgumentException, InvocationTargetException, XulException {
    pathBinding.fireSourceChanged();
    branchBinding.fireSourceChanged();
    remoteBinding.fireSourceChanged();
    revisionBinding.fireSourceChanged();
    unstagedBinding.fireSourceChanged();
    stagedBinding.fireSourceChanged();
  }

  public void addToIndex() throws Exception {
    Collection<UIRepositoryContent> contents = unstagedTable.getSelectedItems();
    for ( UIRepositoryContent content : contents ) {
      uiGit.getGit().add().addFilepattern( content.getName() ).call();
    }
    fireSourceChanged();
  }

  public void onDropToStaged( DropEvent event ) throws Exception {
    for ( Object o : event.getDataTransfer().getData() ) {
      if ( o instanceof UIRepositoryObject ) {
        UIRepositoryContent content = (UIRepositoryContent) o;
        uiGit.getGit().add().addFilepattern( content.getName() ).call();
      }
    }
  }

  public void onDropToUnstaged( DropEvent event ) throws Exception {
    for ( Object o : event.getDataTransfer().getData() ) {
      if ( o instanceof UIRepositoryObject ) {
        UIRepositoryContent content = (UIRepositoryContent) o;
        uiGit.getGit().reset().addPath( content.getName() ).call();
      }
    }
  }

  public void onDrag( DropEvent event ) {
  }

  public void removeFromIndex() throws Exception {
    Collection<UIRepositoryContent> contents = stagedTable.getSelectedItems();
    for ( UIRepositoryContent content : contents ) {
      uiGit.getGit().reset().addPath( content.getName() ).call();
    }
    fireSourceChanged();
  }

  public void commit() throws Exception {
    if ( uiGit.getStagedObjects().size() == 0 ) {
      messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
      messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
      messageBox.setMessage( "There are no staged files" );
      messageBox.open();
      return;
    }

    Matcher m = Pattern.compile( "(.*) <(.*@.*)>" ).matcher( uiGit.getAuthorName() );
    if ( !m.matches() ) {
      messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
      messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
      messageBox.setMessage( "Malformed author name" );
      messageBox.open();
      return;
    }
    uiGit.commit( m.group( 1 ), m.group( 2 ), uiGit.getCommitMessage() );
    uiGit.setCommitMessage( "" );
    fireSourceChanged();
  }

  public void pull() {
    try {
      PullResult result = uiGit.pull();
      revisionBinding.fireSourceChanged();
    } catch ( GitAPIException e ) {
      messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
      messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
      messageBox.setMessage( e.getMessage() );
      messageBox.open();
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  public void push() throws InvalidRemoteException, TransportException, GitAPIException, IOException {
    final String fullBranch = uiGit.getGit().getRepository().getFullBranch();
    final StoredConfig config = uiGit.getGit().getRepository().getConfig();
    Set<String> remotes = config.getSubsections( "remote" );
    if ( remotes.contains( Constants.DEFAULT_REMOTE_NAME ) ) {
      PushResult result = uiGit.getGit().push().call().iterator().next();
      RemoteRefUpdate update = result.getRemoteUpdate( fullBranch );
      messageBox = (XulMessageBox) document.getElementById( "messagebox" );
      if ( update.getStatus() == RemoteRefUpdate.Status.OK ) {
        messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Success" ) );
      } else {
        messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
      }
      messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
      messageBox.setMessage( update.getStatus().toString() );
      messageBox.open();
    } else {
      editRemote();
      push();
    }
  }

  public void editPath() throws IllegalArgumentException, InvocationTargetException, XulException {
    Shell shell = Spoon.getInstance().getShell();
    DirectoryDialog dialog = new DirectoryDialog( shell, SWT.OPEN );
    if ( dialog.open() != null ) {
      closeGit();
      setPath( dialog.getFilterPath() );
      openGit();
      fireSourceChanged();
    }
  }

  public void editRemote() {
    promptBox.setTitle( "Remote repository" );
    promptBox.setButtons( new DialogConstant[] { DialogConstant.OK, DialogConstant.CANCEL } );
    promptBox.setMessage( "URL/path (The remote name will be \"" + Constants.DEFAULT_REMOTE_NAME + "\")" );
    promptBox.setValue( uiGit.getRemote() );
    promptBox.addDialogCallback( new XulDialogCallback<String>() {
      public void onClose( XulComponent component, Status status, String value ) {
        if ( !status.equals( Status.CANCEL ) ) {
          try {
            RemoteSetUrlCommand cmd = uiGit.getGit().remoteSetUrl();
            cmd.setName( Constants.DEFAULT_REMOTE_NAME );
            URIish uri = new URIish( value );
            cmd.setUri( uri );
            // execute the command to change the fetch url
            cmd.setPush( false );
            cmd.call();
            // execute the command to change the push url
            cmd.setPush( true );
            cmd.call();

            remoteBinding.fireSourceChanged();
          } catch ( URISyntaxException e ) {
            if ( value.equals( "" ) ) {
              try {
                deleteRemote();
                remoteBinding.fireSourceChanged();
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
      }
      public void onError( XulComponent component, Throwable err ) {
        throw new RuntimeException( err );
      }
    } );
    promptBox.open();
  }

  @VisibleForTesting
  RemoteConfig deleteRemote() throws GitAPIException {
    RemoteRemoveCommand cmd = uiGit.getGit().remoteRemove();
    cmd.setName( Constants.DEFAULT_REMOTE_NAME );
    return cmd.call();
  }

  @VisibleForTesting
  void setUIGit( UIGit uiGit ) {
    this.uiGit = uiGit;
  }

  public void setPath( String path ) {
    this.path = "".equals( path ) ? null : path;
  }

  public String getPath() {
    return this.path;
  }
}
