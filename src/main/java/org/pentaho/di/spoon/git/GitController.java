package org.pentaho.di.spoon.git;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.ObjectRevision;
import org.pentaho.di.repository.RepositoryElementMetaInterface;
import org.pentaho.di.repository.RepositoryObject;
import org.pentaho.di.repository.RepositoryObjectType;
import org.pentaho.di.repository.StringObjectId;
import org.pentaho.di.repository.pur.PurObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIJob;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryContent;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryObject;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryObjects;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UITransformation;
import org.pentaho.di.ui.spoon.MainSpoonPerspective;
import org.pentaho.di.ui.spoon.SpoonPerspective;
import org.pentaho.di.ui.spoon.SpoonPerspectiveManager;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.containers.XulTree;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.swt.SwtBindingFactory;

public class GitController extends AbstractXulEventHandler {

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

  public GitController() {
    setName( "gitController" );
  }

  public void init() throws IllegalArgumentException, InvocationTargetException, XulException {
    pathText = (XulTextbox) document.getElementById( "path-text" );
    revisionTable = (XulTree) document.getElementById( "revision-table" );
    bf.setDocument( this.getXulDomContainer().getDocumentRoot() );
  }

  public void commit() {
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

  public void setActive( boolean active ) {
    if ( active ) {
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
      EngineMetaInterface meta = mainSpoonPerspective.getActiveMeta();
      if ( meta == null ) {
        return;
      }
      String fileName = meta.getFilename();
      try {
        Repository repository = ( new FileRepositoryBuilder() ).readEnvironment() // scan environment GIT_* variables
          .findGitDir( new File( fileName ).getParentFile() ) // scan up the file system tree
          .build();
        git = new Git( repository );
        path = repository.getDirectory().getParent();
        pathBinding.fireSourceChanged();
        revisionBinding.fireSourceChanged();
        unstagedBinding.fireSourceChanged();
        stagedBinding.fireSourceChanged();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
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
    } else {
      pathBinding.destroyBindings();
      revisionBinding.destroyBindings();
    }
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
}
