package org.pentaho.di.git.spoon.model;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.pentaho.ui.xul.XulEventSourceAdapter;

public class UIFile extends XulEventSourceAdapter {

  private String name;
  private ChangeType changeType;
  private Boolean isStaged = false;

  @Deprecated
  public UIFile( String name, ChangeType changeType ) {
    this.name = name;
    this.changeType = changeType;
  }

  public UIFile( String name, ChangeType changeType, Boolean isStaged ) {
    this.name = name;
    this.changeType = changeType;
    this.isStaged = isStaged;
  }

  public String getName() {
    return name;
  }

  public void setName( String name ) {
    this.name = name;
  }

  public ChangeType getChangeType() {
    return changeType;
  }

  public void setChangeType( ChangeType changeType ) {
    this.changeType = changeType;
  }

  public String getImage() {
    final String location = "org/pentaho/di/git/spoon/images/";
    switch ( changeType ) {
      case ADD:
      case COPY:
        return location + "added.svg";
      case MODIFY:
      case RENAME:
        return location + "changed.svg";
      case DELETE:
        return location + "removed.svg";
      default:
        return "";
    }
  }

  public Boolean getIsStaged() {
    return isStaged;
  }

  public void setIsStaged( Boolean isStaged ) {
    this.isStaged = isStaged;
  }
}
