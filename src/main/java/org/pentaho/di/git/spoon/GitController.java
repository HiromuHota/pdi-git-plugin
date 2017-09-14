package org.pentaho.di.git.spoon;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationListener;
import org.eclipse.jface.viewers.ColumnViewerEditorDeactivationEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.base.AbstractMeta;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.git.spoon.dialog.DeleteBranchDialog;
import org.pentaho.di.git.spoon.dialog.MergeBranchDialog;
import org.pentaho.di.git.spoon.dialog.UsernamePasswordDialog;
import org.pentaho.di.git.spoon.model.GitRepository;
import org.pentaho.di.git.spoon.model.UIFile;
import org.pentaho.di.git.spoon.model.UIGit;
import org.pentaho.di.git.spoon.model.VCS;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.ui.core.dialog.EnterSelectionDialog;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
import org.pentaho.di.ui.spoon.MainSpoonPerspective;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.job.JobGraph;
import org.pentaho.di.ui.spoon.trans.TransGraph;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.components.XulButton;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.components.XulLabel;
import org.pentaho.ui.xul.components.XulMenuitem;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulPromptBox;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.components.XulTreeCol;
import org.pentaho.ui.xul.containers.XulTree;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.swt.SwtBindingFactory;
import org.pentaho.ui.xul.swt.SwtElement;
import org.pentaho.ui.xul.swt.custom.DialogConstant;
import org.pentaho.ui.xul.swt.tags.SwtTreeItem;
import org.pentaho.ui.xul.util.XulDialogCallback.Status;
import org.pentaho.ui.xul.util.XulDialogLambdaCallback;

import com.google.common.annotations.VisibleForTesting;

public class GitController extends AbstractXulEventHandler {

  private static final Class<?> PKG = GitController.class;

  private VCS vcs;
  private String path;
  private String branch;
  private String diff;
  private String authorName;
  private String commitMessage;
  private List<UIRepositoryObjectRevision> selectedRevisions;
  private List<UIFile> selectedChangedFiles;

  private XulTree revisionTable;
  private XulTree changedTable;
  private XulTreeCol checkboxCol;
  private XulMenuitem addToIndexMenuItem;
  private XulMenuitem rmFromIndexMenuItem;
  private XulMenuitem discardMenuItem;
  private XulButton commitButton;
  private XulButton pullButton;
  private XulButton pushButton;
  private XulButton branchButton;
  private XulButton tagButton;
  private XulTextbox authorNameTextbox;
  private XulTextbox commitMessageTextbox;

  private BindingFactory bf = new SwtBindingFactory();
  private Binding revisionBinding;
  private Binding changedBinding;

  public GitController() {
    setName( "gitController" );
  }

  public void init() throws IllegalArgumentException, InvocationTargetException, XulException {
    XulTextbox diffText = (XulTextbox) document.getElementById( "diff" );
    Text text = (Text) diffText.getManagedObject();
    text.setFont( JFaceResources.getFont( JFaceResources.TEXT_FONT ) );

    revisionTable = (XulTree) document.getElementById( "revision-table" );
    changedTable = (XulTree) document.getElementById( "changed-table" );
    checkboxCol = (XulTreeCol) document.getElementById( "checkbox-col" );
    addToIndexMenuItem = (XulMenuitem) document.getElementById( "menuitem-addtoindex" );
    rmFromIndexMenuItem = (XulMenuitem) document.getElementById( "menuitem-rmfromindex" );
    discardMenuItem = (XulMenuitem) document.getElementById( "menuitem-discard" );
    /*
     * Add a listener to add/reset file upon checking/unchecking changed files
     */
    TableViewer tv = (TableViewer) changedTable.getManagedObject();
    tv.getColumnViewerEditor().addEditorActivationListener( new ColumnViewerEditorActivationListener() {
      @Override
      public void beforeEditorDeactivated( ColumnViewerEditorDeactivationEvent event ) {
      }
      @Override
      public void beforeEditorActivated( ColumnViewerEditorActivationEvent event ) {
        ViewerCell viewerCell = (ViewerCell) event.getSource();
        SwtTreeItem selectedItem = (SwtTreeItem) viewerCell.getElement();
        UIFile file = (UIFile) selectedItem.getBoundObject();
        try {
          if ( file.getIsStaged() ) {
            vcs.reset( file.getName() );
          } else {
            vcs.add( file.getName() );
          }
        } catch ( Exception e ) {
          e.printStackTrace();
        }
      }

      @Override
      public void afterEditorDeactivated( ColumnViewerEditorDeactivationEvent event ) {
        try {
          changedBinding.fireSourceChanged();
        } catch ( Exception e ) {
          e.printStackTrace();
        }
      }

      @Override
      public void afterEditorActivated( ColumnViewerEditorActivationEvent event ) {
      }
    } );
    commitButton = (XulButton) document.getElementById( "commit" );
    pullButton = (XulButton) document.getElementById( "pull" );
    pushButton = (XulButton) document.getElementById( "push" );
    branchButton = (XulButton) document.getElementById( "branch" );
    tagButton = (XulButton) document.getElementById( "tag" );

    createBindings();
  }

