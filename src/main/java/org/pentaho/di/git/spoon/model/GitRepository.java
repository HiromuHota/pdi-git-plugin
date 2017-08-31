package org.pentaho.di.git.spoon.model;

import org.pentaho.metastore.persist.MetaStoreAttribute;
import org.pentaho.metastore.persist.MetaStoreElementType;

@MetaStoreElementType(
  name = "Git Repository",
  description = "This defines a Git repository" )
public class GitRepository {

  @MetaStoreAttribute( key = "name" )
  private String name;

  public String getName() {
    return name;
  }

  public void setName( String name ) {
    this.name = name;
  }

  @MetaStoreAttribute( key = "description" )
  private String description;

  public String getDescription() {
    return description;
  }

  public void setDescription( String description ) {
    this.description = description;
  }

  @MetaStoreAttribute( key = "directory" )
  private String directory;

  public String getDirectory() {
    return directory;
  }

  public void setDirectory( String directory ) {
    this.directory = directory;
  }

  @MetaStoreAttribute( key = "type" )
  private String type;

  public String getType() {
    return type;
  }

  public void setType( String type ) {
    this.type = type;
  }
}
