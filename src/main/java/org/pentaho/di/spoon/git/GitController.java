package org.pentaho.di.spoon.git;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
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
import org.pentaho.ui.xul.XulComponent;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.containers.XulTree;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.swt.SwtBindingFactory;
import org.pentaho.ui.xul.util.XulDialogCallback;

public class GitController extends AbstractXulEventHandler {

  private static final Class<?> PKG = RepositoryExplorer.class;

  protected Git git;
  protected String path;

  protected XulTextbox pathText;
  protected XulTree revisionTable;
  protected XulTree unstagedTable;
  protected XulTree stagedTable;

  protected BindingFactory bf = new SwtBindingFactory();
  protected Binding pathBinding;
  protected Binding revisionBinding;
  protected Binding unstagedBinding;
  protected Binding stagedBinding;

  protected XulMessageBox messageBox;
  protected XulConfirmBox confirmBox;

  public GitController() {
    setName( "gitController" );
  }

  public void init() throws IllegalArgumentException, InvocationTargetException, XulException {
    messageBox = (XulMessageBox) document.createElement( "messagebox" );
    confirmBox = (XulConfirmBox) document.createElement( "confirmbox" );
    pathText = (XulTextbox) document.getElementById( "path-text" );
    revisionTable = (XulTree) document.getElementById( "revision-table" );
    bf.setDocument( this.getXulDomContainer().getDocumentRoot() );
  }

  public void setPath( String path ) {
    this.path = path;
  }

  public String getPath() {
    return this.path;
  }