  private void createBindings() {
    XulLabel pathLabel = (XulLabel) document.getElementById( "path" );
    XulLabel branchLabel = (XulLabel) document.getElementById( "branchLabel" );
    XulTextbox diffText = (XulTextbox) document.getElementById( "diff" );
    authorNameTextbox = (XulTextbox) document.getElementById( "author-name" );
    commitMessageTextbox = (XulTextbox) document.getElementById( "commit-message" );

    bf.setDocument( this.getXulDomContainer().getDocumentRoot() );
    bf.setBindingType( Binding.Type.ONE_WAY );
    bf.createBinding( this, "path", pathLabel, "value" );
    bf.createBinding( this, "branch", branchLabel, "value" );
    bf.createBinding( this, "diff", diffText, "value" );
    revisionBinding = bf.createBinding( this, "revisions", revisionTable, "elements" );
    changedBinding = bf.createBinding( this, "changedFiles", changedTable, "elements" );

    bf.createBinding( revisionTable, "selectedItems", this, "selectedRevisions" );
    bf.createBinding( changedTable, "selectedItems", this, "selectedChangedFiles" );

    bf.setBindingType( Binding.Type.BI_DIRECTIONAL );
    bf.createBinding( this, "authorName", authorNameTextbox, "value" );
    bf.createBinding( this, "commitMessage", commitMessageTextbox, "value" );
  }

  public void setActive() {
    document.getElementById( "config" ).setDisabled( false );
    commitButton.setDisabled( false );
    pullButton.setDisabled( false );
    pushButton.setDisabled( false );
    branchButton.setDisabled( false );
    tagButton.setDisabled( false );

    commitMessageTextbox.setReadonly( false );
    authorNameTextbox.setReadonly( false );
  }

