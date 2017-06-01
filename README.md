# Overview

The Git plugin allows you to manage versions of Kettle files without leaving Spoon.
Local filesystem (whether or not connected as a File Repository) is supported, but neither Pentaho Repository nor Database Repository is.

# System requirements

It's been tested with PDI (CE) of 6.1, 7.0, and 7.1; but it should run on the corresponding version of EE.

# How to install

```
$ cd data-integration/plugins
$ unzip pdi-git-plugin-X.X.X-jar-with-dependencies.zip
```

To uninstall, just remove the `pdi-git-plugin` folder.

# How to use

The Git plugin is provided as a <i>perspective</i> (See [Using Perspectives](https://help.pentaho.com/Documentation/7.1/0L0/0Y0/020)).
Switch to the Git perspective

- Whenever you want when connected to a File Repository
- Only when a Transformation/Job is opened, saved at least once before, and focused when not connected to a File Repository

## Working with files

Stage changed files by drag & drop, write a good commit message, change the author name if necessary, and finally <b>Commit</b>.

## Remote

Currently only `origin` can be set and it is always the source of <b>Pull</b> and the target of <b>Push</b>.
<b>Pull</b> is equivalent of `git fetch; git merge --ff`.
If an error (e.g., merge conflict) happens, the operation will be just cancelled.

## Branches

Currently, changing branches is not implemented, but you can do so using other Git clients.

# How to compile

```
mvn clean package
```