  public UIRepositoryObjectRevisions getRevisionObjects() {
    UIRepositoryObjectRevisions revisions = new UIRepositoryObjectRevisions();
    if ( git == null ) {
      return revisions;
    }
    try {
      Iterable<RevCommit> iterable = git.log().call();
      for( RevCommit commit : iterable ) {
        PurObjectRevision rev = new PurObjectRevision(
          commit.getName().substring(0, 7),
          commit.getAuthorIdent().getName(),
          commit.getAuthorIdent().getWhen(),
          commit.getShortMessage());
        revisions.add( new UIRepositoryObjectRevision( (ObjectRevision)rev ) );
      }
    } catch (NoHeadException e) {
      // Do nothing
    } catch (GitAPIException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return revisions;
  }

  public UIRepositoryObjects getUnstagedObjects() throws Exception {
    Set<String> files = new HashSet<String>();
    if ( git == null ) {
      return getObjects( files );
    }
    Status status = git.status().call();
    files.addAll( status.getModified() );
    files.addAll( status.getUntracked() );
    return getObjects( files );
  }

  public UIRepositoryObjects getStagedObjects() throws Exception {
    Set<String> files = new HashSet<String>();
    if ( git == null ) {
      return getObjects( files );
    }
    Status status = git.status().call();
    files.addAll( status.getAdded() );
    files.addAll( status.getChanged() );
    return getObjects( files );
  }

  private UIRepositoryObjects getObjects( Set<String> files ) throws Exception {
    UIRepositoryObjects objs = new UIRepositoryObjects();
    for ( String file : files ) {
      UIRepositoryObject obj;
      Date date = new Date();
      if ( file.endsWith( ".ktr" ) ) {
        ObjectId id = new StringObjectId( file );
        RepositoryElementMetaInterface rc =  new RepositoryObject(
            id, file, null, "-", date, RepositoryObjectType.TRANSFORMATION, "", false );
        obj = new UITransformation( rc, null, null );
      } else {
        ObjectId id = new StringObjectId( file );
        RepositoryElementMetaInterface rc =  new RepositoryObject(
            id, file, null, "-", date, RepositoryObjectType.JOB, "", false );
        obj = new UIJob( rc, null, null );
      }
      objs.add( obj );
    }
    return objs;
  }

  public void setActive() {
    bf.setBindingType( Binding.Type.BI_DIRECTIONAL );
    pathBinding = bf.createBinding( this, "path", pathText, "value" );

    bf.setBindingType( Binding.Type.ONE_WAY );
    revisionBinding = bf.createBinding( this, "revisionObjects", revisionTable, "elements" );

    unstagedTable = (XulTree) document.getElementById( "unstaged-table" );
    stagedTable = (XulTree) document.getElementById( "staged-table" );
    unstagedBinding = bf.createBinding( this, "unstagedObjects", unstagedTable, "elements" );
    stagedBinding = bf.createBinding( this, "stagedObjects", stagedTable, "elements" );

    List<SpoonPerspective> perspectives = SpoonPerspectiveManager.getInstance().getPerspectives();
    SpoonPerspective mainSpoonPerspective = null;
    for (SpoonPerspective perspective : perspectives ) {
      if ( perspective.getId().equals( MainSpoonPerspective.ID ) ) {
        mainSpoonPerspective = perspective;
        break;
      }
    }
    if ( Spoon.getInstance().rep != null ) { // when connected to a repository
      if ( Spoon.getInstance().rep.getClass() == KettleFileRepository.class ) {
        final String baseDirectory = ( (KettleFileRepository) Spoon.getInstance().rep ).getRepositoryMeta().getBaseDirectory();
        path = baseDirectory;
        try {
          git = Git.open( new File( baseDirectory ) );
        } catch ( RepositoryNotFoundException e ) {
          confirmBox.setTitle( "Repository not found" );
          confirmBox.setMessage( "Wanna create a new repository?" );
          confirmBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
          confirmBox.setCancelLabel( BaseMessages.getString( PKG, "Dialog.Cancel" ) );
          confirmBox.addDialogCallback( new XulDialogCallback<Object>() {

            public void onClose( XulComponent sender, Status returnCode, Object retVal ) {
              if ( returnCode == Status.ACCEPT ) {
                try {
                  Git.init().setDirectory( new File( baseDirectory ) ).call();
                  git = Git.open( new File( baseDirectory ) );
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
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      } else { // PentahoEnterpriseRepository and KettleDatabaseRepository are not supported.
        return;
      }
    } else {
      EngineMetaInterface meta = mainSpoonPerspective.getActiveMeta();
      if ( meta == null ) { // no file is opened.
        return;
      }
      String fileName = meta.getFilename();
      Repository repository;
      try {
        repository = ( new FileRepositoryBuilder() ).readEnvironment() // scan environment GIT_* variables
          .findGitDir( new File( fileName ).getParentFile() ) // scan up the file system tree
          .build();
        git = new Git( repository );
        path = repository.getDirectory().getParent();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    try {
      XulTextbox authorName = (XulTextbox) document.getElementById( "author-name" );
      authorName.setValue( git.getRepository().getConfig().getString("user", null, "name")
          + " <" + git.getRepository().getConfig().getString("user", null, "email") + ">" );
      pathBinding.fireSourceChanged();
      revisionBinding.fireSourceChanged();
      unstagedBinding.fireSourceChanged();
      stagedBinding.fireSourceChanged();
    } catch (NoWorkTreeException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IllegalArgumentException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (XulException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void setInactive() {
    pathBinding.destroyBindings();
    revisionBinding.destroyBindings();
  }

  public void addToIndex() throws Exception {
    Collection<UIRepositoryContent> contents = unstagedTable.getSelectedItems();
    for ( UIRepositoryContent content : contents ) {
      git.add().addFilepattern( content.getName() ).call();
    }
    unstagedBinding.fireSourceChanged();
    stagedBinding.fireSourceChanged();
  }

  public void removeFromIndex() throws Exception {
    Collection<UIRepositoryContent> contents = stagedTable.getSelectedItems();
    for ( UIRepositoryContent content : contents ) {
      git.reset().addPath( content.getName() ).call();
    }
    unstagedBinding.fireSourceChanged();
    stagedBinding.fireSourceChanged();
  }

  public void commit() throws Exception {
    if ( getStagedObjects().size() == 0 ) {
      messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
      messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
      messageBox.setMessage( "There are no staged files" );
      messageBox.open();
      return;
    }

    XulTextbox authorName = (XulTextbox) document.getElementById( "author-name" );
    Matcher m = Pattern.compile( "(.*) <(.*@.*)>" ).matcher( authorName.getValue() );
    if ( !m.matches() ) {
      messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
      messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
      messageBox.setMessage( "Malformed author name" );
      messageBox.open();
      return;
    }
    XulTextbox commitMessage = (XulTextbox) document.getElementById( "commit-message" );
    git.commit().setAuthor( m.group(1), m.group(2) ).setMessage( commitMessage.getValue() ).call();
    commitMessage.setValue( "" );
    stagedBinding.fireSourceChanged();
    revisionBinding.fireSourceChanged();
  }
}
