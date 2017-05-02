package org.pentaho.di.spoon.git.model;

import org.pentaho.ui.xul.XulEventSourceAdapter;

public class UIGit extends XulEventSourceAdapter {
  private String path;

  public String getPath() {
    return path;
  }

  public void setPath( String path ) {
    this.path = path;
    firePropertyChange( "path", null, path );
  }
}
