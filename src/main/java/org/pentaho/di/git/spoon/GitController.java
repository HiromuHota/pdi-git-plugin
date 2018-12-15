/*
 * Copyright 2017 Hitachi America, Ltd., R&D.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pentaho.di.git.spoon;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationListener;
import org.eclipse.jface.viewers.ColumnViewerEditorDeactivationEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.base.AbstractMeta;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.variables.Variables;
import org.pentaho.di.git.spoon.dialog.DeleteBranchDialog;
import org.pentaho.di.git.spoon.model.GitRepository;
import org.pentaho.di.git.spoon.model.IVCS;
import org.pentaho.di.git.spoon.model.SVN;
import org.pentaho.di.git.spoon.model.UIFile;
import org.pentaho.di.git.spoon.model.UIGit;
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

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GitController extends AbstractXulEventHandler {

  private static final Class<?> PKG = GitController.class;

  private IVCS vcs;
  private String path;
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
  private Binding branchBinding;
  private Binding diffBinding;

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
            vcs.resetPath( file.getName() );
          } else {
            if ( file.getChangeType() == ChangeType.DELETE ) {
              vcs.rm( file.getName() );
            } else {
              vcs.add( file.getName() );
            }
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
    branchBinding = bf.createBinding( this, "branch", branchLabel, "value" );
    diffBinding = bf.createBinding( this, "diff", diffText, "value" );
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
    branchButton.setDisabled( false );
    tagButton.setDisabled( false );
    ( (XulButton) document.getElementById( "refresh" ) ).setDisabled( false );

    commitMessageTextbox.setReadonly( false );
    authorNameTextbox.setReadonly( false );

    // Depends on VCS type
    pullButton.setLabel( BaseMessages.getString( PKG, vcs.getType() + ".Pull" ) );
    ((XulMenuitem) document.getElementById( "branch-checkout" )).setLabel( BaseMessages.getString( PKG, vcs.getType() + ".Checkout" ) );
    ((XulMenuitem) document.getElementById( "branch-merge" )).setLabel( BaseMessages.getString( PKG, vcs.getType() + ".Merge" ) );
    ((XulMenuitem) document.getElementById( "tag-checkout" )).setLabel( BaseMessages.getString( PKG, vcs.getType() + ".Checkout" ) );
    ((XulMenuitem) document.getElementById( "menuitem-checkout" )).setLabel( BaseMessages.getString( PKG, vcs.getType() + ".ContextMenu.Checkout" ) );
    if ( vcs.getType().equals( IVCS.GIT ) ) {
      pushButton.setDisabled( false );
      document.getElementById( "branch-push" ).setDisabled( false );
      document.getElementById( "tag-push" ).setDisabled( false );
      document.getElementById( "menuitem-reset" ).setDisabled( false );
    } else {
      pushButton.setDisabled( true );
      document.getElementById( "branch-push" ).setDisabled( true );
      document.getElementById( "tag-push" ).setDisabled( true );
      document.getElementById( "menuitem-reset" ).setDisabled( true );
    }
  }

  public void openGit( GitRepository repo ) {
    VariableSpace space = new Variables(  );
    space.initializeVariablesFrom( null );

    String baseDirectory = space.environmentSubstitute( repo.getDirectory() );

    try {
      if ( repo.getType() == null || repo.getType().equals( IVCS.GIT ) ) {
        vcs = new UIGit();
      } else {
        vcs = new SVN();
      }
      vcs.setShell( getShell() );
      vcs.openRepo( baseDirectory );
    } catch ( RepositoryNotFoundException e ) {
      initGit( baseDirectory );
    } catch ( Exception e ) {
      e.printStackTrace();
    }
    // Create bindings if first time
    if ( isOpen() && changedBinding == null ) {
      createBindings();
    }
    setActive();
    setPath( repo );
    setAuthorName( vcs.getAuthorName( IVCS.WORKINGTREE ) );
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
      branchBinding.fireSourceChanged();
      ( (SwtElement) document.getElementById( "branchLabel" ).getParent() ).layout();
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
    changedBinding.fireSourceChanged();
  }

  public void removeFromIndex() throws Exception {
    List<UIFile> contents = getSelectedChangedFiles();
    for ( UIFile content : contents ) {
      vcs.resetPath( content.getName() );
    }
    changedBinding.fireSourceChanged();
  }

  public void openFile() {
    String baseDirectory = vcs.getDirectory();
    getSelectedChangedFiles().stream()
      .forEach( content -> {
        String filePath = baseDirectory + Const.FILE_SEPARATOR + content.getName();
        String commitId;
        commitId = isOnlyWIP() ? IVCS.WORKINGTREE : getFirstSelectedRevision().getName();
        try ( InputStream xmlStream = vcs.open( content.getName(), commitId ) ) {
          EngineMetaInterface meta = null;
          if ( filePath.endsWith( Const.STRING_TRANS_DEFAULT_EXT )
              || FilenameUtils.removeExtension( filePath ).endsWith( Const.STRING_TRANS_DEFAULT_EXT ) ) {
            meta = new TransMeta( xmlStream, null, true, null, null );
          } else if ( filePath.endsWith( Const.STRING_JOB_DEFAULT_EXT )
              || FilenameUtils.removeExtension( filePath ).endsWith( Const.STRING_JOB_DEFAULT_EXT ) ) {
            meta = new JobMeta( xmlStream, null, null );
          } else {
            showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Select a Kettle file" );
            return;
          }
          if ( !isOnlyWIP() ) {
            meta.setName( String.format( "%s (%s)", meta.getName(), vcs.getShortenedName( commitId, IVCS.TYPE_COMMIT ) ) );
          }
          addGraph( meta, filePath );
          loadMainPerspective();
        } catch ( KettleXMLException e ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
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
        /*
         * Determine which versions and open the Kettle files
         */
        InputStream xmlStreamOld, xmlStreamNew;
        String commitIdOld, commitIdNew;
        String filePathOld, filePathNew;
        filePathOld = filePathNew = filePath;
        if ( ( filePath.endsWith( Const.STRING_TRANS_DEFAULT_EXT )
            || filePath.endsWith( Const.STRING_JOB_DEFAULT_EXT ) ) ) { // Kettle files
          if ( content.getChangeType() == ChangeType.ADD ) {
            showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "New file" );
            return;
          }
          if ( isOnlyWIP() ) {
            commitIdNew = IVCS.WORKINGTREE;
            commitIdOld = Constants.HEAD;
          } else {
            commitIdNew = getFirstSelectedRevision().getName();
            commitIdOld = getSelectedRevisions().size() == 1 ? vcs.getParentCommitId( commitIdNew )
              : getLastSelectedRevision().getName();
          }
          xmlStreamOld = vcs.open( content.getName(), commitIdOld );
          xmlStreamNew = vcs.open( content.getName(), commitIdNew );
        } else if ( FilenameUtils.removeExtension( filePath ).endsWith( Const.STRING_TRANS_DEFAULT_EXT )
            || FilenameUtils.removeExtension( filePath ).endsWith( Const.STRING_JOB_DEFAULT_EXT ) ) { // conflicted Kettle files
          commitIdNew = FilenameUtils.getExtension( filePath );
          filePath = FilenameUtils.removeExtension( filePath );
          List<String> candidates = FileUtils.listFiles( new File( FilenameUtils.getFullPath( filePath ) ),
              new WildcardFileFilter( FilenameUtils.removeExtension( content.getName() ) + ".*" ),
              null ).stream()
                .map( file -> FilenameUtils.getExtension( file.getName() ) )
                .filter( candidate -> !candidate.equals( commitIdNew ) )
                .collect( Collectors.toList() );
          if ( candidates.size() == 1 ) {
            commitIdOld = candidates.iterator().next();
          } else {
            EnterSelectionDialog esd = new EnterSelectionDialog( getShell(), candidates.toArray( new String[candidates.size()] ), "Select version", "Select a version to compare with..." );
            commitIdOld = esd.open();
          }
          xmlStreamNew = vcs.open( content.getName(), IVCS.WORKINGTREE );
          xmlStreamOld = vcs.open( FilenameUtils.removeExtension( content.getName() ) + "." + commitIdOld, IVCS.WORKINGTREE );
          filePathOld = filePath + "." + commitIdOld;
        } else {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Select a Kettle file" );
          return;
        }

        EngineMetaInterface metaOld = null, metaNew = null;
        try {
          if ( filePath.endsWith( Const.STRING_TRANS_DEFAULT_EXT ) ) {
            // Use temporary metaOld_ because metaOld will be modified before the 2nd comparison
            metaOld = new TransMeta( xmlStreamOld, null, true, null, null );
            metaNew = new TransMeta( xmlStreamNew, null, true, null, null );
            metaOld = PdiDiff.compareSteps( (TransMeta) metaOld, (TransMeta) metaNew, true );
            metaNew = PdiDiff.compareSteps( (TransMeta) metaNew, (TransMeta) metaOld, false );
            ( (TransMeta) metaOld ).setTransversion( "git: " + commitIdOld );
            ( (TransMeta) metaNew ).setTransversion( "git: " + commitIdNew );
          } else {
            metaOld = new JobMeta( xmlStreamOld, null, null );
            metaNew = new JobMeta( xmlStreamNew, null, null );
            metaOld = PdiDiff.compareJobEntries( (JobMeta) metaOld, (JobMeta) metaNew, true );
            metaNew = PdiDiff.compareJobEntries( (JobMeta) metaNew, (JobMeta) metaOld, false );
            ( (JobMeta) metaOld ).setJobversion( "git: " + commitIdOld );
            ( (JobMeta) metaNew ).setJobversion( "git: " + commitIdNew );
          }
          xmlStreamOld.close();
          xmlStreamNew.close();

          metaOld.setName( String.format( "%s (%s -> %s)", metaOld.getName(),
              vcs.getShortenedName( commitIdOld, IVCS.TYPE_COMMIT ), vcs.getShortenedName( commitIdNew, IVCS.TYPE_COMMIT ) ) );
          metaNew.setName( String.format( "%s (%s -> %s)", metaNew.getName(),
              vcs.getShortenedName( commitIdNew, IVCS.TYPE_COMMIT ), vcs.getShortenedName( commitIdOld, IVCS.TYPE_COMMIT ) ) );

          addGraph( metaOld, filePathOld );
          addGraph( metaNew, filePathNew );
          loadMainPerspective();
        } catch ( KettleXMLException e ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
        } catch ( Exception e ) {
          e.printStackTrace();
        }
      } );
  }

  @VisibleForTesting
  void addGraph( EngineMetaInterface meta, String filePath ) {
    meta.clearChanged();
    meta.setFilename( filePath );
    if ( meta instanceof TransMeta ) {
      Spoon.getInstance().addTransGraph( (TransMeta) meta );
    } else {
      Spoon.getInstance().addJobGraph( (JobMeta) meta );
    }
  }

  @VisibleForTesting
  void loadMainPerspective() {
    Spoon.getInstance().loadPerspective( MainSpoonPerspective.ID );
  }

  public List<UIRepositoryObjectRevision> getSelectedRevisions() {
    return selectedRevisions;
  }

  @VisibleForTesting
  UIRepositoryObjectRevision getFirstSelectedRevision() {
    return getSelectedRevisions().get( 0 );
  }

  private UIRepositoryObjectRevision getLastSelectedRevision() {
    return getSelectedRevisions().get( getSelectedRevisions().size() - 1 );
  }

  public void setSelectedRevisions( List<UIRepositoryObjectRevision> selectedRevisions ) throws Exception {
    this.selectedRevisions = selectedRevisions;
    changedBinding.fireSourceChanged();
  }

  public List<UIFile> getSelectedChangedFiles() {
    return selectedChangedFiles;
  }

  public void setSelectedChangedFiles( List<UIFile> selectedFiles ) throws Exception {
    this.selectedChangedFiles = selectedFiles;
    diffBinding.fireSourceChanged();
  }

  /**
   * Check if only WIP is selected
   * Return true if none is selected
   * @return
   */
  @VisibleForTesting
  Boolean isOnlyWIP() {
    return CollectionUtils.isEmpty( getSelectedRevisions() )
        || ( getFirstSelectedRevision().getName().equals( IVCS.WORKINGTREE ) && getSelectedRevisions().size() == 1 );
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
    return vcs.getBranch();
  }

  public String getDiff() {
    try {
      List<UIFile> selectedFiles = getSelectedChangedFiles();
      if ( selectedFiles.size() != 0 ) {
        if ( isOnlyWIP() ) {
          if ( selectedFiles.get( 0 ).getIsStaged() ) {
            return vcs.diff( Constants.HEAD, IVCS.INDEX, selectedFiles.get( 0 ).getName() );
          } else {
            return vcs.diff( IVCS.INDEX, IVCS.WORKINGTREE, selectedFiles.get( 0 ).getName() );
          }
        } else {
          String newCommitId = getFirstSelectedRevision().getName();
          String oldCommitId = getSelectedRevisions().size() == 1 ? vcs.getParentCommitId( newCommitId )
            : getLastSelectedRevision().getName();
          return vcs.diff( oldCommitId, newCommitId, selectedFiles.get( 0 ).getName() );
        }
      } else {
        return "";
      }
    } catch ( Exception e ) {
      return e.getMessage();
    }
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

  public UIRepositoryObjectRevisions getRevisions() {
    return vcs.getRevisions();
  }

  public List<UIFile> getChangedFiles() {
    List<UIFile> changedFiles = new ArrayList<UIFile>();
    if ( isOnlyWIP() ) {
      addToIndexMenuItem.setDisabled( false );
      rmFromIndexMenuItem.setDisabled( false );
      discardMenuItem.setDisabled( false );
      checkboxCol.setEditable( true );
      authorNameTextbox.setReadonly( false );
      commitMessageTextbox.setReadonly( false );
      commitButton.setDisabled( false );

      setAuthorName( vcs.getAuthorName( IVCS.WORKINGTREE ) );
      setCommitMessage( vcs.getCommitMessage( IVCS.WORKINGTREE ) );

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

        changedFiles.addAll( vcs.getStagedFiles( vcs.getParentCommitId( commitId ), commitId ) );
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

  public void commit() {
    if ( !vcs.hasStagedFiles() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
          "There are no staged files" );
      return;
    }
    if ( getCommitMessage().equals( "" ) ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
          "Aborting commit due to empty commit message" );
      return;
    }

    try {
      if ( vcs.commit( getAuthorName(), getCommitMessage() ) ) {
        setCommitMessage( "" );
        fireSourceChanged();
      }
    } catch ( NullPointerException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ),
          "Malformed author name" );
    } catch ( Exception e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    }
  }

  public void checkout() {
    checkout( IVCS.TYPE_COMMIT );
  }

  public void checkout( String type ) {
    String name = null;
    List<String> names;
    EnterSelectionDialog esd;
    Consumer<String> consumer = null;
    switch ( type ) {
      case IVCS.TYPE_BRANCH:
        names = vcs.getBranches();
        names.remove( vcs.getBranch() );
        esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Branch", "Select the branch to checkout..." );
        name = esd.open();
        if ( name == null ) {
          return;
        }
        consumer = s -> vcs.checkoutBranch( s );
        break;
      case IVCS.TYPE_TAG:
        names = vcs.getTags();
        esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Tag", "Select a tag to checkout..." );
        name = esd.open();
        if ( name == null ) {
          return;
        }
        consumer = s -> vcs.checkoutTag( s );
        break;
      default:
        name = getFirstSelectedRevision().getName();
        consumer = s -> vcs.checkout( s );
    }
    try {
      name = vcs.getExpandedName( name, type );
      consumer.accept( name );
      fireSourceChanged();
    } catch ( Exception e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    }
  }

  /**
   * Reset to the selected commit
   * @throws XulException
   */
  public void reset() throws XulException {
    XulConfirmBox confirmBox = (XulConfirmBox) document.createElement( "confirmbox" );
    confirmBox.setTitle( BaseMessages.getString( PKG, "Git.ContextMenu.Reset" ) );
    confirmBox.setMessage( "Are you sure?" );
    confirmBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
    confirmBox.setCancelLabel( BaseMessages.getString( PKG, "Dialog.Cancel" ) );
    confirmBox.addDialogCallback( (XulDialogLambdaCallback<Object>) ( sender, returnCode, retVal ) -> {
      if ( returnCode.equals( Status.ACCEPT ) ) {
        String commitId = getFirstSelectedRevision().getName();
        try {
          vcs.reset( commitId );
          fireSourceChanged();
        } catch ( Exception e ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
        }
      }
    } );
    confirmBox.open();
  }

  /**
   * Rollback to the selected commit, but does not make a commit
   */
  public void rollback() {
    if ( anyChangedTabs() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "One or more tabs have unsaved changes" );
      return;
    }
    vcs.rollback( getFirstSelectedRevision().getName() );
    fireSourceChanged();
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
        List<UIFile> contents = getSelectedChangedFiles();
        for ( UIFile content : contents ) {
          vcs.revertPath( content.getName() );
        }
        fireSourceChanged();
      }
    } );
    confirmBox.open();
  }

  public void pull() {
    if ( anyChangedTabs() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "One or more tabs have unsaved changes" );
      return;
    }
    if ( vcs.pull() ) {
      fireSourceChanged();
    }
  }

  public void push() {
    push( "default" );
  }

  public void push( String type ) {
    vcs.push( type );
  }

  public void createBranch() throws XulException {
    XulPromptBox promptBox = (XulPromptBox) document.createElement( "promptbox" );
    promptBox.setTitle( BaseMessages.getString( PKG, "Git.Dialog.Branch.Create.Title" ) );
    promptBox.setButtons( new DialogConstant[] { DialogConstant.OK, DialogConstant.CANCEL } );
    promptBox.setMessage( BaseMessages.getString( PKG, "Git.Dialog.Branch.Create.Message" ) );
    promptBox.addDialogCallback( (XulDialogLambdaCallback<String>) ( component, status, value ) -> {
      if ( status.equals( Status.ACCEPT ) ) {
        String branch = getBranch();
        if ( vcs.createBranch( value ) ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
          if ( !branch.equals( getBranch() ) ) { // Creating a branch involves checkingout that branch in Git
            fireSourceChanged();
          }
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
      if ( vcs.deleteBranch( branch, isForce ) ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
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
        if ( vcs.createTag( value ) ) {
          showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
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
      if ( vcs.deleteTag( name ) ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
      }
    }
  }

  public void merge() throws XulException {
    if ( anyChangedTabs() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "One or more tabs have unsaved changes" );
      return;
    }
    if ( vcs.merge() ) {
      fireSourceChanged();
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
          vcs.addRemote( value );
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
  void setVCS( IVCS vcs ) {
    this.vcs = vcs;
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
    } )
    .filter( meta -> meta != null )
    .filter( meta -> meta.hasChanged() )
    .findAny().isPresent();
  }
}
