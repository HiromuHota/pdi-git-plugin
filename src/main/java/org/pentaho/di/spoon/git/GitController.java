package org.pentaho.di.spoon.git;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.repository.ObjectRevision;
import org.pentaho.di.repository.pur.PurObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
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
  XulTextbox pathText;
  XulTree revisionTable;
  BindingFactory bf = new SwtBindingFactory();
  Binding pathBinding;
  Binding revisionBinding;
  String path;
  protected UIRepositoryObjectRevisions revisions;

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
    revisions = new UIRepositoryObjectRevisions();
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

  public void setActive( boolean active ) {
    if ( active ) {
      bf.setBindingType( Binding.Type.BI_DIRECTIONAL );
      pathBinding = bf.createBinding( this, "path", pathText, "value" );

      bf.setBindingType( Binding.Type.ONE_WAY );
      revisionBinding = bf.createBinding( this, "revisionObjects", revisionTable, "elements" );

      List<SpoonPerspective> perspectives = SpoonPerspectiveManager.getInstance().getPerspectives();
      SpoonPerspective mainSpoonPerspective = null;
      for (SpoonPerspective perspective : perspectives ) {
        if ( perspective.getId().equals( "001-spoon-jobs" ) ) {
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
}