  public void openGit( GitRepository repo ) {
    String baseDirectory = repo.getDirectory();
    try {
      if ( repo.getType() == null || repo.getType().equals( VCS.GIT ) ) {
        vcs = new UIGit();
      }
      vcs.openRepo( baseDirectory );
    } catch ( RepositoryNotFoundException e ) {
      initGit( baseDirectory );
    } catch ( Exception e ) {
      e.printStackTrace();
    }
    setActive();
    setPath( repo );
    setBranch( vcs.getBranch() );
    setDiff( "" );
    setAuthorName( vcs.getAuthorName() );
    setCommitMessage( "" );
    fireSourceChanged();
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
            vcs.initRepo( baseDirectory );
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

  public void fireSourceChanged() {
    try {
      revisionBinding.fireSourceChanged();
      changedBinding.fireSourceChanged();
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  public void addToIndex() throws Exception {
    List<UIFile> contents = getSelectedChangedFiles();
    for ( UIFile content : contents ) {
      if ( content.getChangeType() == ChangeType.DELETE ) {
        vcs.rm( content.getName() );
      } else {
        vcs.add( content.getName() );
      }
    }
    fireSourceChanged();
  }

  public void removeFromIndex() throws Exception {
    List<UIFile> contents = getSelectedChangedFiles();
    for ( UIFile content : contents ) {
      vcs.reset( content.getName() );
    }
    fireSourceChanged();
  }

  public void openFile() {
    String baseDirectory = vcs.getDirectory();
    getSelectedChangedFiles().stream()
      .forEach( content -> {
        String filePath = baseDirectory + Const.FILE_SEPARATOR + content.getName();
        String commitId;
        commitId = isOnlyWIP() ? VCS.WORKINGTREE : getFirstSelectedRevision().getName();
        try ( InputStream xmlStream = vcs.open( content.getName(), commitId ) ) {
          EngineMetaInterface meta = null;
          Consumer<EngineMetaInterface> c = null;
          if ( filePath.endsWith( Const.STRING_TRANS_DEFAULT_EXT ) ) {
            meta = new TransMeta( xmlStream, null, true, null, null );
            c = meta0 -> Spoon.getInstance().addTransGraph( (TransMeta) meta0 );
          } else if ( filePath.endsWith( Const.STRING_JOB_DEFAULT_EXT ) ) {
            meta = new JobMeta( xmlStream, null, null );
            c = meta0 -> Spoon.getInstance().addJobGraph( (JobMeta) meta0 );
          } else {
            showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Select a Kettle file" );
            return;
          }
          meta.clearChanged();
          meta.setFilename( filePath );
          if ( !isOnlyWIP() ) {
            meta.setName( String.format( "%s (%s)", meta.getName(), UIGit.abbreviate( commitId ) ) );
          }
          c.accept( meta );
          Spoon.getInstance().loadPerspective( MainSpoonPerspective.ID );
        } catch ( Exception e ) {
          e.printStackTrace();
        }
      } );
  }

  /**
   * Compare two versions of a particular Kettle file and
   * open them in the data integration perspective
   */
  public void visualdiff() {
    String baseDirectory = vcs.getDirectory();
    getSelectedChangedFiles().stream()
      .forEach( content -> {
        String filePath = baseDirectory + Const.FILE_SEPARATOR + content.getName();
        EngineMetaInterface metaOld = null, metaNew = null;
        Consumer<EngineMetaInterface> c = null;
        try {
          InputStream xmlStreamOld, xmlStreamNew;
          String commitIdOld, commitIdNew;
          if ( isOnlyWIP() ) {
            commitIdNew = VCS.WORKINGTREE;
            commitIdOld = Constants.HEAD;
          } else {
            commitIdNew = getFirstSelectedRevision().getName();
            commitIdOld = getSelectedRevisions().size() == 1 ? vcs.getParentCommitId( commitIdNew )
              : getLastSelectedRevision().getName();
          }
          xmlStreamOld = vcs.open( content.getName(), commitIdOld );
          xmlStreamNew = vcs.open( content.getName(), commitIdNew );
          if ( filePath.endsWith( Const.STRING_TRANS_DEFAULT_EXT ) ) {
            // Use temporary metaOld_ because metaOld will be modified before the 2nd comparison
            metaOld = new TransMeta( xmlStreamOld, null, true, null, null );
            metaNew = new TransMeta( xmlStreamNew, null, true, null, null );
            metaOld = PdiDiff.compareSteps( (TransMeta) metaOld, (TransMeta) metaNew, true );
            metaNew = PdiDiff.compareSteps( (TransMeta) metaNew, (TransMeta) metaOld, false );
            ( (TransMeta) metaOld ).setTransversion( "git: " + commitIdOld );
            ( (TransMeta) metaNew ).setTransversion( "git: " + commitIdNew );
            c = meta -> Spoon.getInstance().addTransGraph( (TransMeta) meta );
          } else if ( filePath.endsWith( Const.STRING_JOB_DEFAULT_EXT ) ) {
            metaOld = new JobMeta( xmlStreamOld, null, null );
            metaNew = new JobMeta( xmlStreamNew, null, null );
            metaOld = PdiDiff.compareJobEntries( (JobMeta) metaOld, (JobMeta) metaNew, true );
            metaNew = PdiDiff.compareJobEntries( (JobMeta) metaNew, (JobMeta) metaOld, false );
            ( (JobMeta) metaOld ).setJobversion( "git: " + commitIdOld );
            ( (JobMeta) metaNew ).setJobversion( "git: " + commitIdNew );
            c = meta0 -> Spoon.getInstance().addJobGraph( (JobMeta) meta0 );
          } else {
            showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Select a Kettle file" );
            return;
          }
          xmlStreamOld.close();
          xmlStreamNew.close();

          metaOld.clearChanged();
          metaOld.setName( String.format( "%s (%s -> %s)", metaOld.getName(), UIGit.abbreviate( commitIdOld ), UIGit.abbreviate( commitIdNew ) ) );
          metaOld.setFilename( filePath );
          c.accept( metaOld );
          metaNew.clearChanged();
          metaNew.setName( String.format( "%s (%s -> %s)", metaNew.getName(), UIGit.abbreviate( commitIdNew ), UIGit.abbreviate( commitIdOld ) ) );
          metaNew.setFilename( filePath );
          c.accept( metaNew );
          Spoon.getInstance().loadPerspective( MainSpoonPerspective.ID );
        } catch ( MissingObjectException | NullPointerException e ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "New file" );
        } catch ( Exception e ) {
          e.printStackTrace();
        }
      } );
  }

  public List<UIRepositoryObjectRevision> getSelectedRevisions() {
    return selectedRevisions;
  }

  private UIRepositoryObjectRevision getFirstSelectedRevision() {
    return getSelectedRevisions().get( 0 );
  }

  private UIRepositoryObjectRevision getLastSelectedRevision() {
    return getSelectedRevisions().get( getSelectedRevisions().size() - 1 );
  }

  public void setSelectedRevisions( List<UIRepositoryObjectRevision> selectedRevisions ) throws Exception {
    this.selectedRevisions = selectedRevisions;
    changedBinding.fireSourceChanged();
    setDiff( "" );
  }

  public List<UIFile> getSelectedChangedFiles() {
    return selectedChangedFiles;
  }

  public void setSelectedChangedFiles( List<UIFile> selectedFiles ) throws Exception {
    this.selectedChangedFiles = selectedFiles;
    if ( selectedFiles.size() != 0 ) {
      if ( isOnlyWIP() ) {
        if ( selectedFiles.get( 0 ).getIsStaged() ) {
          setDiff( vcs.diff( Constants.HEAD, VCS.INDEX, selectedFiles.get( 0 ).getName() ) );
        } else {
          setDiff( vcs.diff( VCS.INDEX, VCS.WORKINGTREE, selectedFiles.get( 0 ).getName() ) );
        }
      } else {
        String newCommitId = getFirstSelectedRevision().getName();
        String oldCommitId = getSelectedRevisions().size() == 1 ? vcs.getParentCommitId( newCommitId )
          : getLastSelectedRevision().getName();
        setDiff( vcs.diff( oldCommitId, newCommitId, selectedFiles.get( 0 ).getName() ) );
      }
    }
  }

  /**
   * Check if only WIP is selected
   * Return true if none is selected
   * @return
   */
  private Boolean isOnlyWIP() {
    return getSelectedRevisions().isEmpty()
        || ( getFirstSelectedRevision().getName().equals( VCS.WORKINGTREE ) && getSelectedRevisions().size() == 1 );
  }

  private Shell getShell() {
    return Spoon.getInstance().getShell();
  }

  public String getPath() {
    return this.path;
  }

  public void setPath( GitRepository repo ) {
    this.path = repo.getName();
    ( (Control) document.getElementById( "path" ).getManagedObject() ).setToolTipText( repo.getDirectory() );
    firePropertyChange( "path", null, path );
    ( (SwtElement) document.getElementById( "path" ).getParent() ).layout();
  }

  public String getBranch() {
    return this.branch;
  }

  public void setBranch( String branch ) {
    this.branch = branch;
    firePropertyChange( "branch", null, branch );
    ( (SwtElement) document.getElementById( "branchLabel" ).getParent() ).layout();
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

  public UIRepositoryObjectRevisions getRevisions() throws Exception {
    if ( !isOpen() ) {
      return null;
    } else {
      return vcs.getRevisions();
    }
  }

  public List<UIFile> getChangedFiles() throws Exception {
    if ( getSelectedRevisions() == null ) { // when Spoon is starting
      return null;
    }
    List<UIFile> changedFiles = new ArrayList<UIFile>();
    if ( isOnlyWIP() ) {
      addToIndexMenuItem.setDisabled( false );
      rmFromIndexMenuItem.setDisabled( false );
      discardMenuItem.setDisabled( false );
      checkboxCol.setEditable( true );
      authorNameTextbox.setReadonly( false );
      commitMessageTextbox.setReadonly( false );
      commitButton.setDisabled( false );

      setAuthorName( vcs.getAuthorName() );
      setCommitMessage( "" );

      changedFiles.addAll( vcs.getUnstagedFiles() );
      changedFiles.addAll( vcs.getStagedFiles() );
    } else {
      addToIndexMenuItem.setDisabled( true );
      rmFromIndexMenuItem.setDisabled( true );
      discardMenuItem.setDisabled( true );
      checkboxCol.setEditable( false );
      authorNameTextbox.setReadonly( true );
      commitMessageTextbox.setReadonly( true );
      commitButton.setDisabled( true );

      if ( getSelectedRevisions().size() == 1 ) {
        String commitId = getFirstSelectedRevision().getName();
        setAuthorName( vcs.getAuthorName( commitId ) );
        setCommitMessage( vcs.getCommitMessage( commitId ) );

        changedFiles.addAll( vcs.getStagedFiles( vcs.getParentCommitId( getFirstSelectedRevision().getName() ), getFirstSelectedRevision().getName() ) );
      } else {
        setAuthorName( "" );
        setCommitMessage( "" );

        String newCommitId = getFirstSelectedRevision().getName();
        String oldCommitId = getLastSelectedRevision().getName();
        changedFiles.addAll( vcs.getStagedFiles( oldCommitId, newCommitId ) );
      }
    }
    return changedFiles;
  }

  public void commit() throws Exception {
    if ( !vcs.hasStagedFiles() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
          "There are no staged files" );
      return;
    }

    try {
      vcs.commit( getAuthorName(), getCommitMessage() );
      setCommitMessage( "" );
      fireSourceChanged();
    } catch ( NullPointerException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
          "Malformed author name" );
    }
  }

  public void checkout() {
    String commitId = getFirstSelectedRevision().getName();
    try {
      vcs.checkout( commitId );
      setBranch( vcs.getBranch() );
      fireSourceChanged();
    } catch ( Exception e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    }
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
          List<UIFile> contents = getSelectedChangedFiles();
          for ( UIFile content : contents ) {
            if ( content.getIsStaged() ) {
              showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Please unstage first" );
            } else {
              vcs.checkout( null, content.getName() );
            }
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
    if ( anyChangedTabs() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "One or more tabs have unsaved changes" );
      return;
    }
    if ( !vcs.isClean() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Dirty working-tree" );
      return;
    }
    if ( !vcs.hasRemote() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Please setup a remote" );
      return;
    }
    try {
      PullResult pullResult = vcs.pull();
      processPullResult( pullResult );
    } catch ( TransportException e ) {
      if ( e.getMessage().contains( "Authentication is required but no CredentialsProvider has been registered" ) ) {
        pullWithUsernamePassword();
      } else if ( e.getMessage().contains( "not authorized" ) ) {
        pushWithUsernamePassword();
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    } catch ( Exception e ) {
      if ( vcs.hasRemote() ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
            "Please setup a remote" );
      }
    }
  }

  private void pullWithUsernamePassword() {
    UsernamePasswordDialog dialog = new UsernamePasswordDialog( getShell() );
    if ( dialog.open() == Window.OK ) {
      String username = dialog.getUsername();
      String password = dialog.getPassword();
      try {
        PullResult pullResult = vcs.pull( username, password );
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
        vcs.resetHard();
      } else if ( mergeResult.getFailingPaths().size() != 0 ) {
        for ( Entry<String, MergeFailureReason> failingPath : mergeResult.getFailingPaths().entrySet() ) {
          msg += "\n" + String.format( "%s: %s", failingPath.getKey(), failingPath.getValue() );
        }
      }
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), msg );
    }
  }

  public void push() {
    push( "default" );
  }

  public void push( String type ) {
    if ( !vcs.hasRemote() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Please setup a remote" );
      return;
    }
    String name = null;
    List<String> names;
    EnterSelectionDialog esd;
    switch( type ) {
      case VCS.TYPE_BRANCH:
        names = vcs.getLocalBranches();
        esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Branch", "Select the branch to push..." );
        name = esd.open();
        if ( name == null ) {
          return;
        }
        break;
      case VCS.TYPE_TAG:
        names = vcs.getTags();
        esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Tag", "Select the tag to push..." );
        name = esd.open();
        if ( name == null ) {
          return;
        }
        break;
    }
    try {
      Iterable<PushResult> resultIterable = vcs.push( name );
      processPushResult( resultIterable );
    } catch ( TransportException e ) {
      if ( e.getMessage().contains( "Authentication is required but no CredentialsProvider has been registered" ) ) {
        pushWithUsernamePassword();
      } else if ( e.getMessage().contains( "not authorized" ) ) {
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
        Iterable<PushResult> resultIterable = vcs.push( username, password );
        processPushResult( resultIterable );
      } catch ( Exception e ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
  }

  private void processPushResult( Iterable<PushResult> resultIterable ) throws Exception {
    resultIterable.forEach( result -> { // for each (push)url
      StringBuilder sb = new StringBuilder();
      result.getRemoteUpdates().stream()
        .filter( update -> update.getStatus() != RemoteRefUpdate.Status.OK )
        .filter( update -> update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE )
        .forEach( update -> { // for each failed refspec
          sb.append(
            result.getURI().toString()
            + "\n" + update.getSrcRef().toString()
            + "\n" + update.getStatus().toString()
            + ( update.getMessage() == null ? "" : "\n" + update.getMessage() )
            + "\n\n"
          );
        } );
      if ( sb.length() == 0 ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), sb.toString() );
      }
    } );
  }

  public void checkoutBranch() {
    List<String> names = vcs.getBranches();
    names.remove( vcs.getBranch() );
    EnterSelectionDialog esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Branch", "Select the branch to checkout..." );
    String name = esd.open();
    if ( name != null ) {
      try {
        vcs.checkout( name );
        setBranch( vcs.getBranch() );
        fireSourceChanged();
      } catch ( Exception e ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
  }

  public void createBranch() throws XulException {
    XulPromptBox promptBox = (XulPromptBox) document.createElement( "promptbox" );
    promptBox.setTitle( BaseMessages.getString( PKG, "Git.Dialog.Branch.Create.Title" ) );
    promptBox.setButtons( new DialogConstant[] { DialogConstant.OK, DialogConstant.CANCEL } );
    promptBox.setMessage( BaseMessages.getString( PKG, "Git.Dialog.Branch.Create.Message" ) );
    promptBox.addDialogCallback( (XulDialogLambdaCallback<String>) ( component, status, value ) -> {
      if ( status.equals( Status.ACCEPT ) ) {
        try {
          vcs.createBranch( value );
          vcs.checkout( value );
          setBranch( value );
          fireSourceChanged();
        } catch ( Exception e ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
        }
      }
    } );
    promptBox.open();
  }

  public void deleteBranch() throws XulException {
    DeleteBranchDialog dialog = new DeleteBranchDialog( getShell() );
    List<String> branches = vcs.getLocalBranches();
    branches.remove( vcs.getBranch() );
    dialog.setBranches( branches );
    if ( dialog.open() == Window.OK ) {
      String branch = dialog.getSelectedBranch();
      boolean isForce = dialog.isForce();
      if ( branch == null ) {
        return;
      }
      try {
        vcs.deleteBranch( branch, isForce );
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
      } catch ( Exception e ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
  }

  public void checkoutTag() throws Exception {
    List<String> names = vcs.getTags();
    EnterSelectionDialog esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Tag", "Select a tag to checkout..." );
    String name = esd.open();
    if ( name != null ) {
      try {
        vcs.checkout( name );
        setBranch( vcs.getBranch() );
        fireSourceChanged();
      } catch ( Exception e ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
  }

  public void createTag() throws XulException {
    XulPromptBox promptBox = (XulPromptBox) document.createElement( "promptbox" );
    promptBox.setTitle( BaseMessages.getString( PKG, "Git.Dialog.Tag.Create.Title" ) );
    promptBox.setButtons( new DialogConstant[] { DialogConstant.OK, DialogConstant.CANCEL } );
    promptBox.setMessage( BaseMessages.getString( PKG, "Git.Dialog.Tag.Create.Message" ) );
    promptBox.addDialogCallback( (XulDialogLambdaCallback<String>) ( component, status, value ) -> {
      if ( status.equals( Status.ACCEPT ) ) {
        try {
          vcs.createTag( value );
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
        } catch ( Exception e ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
        }
      }
    } );
    promptBox.open();
  }

  public void deleteTag() throws Exception {
    List<String> names = vcs.getTags();
    EnterSelectionDialog esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Tag", "Select a tag to delete..." );
    String name = esd.open();
    if ( name != null ) {
      try {
        vcs.deleteTag( name );
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
      } catch ( Exception e ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
  }

  public void merge() throws XulException {
    if ( anyChangedTabs() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "One or more tabs have unsaved changes" );
      return;
    }
    if ( !vcs.isClean() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Dirty working-tree" );
      return;
    }
    MergeBranchDialog dialog = new MergeBranchDialog( getShell() );
    List<String> branches = vcs.getLocalBranches();
    branches.remove( vcs.getBranch() );
    dialog.setBranches( branches );
    if ( dialog.open() == Window.OK ) {
      String branch = dialog.getSelectedBranch();
      String mergeStrategy = dialog.getSelectedMergeStrategy();
      try {
        MergeResult result = vcs.mergeBranch( branch, mergeStrategy );
        if ( result.getMergeStatus().isSuccessful() ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
          fireSourceChanged();
        } else {
          if ( result.getMergeStatus() == MergeStatus.CONFLICTING ) {
            vcs.resetHard();
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
      promptBox.setValue( vcs.getRemote() );
      promptBox.addDialogCallback( (XulDialogLambdaCallback<String>) ( component, status, value ) -> {
        if ( status.equals( Status.ACCEPT ) ) {
          try {
            vcs.addRemote( value );
          } catch ( URISyntaxException e ) {
            if ( value.equals( "" ) ) {
              try {
                vcs.removeRemote();
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
  void setUIGit( VCS uiGit ) {
    this.vcs = uiGit;
  }

  public boolean isOpen() {
    return vcs != null;
  }

  @VisibleForTesting
  boolean anyChangedTabs() {
    return Spoon.getInstance().delegates.tabs.getTabs().stream()
    .map( mapEntry -> {
      AbstractMeta meta = null;
      if ( mapEntry != null ) {
        if ( mapEntry.getObject() instanceof TransGraph ) {
          meta = (AbstractMeta) ( mapEntry.getObject() ).getMeta();
        }
        if ( mapEntry.getObject() instanceof JobGraph ) {
          meta = (AbstractMeta) ( mapEntry.getObject() ).getMeta();
        }
      }
      return meta;
    })
    .filter( meta -> meta != null )
    .filter( meta -> meta.hasChanged() )
    .findAny().isPresent();
  }
}
