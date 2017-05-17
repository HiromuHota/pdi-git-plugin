package org.pentaho.di.ui.spoon.git.model;

import org.pentaho.ui.xul.XulEventSourceAdapter;

public class UIFile extends XulEventSourceAdapter {

  private String name;

  public String getName() {
    return name;
  }

  public void setName( String name ) {
    this.name = name;
  }

  public String getImage() {
    return "";
  }
}
