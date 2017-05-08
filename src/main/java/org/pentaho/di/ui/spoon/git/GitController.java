package org.pentaho.di.ui.spoon.git;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.ObjectRevision;
import org.pentaho.di.repository.RepositoryElementMetaInterface;
import org.pentaho.di.repository.RepositoryObject;
import org.pentaho.di.repository.RepositoryObjectType;
import org.pentaho.di.repository.StringObjectId;
import org.pentaho.di.repository.filerep.KettleFileRepository;
import org.pentaho.di.repository.pur.PurObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
import org.pentaho.di.ui.repository.repositoryexplorer.RepositoryExplorer;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIJob;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryContent;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryObject;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryObjects;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UITransformation;
import org.pentaho.di.ui.spoon.MainSpoonPerspective;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.SpoonPerspective;
import org.pentaho.di.ui.spoon.SpoonPerspectiveManager;
import org.pentaho.di.ui.spoon.git.model.UIGit;
import org.pentaho.ui.xul.XulComponent;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.components.WaitBoxRunnable;
import org.pentaho.ui.xul.components.XulButton;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.components.XulLabel;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulPromptBox;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.components.XulWaitBox;
import org.pentaho.ui.xul.containers.XulTree;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.swt.SwtBindingFactory;
import org.pentaho.ui.xul.swt.custom.DialogConstant;
import org.pentaho.ui.xul.util.XulDialogCallback;

import com.google.common.annotations.VisibleForTesting;

public class GitController extends AbstractXulEventHandler {

  private static final Class<?> PKG = RepositoryExplorer.class;

  private Git git;
  private String path;
  private UIGit uiGit = new UIGit();

  private XulTextbox pathText;
  private XulTree revisionTable;
  private XulTree unstagedTable;
  private XulTree stagedTable;
  private XulButton remoteButton;
  private XulButton commitButton;
  private XulButton pullButton;
  private XulButton pushButton;
  private XulMessageBox messageBox;
  private XulConfirmBox confirmBox;
  private XulPromptBox promptBox;
  private XulWaitBox waitBox;

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
    remoteButton = (XulButton) document.getElementById( "remoteButton" );
    commitButton = (XulButton) document.getElementById( "commit" );
    pullButton = (XulButton) document.getElementById( "pull" );
    pushButton = (XulButton) document.getElementById( "push" );

    bf.setDocument( this.getXulDomContainer().getDocumentRoot() );
    bf.setBindingType( Binding.Type.ONE_WAY );
    branchBinding = bf.createBinding( this, "branch", branchLabel, "value" );
    remoteBinding = bf.createBinding( this, "remote", remoteLabel, "value" );
    revisionBinding = bf.createBinding( this, "revisionObjects", revisionTable, "elements" );
    unstagedBinding = bf.createBinding( this, "unstagedObjects", unstagedTable, "elements" );
    stagedBinding = bf.createBinding( this, "stagedObjects", stagedTable, "elements" );

