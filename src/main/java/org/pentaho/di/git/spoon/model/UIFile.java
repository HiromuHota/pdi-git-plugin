package org.pentaho.di.git.spoon.model;

import org.apache.commons.io.FilenameUtils;
import org.pentaho.ui.xul.XulEventSourceAdapter;

public class UIFile extends XulEventSourceAdapter {

  private String name;

  public UIFile( String name ) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName( String name ) {
    this.name = name;
  }

  public String getImage() {
    String ext = FilenameUtils.getExtension( name );
    if ( "ktr".equalsIgnoreCase( ext ) ) {
      return "ui/images/transrepo.svg";
    } else if ( "kjb".equalsIgnoreCase( ext ) ) {
      return "ui/images/jobrepo.svg";
    } else {
      return "";
    }
  }
}
