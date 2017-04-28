package org.pentaho.di.spoon.git;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.jgit.api.Git;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.swt.SwtBindingFactory;

public class GitController extends AbstractXulEventHandler {

  protected Git git;
  BindingFactory bf;
  String path;

  public GitController() {
    setName( "gitController" );
  }

  public void init() throws IOException, IllegalArgumentException, InvocationTargetException, XulException {
    path = "/Users/hiromu/workspace/pdi-git-plugin/testrepo";
    git = Git.open( new File( path ) );
    bf = new SwtBindingFactory();
    bf.setDocument( this.getXulDomContainer().getDocumentRoot() );
    XulTextbox pathText = (XulTextbox) document.getElementById( "path-text" );
    bf.setBindingType( Binding.Type.BI_DIRECTIONAL );
    Binding binding = bf.createBinding( this, "path", pathText, "value" );
    binding.fireSourceChanged();
  }

  public void commit() {
  }

  public void setPath( String path ) {
    this.path = path;
  }

  public String getPath() {
    return this.path;
  }
}
