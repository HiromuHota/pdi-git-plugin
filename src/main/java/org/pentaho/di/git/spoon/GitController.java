package org.pentaho.di.git.spoon;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.MissingObjectException;
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
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.git.spoon.dialog.DeleteBranchDialog;
import org.pentaho.di.git.spoon.dialog.MergeBranchDialog;
import org.pentaho.di.git.spoon.dialog.UsernamePasswordDialog;
import org.pentaho.di.git.spoon.model.GitRepository;
import org.pentaho.di.git.spoon.model.UIFile;
import org.pentaho.di.git.spoon.model.UIGit;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.ui.core.ConstUI;
import org.pentaho.di.ui.core.dialog.EnterSelectionDialog;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.spoon.MainSpoonPerspective;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.metastore.api.IMetaStore;
import org.pentaho.metastore.persist.MetaStoreFactory;
import org.pentaho.metastore.util.PentahoDefaults;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.components.XulButton;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.components.XulLabel;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulPromptBox;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.containers.XulBox;
import org.pentaho.ui.xul.containers.XulMenupopup;
import org.pentaho.ui.xul.containers.XulTree;
import org.pentaho.ui.xul.dnd.DropEvent;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.swt.SwtBindingFactory;
import org.pentaho.ui.xul.swt.custom.DialogConstant;
import org.pentaho.ui.xul.swt.tags.SwtButton;
import org.pentaho.ui.xul.util.XulDialogCallback.Status;
import org.pentaho.ui.xul.util.XulDialogLambdaCallback;

import com.google.common.annotations.VisibleForTesting;

public class GitController extends AbstractXulEventHandler {

  private static final Class<?> PKG = GitController.class;

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
  private XulButton commitButton;
  private XulButton pullButton;
  private XulButton pushButton;
  private XulButton branchButton;
  private XulButton mergeButton;
  private XulTextbox authorNameTextbox;
  private XulTextbox commitMessageTextbox;

  private BindingFactory bf = new SwtBindingFactory();
  private Binding revisionBinding;
  private Binding unstagedBinding;
  private Binding stagedBinding;

  private CCombo comboBranch;

  public GitController() {
    setName( "gitController" );
  }

  public void init() throws IllegalArgumentException, InvocationTargetException, XulException {
    XulTextbox diffText = (XulTextbox) document.getElementById( "diff" );
    Text text = (Text) diffText.getManagedObject();
    text.setFont( JFaceResources.getFont( JFaceResources.TEXT_FONT ) );

    // ToolTip
    branchButton = (SwtButton) document.getElementById( "branchButton" );
    branchButton.setTooltiptext( BaseMessages.getString( PKG, "Git.ToolTip.Branch" ) );
    mergeButton = (SwtButton) document.getElementById( "mergeButton" );
    mergeButton.setTooltiptext( BaseMessages.getString( PKG, "Git.ToolTip.Merge" ) );

    revisionTable = (XulTree) document.getElementById( "revision-table" );
    unstagedTable = (XulTree) document.getElementById( "unstaged-table" );
    stagedTable = (XulTree) document.getElementById( "staged-table" );
    commitButton = (XulButton) document.getElementById( "commit" );
    pullButton = (XulButton) document.getElementById( "pull" );
    pushButton = (XulButton) document.getElementById( "push" );

    createBindings();
    addWidgets();
  }

  private void createBindings() {
    XulLabel pathLabel = (XulLabel) document.getElementById( "path" );
    XulTextbox diffText = (XulTextbox) document.getElementById( "diff" );
    authorNameTextbox = (XulTextbox) document.getElementById( "author-name" );
    commitMessageTextbox = (XulTextbox) document.getElementById( "commit-message" );

    bf.setDocument( this.getXulDomContainer().getDocumentRoot() );
    bf.setBindingType( Binding.Type.ONE_WAY );
    bf.createBinding( this, "path", pathLabel, "value" );
    bf.createBinding( this, "diff", diffText, "value" );
    revisionBinding = bf.createBinding( uiGit, "revisions", revisionTable, "elements" );
    unstagedBinding = bf.createBinding( uiGit, "unstagedObjects", unstagedTable, "elements" );
    stagedBinding = bf.createBinding( uiGit, "stagedObjects", stagedTable, "elements" );

    bf.createBinding( revisionTable, "selectedItems", this, "selectedRevisions" );
    bf.createBinding( unstagedTable, "selectedItems", this, "selectedUnstagedObjects" );
    bf.createBinding( stagedTable, "selectedItems", this, "selectedStagedObjects" );

    bf.setBindingType( Binding.Type.BI_DIRECTIONAL );
    bf.createBinding( this, "authorName", authorNameTextbox, "value" );
    bf.createBinding( this, "commitMessage", commitMessageTextbox, "value" );
  }

