# Overview

The Git plugin allows you to manage versions of local Kettle files without leaving Spoon.

# How to install

```
$ cd data-integration/plugins
$ unzip pdi-git-plugin-X.X.X-jar-with-dependencies.zip
```

To uninstall, just remove the `pdi-git-plugin` folder.

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

<img src="src/main/resources/org/pentaho/di/git/spoon/images/context_menu.png" width="16">
**Config** allows you to configure the opened Git repository.
Currently, a remote repository named "origin" can be set.

## Working with files

Stage changed files by drag & drop, write a good commit message, change the author name if necessary, and finally <b>Commit</b>.
Right-click on a commit pops up a context menu, where you can choose **checkout** to checkout that particular commit.

### Diff

Texual diff will be displayed in the bottom left corner:

- When a commit is selected in the commit history, or
- When <i>WIP</i> (work-in-progress) is selected in the commit history and an unstaged/untracked files is selected

Visual diff can be displayed by right-clicking on a unstaged/untracked file, then choose **Visual diff**.
This opens up two tabs: **HEAD -> Working tree** and **Working tree -> HEAD** in the Data Integration perspective.

- HEAD -> Working tree: the difference you see when looking from HEAD (the last committed version) to the working tree (the current, uncommitted version)
- Working tree -> HEAD:	 vice versa

The difference is represented by the small icon superimposed on the top-right corner on the steps/job entries.
Each icon means as follows:

- <img src="src/main/resources/org/pentaho/di/git/spoon/images/added.png" width="16">: Added
- <img src="src/main/resources/org/pentaho/di/git/spoon/images/changed.png" width="16">: Changed
- <img src="src/main/resources/org/pentaho/di/git/spoon/images/removed.png" width="16">: Removed

Note that even just a x-y location change of step/job entry is recognized as a changed one.

## Remote

<img src="src/main/resources/org/pentaho/di/git/spoon/images/pull.png" width="16">
**Pull** and
<img src="src/main/resources/org/pentaho/di/git/spoon/images/push.png" width="16">
**Push**
allows you to sync between the opened, local repository and the remote one.
<b>Pull</b> is equivalent of `git fetch; git merge --ff`.
If an error (e.g., merge conflict) happens, the operation will be just cancelled.
Note that "origin" is always the source of <b>Pull</b> and the target of <b>Push</b>.

## Branches

<img src="src/main/resources/org/pentaho/di/git/spoon/images/branch.png" width="16">
**Branch** has branch operations: **Checkout** switches between branches, **Create / Delete** can create / delete a branch, **Merge** can merge a branch into the current one.

Switching to a remote branch, say `origin/feature`, gets you in a detached HEAD state.
Use **Branch > Create** to create a local branch, say `feature`, then you will get out of the state.
Collectively, they are equivalent of `git checkout origin/feature` then `git checkout -b feature`.

## FAQ

### When pushing, I get an "UnknownHostKey" error

This happens when connecting to the remote repository via SSH.
Please add the host to `~/.ssh/known_hosts`

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
