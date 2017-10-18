# Overview

This plugin allows you to manage versions of local Kettle files without leaving Spoon.
In addition to Git, Subversion is also supported.

# How to install

## PDI Plugin

```
$ cd data-integration/plugins
$ unzip pdi-git-plugin-X.X.X-jar-with-dependencies.zip
```

To uninstall, just remove the `pdi-git-plugin` folder.

## Native SVN libraries (skip if Subversion is not used)

### Windows

1. Download SlikSVN from [here](https://sliksvn.com/download/) and install it
2. Copy `C:\Program Files\SlikSvn\bin\libsvnjavahl-1.dll` to `libswt\win64` (64-bit) or to `libswt\win32` (32-bit)

### Mac OS X

```
brew install subversion --with-java
sudo ln -s /usr/local/lib/libsvnjavahl-1.dylib $JAVA_HOME/jre/lib/
```

or follow instructions in [here](http://subclipse.stage.tigris.org/wiki/JavaHL).

### Linux (Debian/Ubuntu)

```
$ sudo apt-get install libsvn-java
```

Append `/usr/lib/x86_64-linux-gnu/jni/` to `LIBPATH` in `spoon.sh` as follows:

```
LIBPATH=$LIBPATH:/usr/lib/x86_64-linux-gnu/jni/
export LIBPATH
```

Important: For example in Ubuntu 14.04, the version of libsvn-java will be 1.8.8 by default, but it should be 1.9.X.
Ubuntu 16.04 (Xenial) gives you 1.9.3 by default.

# How to use

The Git plugin is provided as a <i>perspective</i> (See [Using Perspectives](https://help.pentaho.com/Documentation/7.1/0L0/0Y0/020)).
**View > Perspectives > Git** takes you to the Git perspective.

## Git Repository

When you switch to the Git perspective, you will be asked to choose a Git repository out of a list to open.
To add a Git repository to the list, go to
<img src="src/main/resources/org/pentaho/di/git/spoon/images/repository.png" width="16">
**Git Repository > Add**.

The information of repositories will be stored in `$HOME/.pentaho/metastore/pentaho/Git Repository/`.

### Config

<img src="https://github.com/pentaho/pentaho-kettle/raw/7.1.0.0-R/ui/package-res/ui/images/context_menu.png" width="16"> **Config** allows you to configure the opened Git repository.
Currently, a remote repository named "origin" can be set.

## Commit history

The commit history is listed in reverse chronological order.
Selecting one of the commits shows a list of changed files in that particular commit.
If there are any changes in the working tree, WORKINGTREE is added to the top of the list, where you can see those changes.
Right-click menu on a commit differs between Git and Subversion.

### Git

- **Checkout**: checkout a previous commit (`git checkout <commit>`). <img src="src/main/resources/org/pentaho/di/git/spoon/images/branch.png" width="16"> **Branch > Checkout** to undo this operation.
- **Rollback**: rollback to a previous commit (`git revert --no-commit HEAD..<commit>`). **Discard changes** to undo this operation.
- **Reset**: reset HEAD to a previous commit (`git reset --mixed <commit>`).

### Subversion

- **Update**: update to a previous revision (`svn update -r <revision>)`. <img src="src/main/resources/org/pentaho/di/git/spoon/images/pull.png" width="16"> **Update** to undo this operation.
- **Rollback**: rollback to a previous commit (`svn merge -r BASE:<revision>`). **Discard changes** to undo this operation.

Use **Checkout/Update** to see how the contents looked like at that commit with the commit history intact.
Use **Rollback** to rollback all the changes. A new commit can be made to persist the rollback.

## Working with files

If you have changed files but WORKINGTREE is not listed, push **Refresh** to reflect the changes in the Git perspective.
Make sure no commit other than WORKINGTREE is selected in the commit history.
Stage changed files by checking the checkbox on the left side of each file, write a good commit message, change the author name if necessary, and finally <b>Commit</b>.

### Diff

Diff information can be obtained texually and visually.
In order to get the right diff you want to see, it is important to understand the followings:

- When only one commit is selected, the diff will be between the selected commit and its first parent commit.
- When multiple commits are selected, the diff will be between the newest commit and the oldest commit (out of the selected commits).
- When no commit is selected, it is assumed that WORKINGTREE is selected.

Examples:

- Only WORKINGTREE is selected: (1)
- Only 456def is selected: (2)
- WORKINGTREE and 456def are selected: (3)
- 123abc and 789ghi are selected: (4)

You can also see the diff of a specific changed file by selecting one of them, but special rules applie when WORKINGTREE is selected.

- If only WORKINGTREE is selected AND the selected file is not staged: (5)
- If only WORKINGTREE is selected AND the selected file is staged: (6)
- If another commit is also selected: the diff will be between WORKINGTREE and that another commit

![diff](images/diff.png)

A texual diff will be displayed in the bottom left corner.
Visual diff can be displayed by right-clicking on a changed file, then choose **Visual diff**.
This opens up two tabs in the Data Integration perspective:
one tab shows the difference you see when looking from one commit to another commit, and the other tab shows the other way around.

The difference is represented by the small icon superimposed on the top-right corner on the steps/job entries.
Each icon means as follows:

- <img src="src/main/resources/org/pentaho/di/git/spoon/images/added.png" width="16">: Added
- <img src="src/main/resources/org/pentaho/di/git/spoon/images/changed.png" width="16">: Changed
- <img src="src/main/resources/org/pentaho/di/git/spoon/images/removed.png" width="16">: Removed

Note that even just a x-y location change of step/job entry is recognized as a changed one.

### Resolve conflicts

Conflicts happen when merging on a Git repository or when updating on a Subversion repository, but it is difficult to resolve.
Even a very simple conflict like below could be a problem because Spoon won't open such an ill-formed file and editing a Kettle file in a text editor might fail to conform with the Kettle file format.
Instead of resolving conflicts line-by-line, this plugin allows you to resolve them by accepting one out of conflicted versions.

```
      <GUI>
++<<<<<<< HEAD
 +      <xloc>320</xloc>
 +      <yloc>32</yloc>
++=======
+       <xloc>416</xloc>
+       <yloc>80</yloc>
++>>>>>>> d003036e19537739415b7a7c0e6ded6238050189
        <draw>Y</draw>
      </GUI>
```

When a Kettle file, say `hoge.ktr`, has conflicts, this plugin creates a file for each version.
For a Git repository, `hoge.ktr.ours` and `hoge.ktr.theirs` are created.
For a Subversion repository, `hoge.ktr.mine`, `hoge.ktr.rXX`, and `hoge.ktr.rYY` are created.
To accept your desired version, **Stage** the corresponding file (e.g., `hoge.ktr.ours`), then make a commit.
To abort, **Discard changes** of the conflicted file (e.g., `hoge.ktr`) and (Git-only) **Reset** to the latest commit.

## Remote

<img src="src/main/resources/org/pentaho/di/git/spoon/images/pull.png" width="16"> **Pull** and <img src="src/main/resources/org/pentaho/di/git/spoon/images/push.png" width="16"> **Push** allows you to sync between the opened, local repository and the remote one.
**Pull** and **Push** are equivalent of `git pull` and `git push`, respectively.
Thus, the remote `origin` is the source of <b>Pull</b> and the target of <b>Push</b> unless configured otherwise.
If an error (e.g., merge conflict) happens, the operation will be just cancelled.

These commands, however, behave differently depending on how `origin` and branches are configured.
Here is an example `.git/config` (see [here](https://git-scm.com/docs/git-config) for more details):

```
[branch "master"]
  mergeoptions = --no-ff
[remote "origin"]
  url = git@example.com:hiromu/testrepo.git
  fetch = +refs/heads/*:refs/remotes/origin/*
  pushurl = git@example.com:hiromu/testrepo.git
  pushurl = git@example.com:hiromu/testrepo2.git
```

With this example config, **Pull** uses the non fast-forward mode instead of the default fast-forward mode when merging into `master`, **Push** pushes the current branch to two remotes.

### Subversion

**Push** is disabled because making a commit always pushes changes to the remote repository.
Instead of **Pull**, **Update** is used because of the Subversion terminology.

## Branches

<img src="src/main/resources/org/pentaho/di/git/spoon/images/branch.png" width="16"> **Branch** has branch operations: **Checkout** switches between branches, **Create / Delete** can create / delete a branch, **Merge** can merge a branch into the current one, **Push** can push a specific branch.

Switching to a remote branch, say `origin/feature`, gets you in a detached HEAD state.
Use **Branch > Create** to create a local branch, say `feature`, then you will get out of the state.
Collectively, they are equivalent of `git checkout origin/feature` then `git checkout -b feature`.

### Subversion

The typical repository layout is trunk/branches/tags, but currently this plugin has no assumption on the layout.
Thus, the list of branches includes any directories in the repository.
For example, the list may include `/trunk` and `/tags/tag1`.

## Tags

<img src="src/main/resources/org/pentaho/di/git/spoon/images/tag.png" width="16"> **Tag** has tagging operations similar to
<img src="src/main/resources/org/pentaho/di/git/spoon/images/branch.png" width="16"> **Branch** except **Merge** is not available.

## FAQ

### When pushing, I get an "UnknownHostKey" error

This happens when connecting to the remote repository via SSH.
Please add the host to `~/.ssh/known_hosts` in "ssh-rsa" format instead of "ecdsa-sha2-nistp256" format.

### The remote host has been added, but I still get an "UnknownHostKey" error

This could happen for example when the remote host is Gerrit.
The remote host will be added by executing `$ ssh -p 29418 hiromu@localhost`

```
[localhost]:29418 ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBAFgEc3BqPijHvVs5KoXLLoBaYtBlW8c8v+wpHEPpKObAF0lSG2qt764zFUE1eRlb/thq8RdNxHQ8l+i4VLTlR8=
```

However, JSch (Java library for SSH and used in the Git plugin) prefers **ssh-rsa** over **ecdsa-sha2-nistp256**. Hence, add the remote host like below.

```
[localhost]:29418 ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDVoiADWyjer2MRMZYAl1Ws/0zj9VyqMgYQxgNL+xcFGz4cO4AZIaL5L6TlNaU5bOF3WeCFgDLMrMioUoWS/0yLE5Q9mXwE2/5V3fEKDgMfuO+xvEGoh/xZb0GqhCeioG63+clqrXM8DvYfqzMmUg8ksPejEYeQpSrTkg0S5RE9AEB/+qvNnipye7M+9Nutr2lSE+GRhRfFNITCXLIAN6ukoKis+xVZgCMXFSnS41PlhQ/mLNJdA1bMxjm1/58iJsdF44iD+cuM/mFvLoAnXeAbOkkj8jyM136vAvO45M5c+a6Z8k4X7Q/CxsZ2IowWfUshg0jsjerzANUPCaoP9VJX
```

### When behind proxy

Define *System Properties* (not environment variables) like below in spoon.sh

```
OPT="$OPT -Dhttp.proxyHost=localhost -Dhttp.proxyPort=3128 -Dhttps.proxyHost=localhost -Dhttps.proxyPort=3128"
```

or in Spoon.bat

```
set OPT=%OPT% "-Dhttp.proxyHost=10.0.2.2" "-Dhttp.proxyPort=3128" "-Dhttps.proxyHost=10.0.2.2" "-Dhttps.proxyPort=3128"
```

The host and port should be replaced according to your proxy server.

### I got "407 Proxy Authentication Required" error

Proxy Authentication is currently not supported.

### When I checkedout a remote branch, say *origin/develop*, I ended up being in a HEAD detached state. How can I create a local branch?

Please create a branch called *develop*, then you will be out of the HEAD detached state.

# How to compile

```
mvn clean package
```