  public void setActive() {
    XulDomContainer mainSpoonContainer = Spoon.getInstance().getXulDomContainer();
    mainSpoonContainer.getDocumentRoot().getElementById( "menu-git-remote-setting" ).setDisabled( false );
    commitButton.setDisabled( false );
    pullButton.setDisabled( false );
    pushButton.setDisabled( false );
    branchButton.setDisabled( false );
    mergeButton.setDisabled( false );

    commitMessageTextbox.setReadonly( false );
    authorNameTextbox.setReadonly( false );

    setAuthorName( uiGit.getAuthorName() );
    setCommitMessage( "" );
  }

  public void openGit() {
    IMetaStore metaStore = Spoon.getInstance().getMetaStore();
    MetaStoreFactory<GitRepository> repoFactory = new MetaStoreFactory<GitRepository>( GitRepository.class, metaStore, PentahoDefaults.NAMESPACE );

    try {
      List<String> names = repoFactory.getElementNames();
      Collections.sort( names );
      EnterSelectionDialog esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Repository", "Select the repository to open..." );
      String name = esd.open();

      if ( name == null ) {
        return;
      }
      GitRepository repo = repoFactory.loadElement( name );
      openGit( repo.getDirectory() );
      if ( isOpen() ) {
        setActive();
        fireSourceChanged();
      }
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  private void openGit( String baseDirectory ) {
    try {
      uiGit.openGit( baseDirectory );
      setPath( baseDirectory );
      setDiff( "" );
      setBranches();
    } catch ( RepositoryNotFoundException e ) {
      initGit( baseDirectory );
    } catch ( NullPointerException e ) {
      return;
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  private void addWidgets() {
    XulBox boxBranch = (XulBox) document.getElementById( "boxBranch" );
    ( (Composite) boxBranch.getManagedObject() ).setLayout( new FillLayout() );
    if ( comboBranch == null ) {
      comboBranch = new CCombo( (Composite) boxBranch.getManagedObject(), SWT.DROP_DOWN );
      comboBranch.addSelectionListener( new SelectionAdapter() {
        @Override
        public void widgetSelected( SelectionEvent e ) {
          String branch = ( (CCombo) e.getSource() ).getText();
          try {
            uiGit.checkout( branch );
          } catch ( Exception e1 ) {
            showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e1.getMessage() );
            String current = uiGit.getBranch();
            int currentIndex = 0;
            for ( String branch2 : uiGit.getBranches() ) {
              if ( current.equals( branch2 ) ) {
                comboBranch.select( currentIndex );
              }
              currentIndex++;
            }
          }
        }
      } );
    }
  }

  private void setBranches() {
    comboBranch.removeAll();
    if ( uiGit.isOpen() ) {
      String current = uiGit.getBranch();
      int currentIndex = 0;
      for ( String branch : uiGit.getBranches() ) {
        comboBranch.add( branch );
        if ( current.equals( branch ) ) {
          currentIndex = comboBranch.getItemCount() - 1;
        }
      }
      comboBranch.select( currentIndex );
    }
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
            showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
          }
        }
      } );
      confirmBox.open();
    } catch ( XulException e ) {
      e.printStackTrace();
    }
  }

  protected void fireSourceChanged() throws IllegalArgumentException, InvocationTargetException, XulException {
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

  public void openFile() {
    String baseDirectory = uiGit.getDirectory();
    getSelectedUnstagedObjects().stream()
      .filter( content -> content.getName().endsWith( Const.STRING_TRANS_DEFAULT_EXT ) || content.getName().endsWith( Const.STRING_JOB_DEFAULT_EXT ) )
      .forEach( content -> {
        String filePath = baseDirectory + Const.FILE_SEPARATOR + content.getName();
        try ( InputStream xmlStream = new FileInputStream( new File( filePath ) ) ) {
          EngineMetaInterface meta = null;
          Consumer<EngineMetaInterface> c = null;
          if ( filePath.endsWith( Const.STRING_TRANS_DEFAULT_EXT ) ) {
            meta = new TransMeta( xmlStream, null, true, null, null );
            c = meta0 -> Spoon.getInstance().addTransGraph( (TransMeta) meta0 );
          } else if ( filePath.endsWith( Const.STRING_JOB_DEFAULT_EXT ) ) {
            meta = new JobMeta( xmlStream, null, null );
            c = meta0 -> Spoon.getInstance().addJobGraph( (JobMeta) meta0 );
          }
          meta.clearChanged();
          meta.setFilename( filePath );
          c.accept( meta );
          Spoon.getInstance().loadPerspective( MainSpoonPerspective.ID );
        } catch ( Exception e ) {
          e.printStackTrace();
        }
      } );
  }

  public void diff() {
    String baseDirectory = uiGit.getDirectory();
    getSelectedUnstagedObjects().stream()
      .filter( content -> content.getName().endsWith( Const.STRING_TRANS_DEFAULT_EXT ) || content.getName().endsWith( Const.STRING_JOB_DEFAULT_EXT ) )
      .forEach( content -> {
        String filePath = baseDirectory + Const.FILE_SEPARATOR + content.getName();
        EngineMetaInterface metaOld = null, metaNew = null;
        Consumer<EngineMetaInterface> c = null;
        try {
          InputStream xmlStreamOld = uiGit.open( content.getName(), Constants.HEAD );
          String commitIdOld = uiGit.getCommitId( Constants.HEAD );
          InputStream xmlStreamNew = new FileInputStream( new File( filePath ) );
          if ( filePath.endsWith( Const.STRING_TRANS_DEFAULT_EXT ) ) {
            // Use temporary metaOld_ because metaOld will be modified before the 2nd comparison
            metaOld = new TransMeta( xmlStreamOld, null, true, null, null );
            metaNew = new TransMeta( xmlStreamNew, null, true, null, null );
            metaOld = PdiDiff.compareSteps( (TransMeta) metaOld, (TransMeta) metaNew, true );
            metaNew = PdiDiff.compareSteps( (TransMeta) metaNew, (TransMeta) metaOld, false );
            ( (TransMeta) metaOld ).setTransversion( "git: " + commitIdOld );
            ( (TransMeta) metaNew ).setTransversion( "git: WORKINGTREE" );
            c = meta -> Spoon.getInstance().addTransGraph( (TransMeta) meta );
          } else if ( filePath.endsWith( Const.STRING_JOB_DEFAULT_EXT ) ) {
            metaOld = new JobMeta( xmlStreamOld, null, null );
            metaNew = new JobMeta( xmlStreamNew, null, null );
            metaOld = PdiDiff.compareJobEntries( (JobMeta) metaOld, (JobMeta) metaNew, true );
            metaNew = PdiDiff.compareJobEntries( (JobMeta) metaNew, (JobMeta) metaOld, false );
            ( (JobMeta) metaOld ).setJobversion( "git: " + commitIdOld );
            ( (JobMeta) metaNew ).setJobversion( "git: WORKINGTREE" );
            c = meta0 -> Spoon.getInstance().addJobGraph( (JobMeta) meta0 );
          }
          xmlStreamOld.close();
          xmlStreamNew.close();

          metaOld.clearChanged();
          metaOld.setName( metaOld.getName() + " (HEAD->Working tree)" );
          metaOld.setFilename( filePath );
          c.accept( metaOld );
          metaNew.clearChanged();
          metaNew.setName( metaNew.getName() + " (Working tree->HEAD)" );
          metaNew.setFilename( filePath );
          c.accept( metaNew );
          Spoon.getInstance().loadPerspective( MainSpoonPerspective.ID );
        } catch ( MissingObjectException e ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "New file" );
        } catch ( Exception e ) {
          e.printStackTrace();
        }
      } );
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
        setAuthorName( uiGit.getAuthorName() );
        authorNameTextbox.setReadonly( false );
        setCommitMessage( "" );
        commitMessageTextbox.setReadonly( false );
        commitButton.setDisabled( false );
      } else {
        // TODO Should show diff for multiple commits
        String commitId = getSelectedRevisions().get( 0 ).getName();
        setDiff( uiGit.show( commitId ) );
        setAuthorName( uiGit.getAuthorName( commitId ) );
        authorNameTextbox.setReadonly( true );
        setCommitMessage( uiGit.getCommitMessage( commitId ) );
        commitMessageTextbox.setReadonly( true );
        commitButton.setDisabled( true );
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

  public void checkout() throws XulException, IllegalArgumentException, InvocationTargetException {
    String commitId = getSelectedRevisions().get( 0 ).getName();
    try {
      uiGit.checkout( commitId );
    } catch ( Exception e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    }
    fireSourceChanged();
    setBranches();
  }

  /**
   * Discard changes to selected unstaged files.
   * Equivalent to <tt>git checkout -- &lt;paths&gt;</tt>
   * @throws Exception
   */
  public void discard() throws Exception {
    XulConfirmBox confirmBox = (XulConfirmBox) document.createElement( "confirmbox" );
    confirmBox.setTitle( BaseMessages.getString( PKG, "Git.ContextMenu.Discard" ) );
    confirmBox.setMessage( "Are you sure?" );
    confirmBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
    confirmBox.setCancelLabel( BaseMessages.getString( PKG, "Dialog.Cancel" ) );
    confirmBox.addDialogCallback( (XulDialogLambdaCallback<Object>) ( sender, returnCode, retVal ) -> {
      if ( returnCode.equals( Status.ACCEPT ) ) {
        try {
          List<UIFile> contents = getSelectedUnstagedObjects();
          for ( UIFile content : contents ) {
            uiGit.checkoutPath( content.getName() );
          }
          fireSourceChanged();
        } catch ( Exception e ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
        }
      }
    } );
    confirmBox.open();
  }

  public void pull() {
    try {
      PullResult pullResult = uiGit.pull();
      processPullResult( pullResult );
    } catch ( TransportException e ) {
      if ( e.getMessage().contains( "Authentication is required but no CredentialsProvider has been registered" ) ) {
        pullWithUsernamePassword();
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    } catch ( Exception e ) {
      if ( uiGit.hasRemote() ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
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
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
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
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    } catch ( Exception e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
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
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
  }

  private void processPushResult( Iterable<PushResult> resultIterable ) throws Exception {
    PushResult result = resultIterable.iterator().next(); // should be only one element (remote=origin)
    for ( RemoteRefUpdate update : result.getRemoteUpdates() ) {
      if ( update.getStatus() == RemoteRefUpdate.Status.OK ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ),
            BaseMessages.getString( PKG, "Dialog.Success" ) );
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
            update.getStatus().toString()
            + ( update.getMessage() == null ? "" : "\n" + update.getMessage() ) );
      }
    }
  }

  public void branch() {
    ConstUI.displayMenu( (XulMenupopup) document.getElementById( "branchContextMenu" ), (Control) branchButton.getManagedObject() );
  }

  public void createBranch() throws XulException {
    XulPromptBox promptBox = (XulPromptBox) document.createElement( "promptbox" );
    promptBox.setTitle( BaseMessages.getString( PKG, "Git.ContextMenu.CreateBranch" ) );
    promptBox.setButtons( new DialogConstant[] { DialogConstant.OK, DialogConstant.CANCEL } );
    promptBox.setMessage( BaseMessages.getString( PKG, "Git.Dialog.CreateBranch" ) );
    promptBox.addDialogCallback( (XulDialogLambdaCallback<String>) ( component, status, value ) -> {
      if ( status.equals( Status.ACCEPT ) ) {
        try {
          uiGit.createBranch( value );
          uiGit.checkout( value );
          fireSourceChanged();
          setBranches();
        } catch ( Exception e ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
        }
      }
    } );
    promptBox.open();
  }

  public void deleteBranch() throws XulException {
    DeleteBranchDialog dialog = new DeleteBranchDialog( getShell() );
    List<String> branches = uiGit.getBranches();
    branches.remove( uiGit.getBranch() );
    dialog.setBranches( branches );
    if ( dialog.open() == Window.OK ) {
      String branch = dialog.getSelectedBranch();
      boolean isForce = dialog.isForce();
      try {
        uiGit.deleteBranch( branch, isForce );
        setBranches();
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
      } catch ( Exception e ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
  }

  public void merge() throws XulException {
    MergeBranchDialog dialog = new MergeBranchDialog( getShell() );
    List<String> branches = uiGit.getBranches();
    branches.remove( uiGit.getBranch() );
    dialog.setBranches( branches );
    if ( dialog.open() == Window.OK ) {
      String branch = dialog.getSelectedBranch();
      String mergeStrategy = dialog.getSelectedMergeStrategy();
      try {
        MergeResult result = uiGit.mergeBranch( branch, mergeStrategy );
        if ( result.getMergeStatus().isSuccessful() ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
          fireSourceChanged();
          setBranches();
        } else {
          if ( result.getMergeStatus() == MergeStatus.CONFLICTING ) {
            uiGit.resetHard();
          }
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), result.getMergeStatus().toString() );
        }
      } catch ( Exception e ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
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

  public boolean isOpen() {
    return uiGit.isOpen();
  }
}