    bf.setBindingType( Binding.Type.BI_DIRECTIONAL );
    pathBinding = bf.createBinding( this, "path", pathText, "value" );
    bf.createBinding( uiGit, "authorName", authorName, "value" );
    bf.createBinding( uiGit, "commitMessage", commitMessage, "value" );
  }

  @VisibleForTesting
  void initMessageBox() throws XulException {
    messageBox = (XulMessageBox) document.createElement( "messagebox" );
    confirmBox = (XulConfirmBox) document.createElement( "confirmbox" );
    promptBox = (XulPromptBox) document.createElement( "promptbox" );
    waitBox = (XulWaitBox) document.createElement( "waitbox" );
  }

  public void setActive() {
    openGit();
    if ( git == null ) {
      return;
    }

    remoteButton.setDisabled( false );
    commitButton.setDisabled( false );
    pullButton.setDisabled( false );
    pushButton.setDisabled( false );

    setAuthorName( git.getRepository().getConfig().getString( "user", null, "name" )
        + " <" + git.getRepository().getConfig().getString( "user", null, "email" ) + ">" );

    try {
      fireSourceChanged();
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  public void setInactive() {
    if ( git == null ) {
      return; // No thing to do
    }

    remoteButton.setDisabled( true );
    commitButton.setDisabled( true );
    pullButton.setDisabled( true );
    pushButton.setDisabled( true );

    git.close();
    git = null;

    try {
      fireSourceChanged();
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  private void openGit() {
    if ( Spoon.getInstance().rep != null ) { // when connected to a repository
      if ( Spoon.getInstance().rep.getClass() != KettleFileRepository.class ) {
        return; // PentahoEnterpriseRepository and KettleDatabaseRepository are not supported.
      } else {
        final String baseDirectory = ( (KettleFileRepository) Spoon.getInstance().rep ).getRepositoryMeta().getBaseDirectory();
        path = baseDirectory;
        try {
          git = Git.open( new File( baseDirectory ) );
        } catch ( RepositoryNotFoundException e ) {
          initGit( baseDirectory );
        } catch ( IOException e ) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    } else { // when not connected to a repository
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
        return;
      } else if ( meta.getFilename() == null ) { // not saved yet
        return;
      }
      // Find the git repository for this file
      String fileName = meta.getFilename();
      try {
        Repository repository = ( new FileRepositoryBuilder() ).readEnvironment() // scan environment GIT_* variables
          .findGitDir( new File( fileName ).getParentFile() ) // scan up the file system tree
          .build();
        git = new Git( repository );
        path = repository.getDirectory().getParent();
      } catch ( IOException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch ( IllegalArgumentException e ) { // No git repository found when scanning up to the root
        initGit( new File( fileName ).getParentFile().getPath() );
      }
    }
    return;
  }

  @VisibleForTesting
  void initGit( final String baseDirectory ) {
    confirmBox.setTitle( "Repository not found" );
    confirmBox.setMessage( "Create a new repository in the following path?\n" + baseDirectory );
    confirmBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
    confirmBox.setCancelLabel( BaseMessages.getString( PKG, "Dialog.Cancel" ) );
    confirmBox.addDialogCallback( new XulDialogCallback<Object>() {

      public void onClose( XulComponent sender, Status returnCode, Object retVal ) {
        if ( returnCode == Status.ACCEPT ) {
          try {
            Git.init().setDirectory( new File( baseDirectory ) ).call();
            git = Git.open( new File( baseDirectory ) );
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
      git.add().addFilepattern( content.getName() ).call();
    }
    fireSourceChanged();
  }

  public void removeFromIndex() throws Exception {
    Collection<UIRepositoryContent> contents = stagedTable.getSelectedItems();
    for ( UIRepositoryContent content : contents ) {
      git.reset().addPath( content.getName() ).call();
    }
    fireSourceChanged();
  }

  public void commit() throws Exception {
    if ( getStagedObjects().size() == 0 ) {
      messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
      messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
      messageBox.setMessage( "There are no staged files" );
      messageBox.open();
      return;
    }

    Matcher m = Pattern.compile( "(.*) <(.*@.*)>" ).matcher( getAuthorName() );
    if ( !m.matches() ) {
      messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
      messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
      messageBox.setMessage( "Malformed author name" );
      messageBox.open();
      return;
    }
    git.commit().setAuthor( m.group( 1 ), m.group( 2 ) ).setMessage( getCommitMessage() ).call();
    setCommitMessage( "" );
    fireSourceChanged();
  }

  public void pull() {
    try {
      PullResult result = git.pull().call();
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
    final String fullBranch = git.getRepository().getFullBranch();
    final StoredConfig config = git.getRepository().getConfig();
    Set<String> remotes = config.getSubsections( "remote" );
    if ( remotes.contains( Constants.DEFAULT_REMOTE_NAME ) ) {
      final Shell shell = Spoon.getInstance().getShell();
      waitBox.setIndeterminate( true );
      waitBox.setCanCancel( false );
      waitBox.setTitle( "Please Wait..." );
      waitBox.setMessage( "Pushing to the remote repository. Please Wait." );
      waitBox.setDialogParent( shell );
      waitBox.setRunnable( new WaitBoxRunnable( waitBox ) {
        @Override
        public void run() {

          shell.getDisplay().syncExec( new Runnable() {
            @Override
            public void run() {
              try {
                PushResult result = git.push().call().iterator().next();
                waitBox.stop();
                RemoteRefUpdate update = result.getRemoteUpdate( fullBranch );
                if ( update.getStatus() == RemoteRefUpdate.Status.OK ) {
                  messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Success" ) );
                } else {
                  messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
                }
                messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
                messageBox.setMessage( update.getStatus().toString() );
                messageBox.open();
              } catch ( GitAPIException e ) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            }
          } );
        }
        @Override
        public void cancel() {
          // TODO Auto-generated method stub
        }
      } );
      waitBox.start();
    } else {
      editRemote();
      push();
    }
  }

  public void editRemote() {
    final StoredConfig config = git.getRepository().getConfig();
    promptBox.setTitle( "Remote repository" );
    promptBox.setButtons( new DialogConstant[] { DialogConstant.OK, DialogConstant.CANCEL } );
    promptBox.setMessage( "URL/path (The remote name will be \"" + Constants.DEFAULT_REMOTE_NAME + "\")" );
    promptBox.setValue( getRemote() );
    promptBox.addDialogCallback( new XulDialogCallback<String>() {
      public void onClose( XulComponent component, Status status, String value ) {
        if ( !status.equals( Status.CANCEL ) ) {
          try {
            RemoteConfig remoteConfig = new RemoteConfig( config, Constants.DEFAULT_REMOTE_NAME );
            URIish uri = new URIish( value );
            remoteConfig.addURI( uri );
            remoteConfig.update( config );
            config.save();
            remoteBinding.fireSourceChanged();
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
  Git getGit() {
    return this.git;
  }

  @VisibleForTesting
  void setGit( Git git ) {
    this.git = git;
  }

  public void setPath( String path ) {
    this.path = path;
  }

  public String getPath() {
    return this.path;
  }

  public String getBranch() {
    try {
      return git.getRepository().getBranch();
    } catch ( Exception e ) {
      return "";
    }
  }

  public String getRemote() {
    try {
      StoredConfig config = git.getRepository().getConfig();
      RemoteConfig remoteConfig = new RemoteConfig( config, Constants.DEFAULT_REMOTE_NAME );
      return remoteConfig.getURIs().iterator().next().toString();
    } catch ( Exception e ) {
      return "";
    }
  }

  public String getAuthorName() {
    return uiGit.getAuthorName();
  }

  public void setAuthorName( String authorName ) {
    uiGit.setAuthorName( authorName );
  }

  public String getCommitMessage() {
    return uiGit.getCommitMessage();
  }

  public void setCommitMessage( String message ) {
    uiGit.setCommitMessage( message );
  }

  public UIRepositoryObjectRevisions getRevisionObjects() {
    UIRepositoryObjectRevisions revisions = new UIRepositoryObjectRevisions();
    try {
      Iterable<RevCommit> iterable = git.log().call();
      for ( RevCommit commit : iterable ) {
        PurObjectRevision rev = new PurObjectRevision(
          commit.getName().substring( 0, 7 ),
          commit.getAuthorIdent().getName(),
          commit.getAuthorIdent().getWhen(),
          commit.getShortMessage() );
        revisions.add( new UIRepositoryObjectRevision( (ObjectRevision) rev ) );
      }
    } catch ( Exception e ) {

    }
    return revisions;
  }

  public UIRepositoryObjects getUnstagedObjects() throws Exception {
    Set<String> files = new HashSet<String>();
    try {
      Status status = git.status().call();
      files.addAll( status.getModified() );
      files.addAll( status.getUntracked() );
    } catch ( Exception e ) {
    }
    return getObjects( files );
  }

  public UIRepositoryObjects getStagedObjects() throws Exception {
    Set<String> files = new HashSet<String>();
    try {
      Status status = git.status().call();
      files.addAll( status.getAdded() );
      files.addAll( status.getChanged() );
    } catch ( Exception e ) {
    }
    return getObjects( files );
  }

  private UIRepositoryObjects getObjects( Set<String> files ) throws Exception {
    UIRepositoryObjects objs = new UIRepositoryObjects();
    for ( String file : files ) {
      UIRepositoryObject obj;
      Date date = new Date();
      ObjectId id = new StringObjectId( file );
      if ( file.endsWith( ".ktr" ) ) {
        RepositoryElementMetaInterface rc =  new RepositoryObject(
            id, file, null, "-", date, RepositoryObjectType.TRANSFORMATION, "", false );
        obj = new UITransformation( rc, null, null );
      } else if ( file.endsWith( ".kjb" ) ) {
        RepositoryElementMetaInterface rc =  new RepositoryObject(
            id, file, null, "-", date, RepositoryObjectType.JOB, "", false );
        obj = new UIJob( rc, null, null );
      } else {
        RepositoryElementMetaInterface rc =  new RepositoryObject(
            id, file, null, "-", date, RepositoryObjectType.UNKNOWN, "", false );
        obj = new UIJob( rc, null, null );
      }
      objs.add( obj );
    }
    return objs;
  }
}
