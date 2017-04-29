package org.pentaho.di.spoon.git;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.ui.spoon.SpoonPerspective;
import org.pentaho.di.ui.spoon.SpoonPerspectiveManager;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.swt.SwtBindingFactory;

public class GitController extends AbstractXulEventHandler {

  protected Git git;
  BindingFactory bf;
  Binding binding;
  String path;

  public GitController() {
    setName( "gitController" );
  }

  public void init() throws IllegalArgumentException, InvocationTargetException, XulException {
    XulTextbox pathText = (XulTextbox) document.getElementById( "path-text" );
    bf = new SwtBindingFactory();
    bf.setDocument( this.getXulDomContainer().getDocumentRoot() );
    bf.setBindingType( Binding.Type.BI_DIRECTIONAL );
    binding = bf.createBinding( this, "path", pathText, "value" );
  }

  public void commit() {
  }

  public void setPath( String path ) {
    this.path = path;
  }

  public String getPath() {
    return this.path;
  }

  public void setActive( boolean active ) {
    List<SpoonPerspective> perspectives = SpoonPerspectiveManager.getInstance().getPerspectives();
    SpoonPerspective mainSpoonPerspective = perspectives.stream()
      .filter( perspective -> perspective.getId().equals( "001-spoon-jobs" ) )
      .collect( Collectors.toList() )
      .get( 0 );
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
      binding.fireSourceChanged();
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
  }
}
