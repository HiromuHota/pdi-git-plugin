package org.pentaho.di.ui.spoon.git.model;

import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryContent;

public class UITransformation extends UIRepositoryContent {

  private static final long serialVersionUID = -3674981946848733377L;

  private String name;

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName( String name ) {
    this.name = name;
  }

  @Override
  public String getImage() {
    return "ui/images/transrepo.svg";
  }
}
